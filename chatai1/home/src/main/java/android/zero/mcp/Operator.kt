package android.zero.mcp

import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import android.zero.mcp.model.McpResponse
import java.util.UUID
import kotlinx.coroutines.async

/**
 * 操作端：根据 Command 调用 ExecutionEngine，并更新上下文。
 */
class Operator(private val contextManager: ContextManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {

    // Modified to accept ExecuteCommand directly as per interpreter's output and original intent
    suspend fun execute(cmd: ExecuteCommand): String {
        val ctxId = cmd.args["contextId"]
        val context = contextManager.getContext(ctxId.orEmpty())

        val result = when (cmd.type) {
            "shell.execute" -> {
                val commandLine = cmd.args["command"] ?: ""
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                val shellHandler = ShellHandler(scope)
                val deferredResult = scope.async {
                    var result = ""
                    for (response in responseChannel) {
                        when (response.event) {
                            "shell.log" -> result += response.result + "\n"
                            "shell.error" -> result = "Error: \${response.result}"
                            "shell.complete" -> {}
                            else -> {}
                        }
                    }
                    result
                }
                shellHandler.handle(mapOf("command" to commandLine, "contextId" to cmd.args["contextId"]), responseChannel)
                deferredResult.await()
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
            "file.searchName" -> {
                val keyword = cmd.args["keyword"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val fileHandler = FileHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                fileHandler.handleSearchByName(cmd.args, responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            "file.searchContent" -> {
                val content = cmd.args["content"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val fileHandler = FileHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                fileHandler.handleSearchByContent(cmd.args, responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            "file.listModuleIncludes" -> {
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val fileHandler = FileHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                fileHandler.handleListIncludes(cmd.args, responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            "file.listModuleFiles" -> {
                val modulePath = cmd.args["modulePath"] ?: ""
                val fileHandler = FileHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                fileHandler.handleListModuleFiles(cmd.args, responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            "file.upload" -> {
                val path = cmd.args["path"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val fileHandler = FileHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                fileHandler.handleUpload(cmd.args, responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            // Handle TabFile commands
            "tabfile.getFile" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val content = try {
                    File(filePath).readText()
                } catch (e: Exception) {
                    null
                }
                if (content != null) "{\"success\":true,\"content\":\"${content.replace(\"\"\", \"\\\"\")}\"}" else "{\"success\":false,\"error\":\"File not found or error reading file\"}"   
            }
            "tabfile.getLine" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val lineRange = cmd.args["lineRange"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Lines \$lineRange from tab: \$filePath\n\$content"
            }
            "tabfile.getCursor" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val cursorLine = cmd.args["cursorLine"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Cursor line \$cursorLine from tab: \$filePath\n\$content"
            }
            "tabfile.getFunction" -> {
                val filePath = cmd.args["filePath"] ?: ""
                val functionName = cmd.args["function"] ?: ""
                val content = cmd.args["content"] ?: ""
                "Function \$functionName from tab: \$filePath\n\$content"
            }
            // Handling for Gradle tasks
            "task.execute" -> {
                val tasks = cmd.args["tasks"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val gradleHandler = GradleHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                gradleHandler.handleExecuteTask(mapOf(
                    "tasks" to tasks,
                    "projectRoot" to projectRoot
                ), responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            "gradle.execute" -> {
                val command = cmd.args["command"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                val gradleHandler = GradleHandler()
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                gradleHandler.handleExecuteTask(mapOf(
                    "command" to command,
                    "projectRoot" to projectRoot
                ), responseChannel)
                val response = responseChannel.receive()
                response.result
            }
            else -> "Unknown command type: \${cmd.type}"
        }

        // Store result in context
        if (ctxId != null && context != null) {
            contextManager.put(ctxId, "lastResult", result)
        }
        return result
    }
}
