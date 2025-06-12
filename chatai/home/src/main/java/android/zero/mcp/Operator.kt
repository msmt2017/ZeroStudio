package android.zero.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async

class Operator(
    private val contextManager: ContextManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    suspend fun execute(cmd: ExecuteCommand): String {
        val ctxId = cmd.contextId
        val context = contextManager.getContext(ctxId.orEmpty())

        val result = when (cmd.type) {
            "shell.execute" -> {
                val commandLine = cmd.args["command"] ?: ""
                val shellHandler = ShellHandler(scope)
                val responseChannel = Channel<McpResponse>(Channel.UNLIMITED)
                val deferredResult = scope.async {
                    var result = ""
                    for (response in responseChannel) {
                        when (response.event) {
                            "shell.log" -> result += response.result + "\n"
                            "shell.error" -> result = "Error: ${response.result}"
                            "shell.complete" -> {}
                        }
                    }
                    result
                }
                shellHandler.handle(cmd.args, responseChannel)
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
                "File search by name not directly supported by ExecutionEngine in this sample. Keyword: $keyword"
            }
            "file.searchContent" -> {
                val content = cmd.args["content"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                "File search by content not directly supported by ExecutionEngine in this sample. Content: $content"
            }
            "file.listModuleIncludes" -> {
                val projectRoot = cmd.args["projectRoot"] ?: "."
                "List module includes not directly supported by ExecutionEngine in this sample."
            }
            "file.listModuleFiles" -> {
                val modulePath = cmd.args["modulePath"] ?: ""
                "List module files not directly supported by ExecutionEngine in this sample."
            }
            "file.upload" -> {
                val path = cmd.args["path"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
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
                val projectRoot = cmd.args["projectRoot"] ?: "."
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