package android.zero.mcp

import android.content.Context
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Manages Termux terminal sessions to execute shell commands.
 * This class acts as a bridge, receiving commands from an external source (like a UI or server),
 * running them in a true Termux environment, and streaming the output back.
 */
class ShellHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val context: Context // Assuming the Android Context is available
) {

    // Corrected the declaration of sessionCache.
    // It should be 'mutableMapOf' directly, without a redundant 'mutable' keyword.
    private val sessionCache = mutableMapOf<String, TerminalSession>()

    /**
     * Handles an incoming command request. It will either create a new Termux session
     * or use an existing one to execute the command.
     *
     * @param args A map containing command details, including "command" and "contextId".
     * @param out A channel to send McpResponse objects (containing output, errors, etc.) back to the caller.
     */
    suspend fun handle(
        args: Map<String, String>,
        out: SendChannel<McpResponse>
    ) {
        val commandToExecute = args["command"]?.trim()
        val contextId = args["contextId"]?.trim()

        // Validate required parameters
        if (commandToExecute.isNullOrEmpty()) {
            out.send(resp(args["id"], contextId, "shell.error", "No command provided"))
            return
        }
        if (contextId.isNullOrEmpty()) {
            out.send(resp(args["id"], contextId, "shell.error", "Missing contextId for session management"))
            return
        }

        // Get or create a terminal session.
        // We use getOrPut to ensure that for a given contextId, only one session is created.
        val session = sessionCache.getOrPut(contextId) {
            createTermuxSession(contextId, out, args["id"])
        }

        // Write the command to the session's input.
        // The TerminalSession.write(String) method is available from TerminalOutput.
        session.write(commandToExecute)
        session.write("\n") // Press Enter to execute the command
    }

    /**
     * Creates a new TerminalSession configured for the Termux environment.
     *
     * @param contextId The unique identifier for this session.
     * @param out The channel for sending responses back.
     * @param requestId The original request ID for correlation.
     * @return A fully configured TerminalSession.
     */
    private fun createTermuxSession(contextId: String, out: SendChannel<McpResponse>, requestId: String?): TerminalSession {
        // Define the shell executable and its arguments.
        // Using `login` as the executable ensures a proper Termux environment.
        val executablePath = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/login"
        val cwd = TermuxConstants.TERMUX_HOME_DIR_PATH
        val shellArgs = arrayOf<String>() // No extra args needed for login shell
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=${TermuxConstants.TERMUX_HOME_DIR_PATH}",
            "PREFIX=${TermuxConstants.TERMUX_PREFIX_DIR_PATH}",
            // Pass system environment variables for a more complete environment
            "BOOTCLASSPATH=${System.getenv("BOOTCLASSPATH")}",
            "ANDROID_ROOT=${System.getenv("ANDROID_ROOT")}",
            "ANDROID_DATA=${System.getenv("ANDROID_DATA")}",
            "EXTERNAL_STORAGE=${System.getenv("EXTERNAL_STORAGE")}"
        )

        // Create a custom session client to handle events from the TerminalSession.
        // This client will receive terminal output, session finish events, and errors.
        val client = object : TermuxTerminalSessionClientBase() {
            /**
             * This callback is triggered whenever the text in the terminal buffer changes.
             * This is our primary way of capturing the command's output as it's generated.
             */
            override fun onTextChanged(changedSession: TerminalSession) {
                // Extract the current visible text (transcript) from the terminal emulator.
                val transcript = changedSession.emulator.screen.transcriptText
                scope.launch {
                    // Send the transcript as a 'shell.log' event.
                    out.send(resp(requestId, contextId, "shell.log", transcript))
                }
            }

            /**
             * This callback is triggered when the terminal session process exits.
             * We use this to signal command completion and clean up the session.
             */
            override fun onSessionFinished(finishedSession: TerminalSession) {
                scope.launch {
                    // Send a 'shell.complete' event with the exit code.
                    out.send(resp(requestId, contextId, "shell.complete", "Session finished with exit code: ${finishedSession.exitStatus}"))
                    // Remove the session from the cache as it's no longer running.
                    sessionCache.remove(contextId)
                }
            }

            /**
             * This callback handles any errors reported by the session.
             * Errors are propagated as 'shell.error' events.
             */
            override fun logError(tag: String?, message: String?) {
                scope.launch {
                    out.send(resp(requestId, contextId, "shell.error", message ?: "Unknown error"))
                }
            }

            // Other TerminalSessionClientBase methods are implicitly overridden or handled by the base class.
        }

        // Define standard terminal dimensions. These will be updated after session creation.
        val initialColumns = 80
        val initialRows = 24

        // Create the TerminalSession.
        // Based on the compiler error, the constructor expected is (executablePath, cwd, shellArgs, env, initialRows, client).
        val session = TerminalSession(executablePath, cwd, shellArgs, env, initialRows, client)

        // After the session is created, update its dimensions.
        // This is crucial for proper terminal emulation and display.
        session.updateSize(initialColumns, initialRows)

        return session
    }

    /**
     * Closes a specific terminal session if it exists in the cache.
     * This will terminate the underlying shell process.
     * @param contextId The unique identifier of the session to close.
     */
    fun closeSession(contextId: String) {
        // The 'finishIfRunning' method is indeed available on TerminalSession.
        // The previous "Unresolved reference" was a side effect of the syntax error above.
        sessionCache.remove(contextId)?.finishIfRunning()
    }

    /**
     * Helper function to create an McpResponse object with a unique ID, event type, and data.
     * @param id The original request ID, or a new UUID if not provided.
     * @param contextId The context ID associated with the response.
     * @param event The type of event (e.g., "shell.log", "shell.error", "shell.complete").
     * @param data The payload of the response.
     * @return An instance of McpResponse.
     */
    private fun resp(id: String?, contextId: String?, event: String, data: String) =
        McpResponse(id ?: UUID.randomUUID().toString(), event, data, null, contextId)
}
