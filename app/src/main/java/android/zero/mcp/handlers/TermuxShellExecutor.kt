// File: android/zero/mcp/handlers/TermuxShellExecutor.kt
package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.LogManager
import com.termux.am.Am // From termux-am-library
import com.termux.shared.R
import com.termux.shared.android.PackageUtils
import com.termux.shared.android.PermissionUtils
import com.termux.shared.errors.Error
import com.termux.shared.shell.ArgumentTokenizer
import com.termux.shared.shell.command.ExecutionCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * [TermuxShellExecutor] handles the execution of shell commands using the Termux:API.
 * It leverages `com.termux.am.Am` for robust command execution and captures stdout/stderr.
 *
 * It provides a callback mechanism to stream results back to the caller.
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @author Android Zero
 */
class TermuxShellExecutor(
    private val scope: CoroutineScope,
    private val context: Context
) {

    private val TAG = "TermuxShellExecutor"

    // Maps a unique command ID to a Triple of (stdout, stderr, callback) for async tracking
    private val activeCommands = ConcurrentHashMap<String, Triple<ByteArrayOutputStream, ByteArrayOutputStream, (String, String, Boolean?) -> Unit>>()

    /**
     * Executes a given shell command in the Termux environment.
     * Output (stdout, stderr) and completion status are streamed back via the callback.
     *
     * @param command The shell command string to execute.
     * @param workingDirectory Optional working directory for the command.
     * @param callback A lambda to send back events: (eventType: String, data: String, success: Boolean?).
     * eventType: "shell.exec.stdout", "shell.exec.stderr", "shell.exec.complete", "shell.exec.error".
     * success: `null` for stdout/stderr, `true`/`false` for complete/error.
     */
    fun executeCommand(
        command: String,
        workingDirectory: String?,
        callback: (eventType: String, data: String, success: Boolean?) -> Unit
    ) {
        val commandId = UUID.randomUUID().toString()
        LogManager.addLog("TermuxShellExecutor: Preparing to execute command (ID: $commandId): $command", "DEBUG", TAG)

        scope.launch(Dispatchers.IO) {
            val stdoutStream = ByteArrayOutputStream()
            val stderrStream = ByteArrayOutputStream()
            activeCommands[commandId] = Triple(stdoutStream, stderrStream, callback)

            try {
                // Termux's Am.run expects an array of arguments, not a single string.
                // We use ArgumentTokenizer from Termux to parse the command string into arguments.
                val commandArgs = ArgumentTokenizer.tokenize(command).toTypedArray()

                // Check display over apps permission if starting activity/service (Android 10+)
                val checkDisplayOverAppsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    (commandArgs.firstOrNull() == "start" || commandArgs.firstOrNull() == "startservice") &&
                    !PermissionUtils.validateDisplayOverOtherAppsPermissionForPostAndroid10(context, false)

                if (checkDisplayOverAppsPermission) {
                    val errorMsg = context.getString(
                        R.string.error_display_over_other_apps_permission_not_granted,
                        PackageUtils.getAppNameForPackage(context)
                    )
                    LogManager.addLog("TermuxShellExecutor: Permission denied: $errorMsg", "ERROR", TAG)
                    callback("shell.exec.error", errorMsg, false)
                    return@launch
                }

                // Create PrintStreams for stdout and stderr
                PrintStream(stdoutStream, true, StandardCharsets.UTF_8.name()).use { stdoutPrintStream ->
                    PrintStream(stderrStream, true, StandardCharsets.UTF_8.name()).use { stderrPrintStream ->

                        // Instantiate Am and run the command
                        val am = Am(stdoutPrintStream, stderrPrintStream, context.applicationContext as Application)
                        // Am.run returns an Error object if an exception occurred during its internal execution.
                        // It does NOT reflect the shell command's exit code directly.
                        val amError: Error? = try {
                            am.run(commandArgs)
                            null // No error from Am itself
                        } catch (e: Exception) {
                            Error(e.message, e) // Capture any direct exceptions from Am.run
                        }

                        // Flush streams to ensure all content is written
                        stdoutPrintStream.flush()
                        stderrPrintStream.flush()

                        val stdoutContent = stdoutStream.toString(StandardCharsets.UTF_8.name())
                        val stderrContent = stderrStream.toString(StandardCharsets.UTF_8.name())

                        // Send stdout/stderr content if available
                        if (stdoutContent.isNotEmpty()) {
                            callback("shell.exec.stdout", stdoutContent, null)
                        }
                        if (stderrContent.isNotEmpty()) {
                            callback("shell.exec.stderr", stderrContent, null)
                        }

                        if (amError != null) {
                            LogManager.addLog("TermuxShellExecutor: Am.run internal error: ${amError.message}", "ERROR", TAG)
                            callback("shell.exec.error", amError.message ?: "Unknown internal error", false)
                        } else {
                            // Am.run doesn't return the *shell command's* exit code directly.
                            // To get the exit code, one would typically need to execute 'echo $?'
                            // in a new session after the command, or use a more sophisticated process execution API.
                            // For simplicity, we assume success if Am.run itself didn't throw an exception.
                            // In a full Termux integration, you would use Termux's AppShell or TerminalSession
                            // which provide direct exit codes.
                            LogManager.addLog("TermuxShellExecutor: Command completed.", "INFO", TAG)
                            callback("shell.exec.complete", "Command completed with simulated exit code 0.", true)
                        }
                    }
                }
            } catch (e: Exception) {
                LogManager.addLog("TermuxShellExecutor: Uncaught exception during command execution: ${e.message}", "ERROR", TAG)
                callback("shell.exec.error", "Execution error: ${e.message}", false)
            } finally {
                activeCommands.remove(commandId)
                stdoutStream.close()
                stderrStream.close()
            }
        }
    }
}
