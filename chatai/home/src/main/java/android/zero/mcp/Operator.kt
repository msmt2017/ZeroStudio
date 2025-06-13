// File: Operator.kt
package android.zero.mcp

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull // Import for potential timeout handling

class Operator(
    private val contextManager: ContextManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    suspend fun execute(cmd: ExecuteCommand): String {
        val ctxId = cmd.contextId
        // `context` is retrieved but not directly used in the `when` block for the `shell.execute` case itself.
        // It's mainly used to put the `lastResult`.
        val context = contextManager.getContext(ctxId.orEmpty())

        val result = when (cmd.type) {
            "shell.execute" -> {
                val commandLine = cmd.args["command"] ?: ""
                // Ensure a valid Android Context is passed to ShellHandler
                val androidContext = contextManager.getContext(ctxId.orEmpty())?.get("androidContext") as? Context
                    ?: throw IllegalArgumentException("Android Context missing for ShellHandler in contextId: ${ctxId.orEmpty()}")

                val shellHandler = ShellHandler(scope, androidContext)
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)

                // Use a Deferred to collect the result from the channel asynchronously
                val deferredResult = scope.async {
                    var resultText = ""
                    try {
                        for (response in responseChannel) {
                            when (response.event) {
                                "shell.log" -> {
                                    // Append log output. Adding a newline for readability if the output isn't line-buffered.
                                    resultText += response.result + "\n"
                                }
                                "shell.error" -> {
                                    // If an error occurs, set the result text to the error message.
                                    // This assumes you want to return only the error message in case of an error.
                                    resultText = "Error: ${response.result}"
                                    // Important: Close the channel as soon as a final state (error/complete) is reached.
                                    responseChannel.close()
                                }
                                "shell.complete" -> {
                                    // Session finished. Optionally, you could append the completion message.
                                    // For now, we just ensure the channel is closed.
                                    responseChannel.close()
                                }
                                // Handle other potential events if any, though "shell.log", "shell.error", "shell.complete" are primary.
                                else -> {
                                    // Optionally log unexpected events
                                    resultText += "Unexpected event: ${response.event} - ${response.result}\n"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Catch any exceptions during channel processing
                        resultText = "Internal channel error: ${e.message}"
                        responseChannel.close() // Ensure channel is closed on error
                    }
                    resultText
                }

                // Launch the command handling without awaiting it immediately in this coroutine.
                // The deferredResult.await() will wait for the channel to close.
                shellHandler.handle(cmd.args, responseChannel)

                // You might want to add a timeout here to prevent indefinite waiting for the shell command.
                // For example, if a shell command hangs, this would prevent the Operator from hanging.
                withTimeoutOrNull(60_000L) { // 60 seconds timeout
                    deferredResult.await()
                } ?: "Command timed out after 60 seconds."
            }
            "file.create" -> {
                val path = cmd.args["path"] ?: ""
                val isDir = cmd.args["isDirectory"]?.toBoolean() ?: false
                val ok = ExecutionEngine.createFile(path, isDir)
                "{\"success\":$ok}"
            }
            "file.write" -> {
                val path = cmd.args["path"] ?: ""
                val content = cmd.args["content"] ?: ""
                val ok = ExecutionEngine.writeFile(path, content)
                "{\"success\":$ok}"
            }
            // The rest of the when block remains the same...
            "file.searchName" -> {
                val keyword = cmd.args["keyword"] ?: ""
                "File search by name not directly supported by ExecutionEngine in this sample. Keyword: $keyword"
            }
            "file.searchContent" -> {
                val content = cmd.args["content"] ?: ""
                "File search by content not directly supported by ExecutionEngine in this sample. Content: $content"
            }
            "file.listModuleIncludes" -> {
                "List module includes not directly supported by ExecutionEngine in this sample."
            }
            "file.listModuleFiles" -> {
                val modulePath = cmd.args["modulePath"] ?: ""
                "List module files not directly supported by ExecutionEngine in this sample."
            }
            "file.upload" -> {
                val path = cmd.args["path"] ?: ""
                "File upload not directly supported by ExecutionEngine in this sample. Path: $path"
            }
            "tabfile.getFile" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val content = cmd.args["content"] ?: ""
                "File content from tab: $filePath\n$content"
            }
            "tabfile.getLine" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val lineRange = cmd.args["lineRange"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Lines $lineRange from tab: $filePath\n$content"
            }
            "tabfile.getCursor" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val cursorLine = cmd.args["cursorLine"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Cursor line $cursorLine from tab: $filePath\n$content"
            }
            "tabfile.getFunction" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val functionName = cmd.args["function"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Function $functionName from tab: $filePath\n$content"
            }
            "task.execute" -> {
                val tasks = cmd.args["tasks"] ?: ""
                "Task execution not directly supported by ExecutionEngine in this sample. Tasks: $tasks"
            }
            "gradle.execute" -> {
                val command = cmd.args["command"] ?: ""
                "Gradle command execution not directly supported by ExecutionEngine in this sample. Command: $command"
            }
            else -> "Unknown command type: ${cmd.type}"
        }

        if (ctxId != null && context != null) {
            contextManager.put(ctxId, "lastResult", result)
        }
        return result
    }
}
