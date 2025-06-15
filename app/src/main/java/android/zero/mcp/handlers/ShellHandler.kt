// File: android/zero/mcp/handlers/ShellHandler.kt
package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.LogManager
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/**
 * [ShellHandler] processes MCP commands for executing shell commands within the Termux environment.
 * It delegates the actual execution to [TermuxShellExecutor].
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @author Android Zero
 */
class ShellHandler(
    private val scope: CoroutineScope,
    private val context: Context
) : CommandHandler {

    private val TAG = "ShellHandler"
    private val termuxShellExecutor = TermuxShellExecutor(scope, context)

    override suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>) {
        scope.launch {
            val id = command.id
            val contextId = command.contextId
            val instructionParts = command.instruction.split(":")
            if (instructionParts.size < 2) {
                sendError(id, contextId, "Invalid shell instruction format.", responseChannel)
                return@launch
            }

            val operation = instructionParts[1].removePrefix("#")

            LogManager.addLog("ShellHandler: Handling operation '$operation'", "DEBUG", TAG)

            when (operation) {
                "exec" -> {
                    val shellCommand = command.args["command"]?.jsonPrimitive?.content
                    val workingDirectory = command.args["path"]?.jsonPrimitive?.content
                    if (shellCommand == null) {
                        sendError(id, contextId, "Missing 'command' argument for shell.exec.", responseChannel)
                        return@launch
                    }
                    handleShellExec(id, contextId, shellCommand, workingDirectory, responseChannel)
                }
                else -> {
                    sendError(id, contextId, "Unknown shell operation: $operation", responseChannel)
                }
            }
        }
    }

    /**
     * Executes a shell command using [TermuxShellExecutor] and streams output via response channel.
     */
    private suspend fun handleShellExec(id: String, contextId: String?, shellCommand: String, workingDirectory: String?, responseChannel: SendChannel<String>) {
        LogManager.addLog("ShellHandler: Executing shell command: '$shellCommand' in '$workingDirectory'", "INFO", TAG)

        // Send initial dispatched response
        sendResponse(id, contextId, "shell.exec.dispatched", JsonPrimitive("Command sent to Termux."), true, responseChannel)

        termuxShellExecutor.executeCommand(shellCommand, workingDirectory) { event, data, success ->
            scope.launch {
                sendResponse(id, contextId, event, JsonPrimitive(data), success, responseChannel)
            }
        }
    }

    /**
     * Helper to send a generic MCP response.
     */
    private suspend fun sendResponse(id: String, contextId: String?, event: String, data: JsonPrimitive, success: Boolean?, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, event, data, success, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("ShellHandler: Sent event '$event' for $id.", if (success == false) "ERROR" else "INFO", TAG)
    }

    /**
     * Helper to send an error MCP response.
     */
    private suspend fun sendError(id: String, contextId: String?, errorMessage: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "shell.error", JsonPrimitive(errorMessage), false, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("ShellHandler: Error for $id: $errorMessage", "ERROR", TAG)
    }
}
