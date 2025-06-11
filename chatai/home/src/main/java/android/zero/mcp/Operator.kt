package android.zero.mcp

/**
 * 操作端：根据 Command 调用 ExecutionEngine，并更新上下文。
 */
class Operator(private val contextManager: ContextManager) {

    // Modified to accept ExecuteCommand directly as per interpreter's output and original intent
    suspend fun execute(cmd: ExecuteCommand): String {
        val ctxId = cmd.contextId
        val context = contextManager.getContext(ctxId.orEmpty())

        val result = when (cmd.type) {
            "shell.execute" -> {
                val commandLine = cmd.args["command"] ?: ""
                ExecutionEngine.executeShell(commandLine)
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
                val projectRoot = cmd.args["projectRoot"] ?: "." // Assuming projectRoot comes from args now
                // This would ideally interact with a FileHandler instance, but for simplicity, directly calling ExecutionEngine if it had such methods.
                // Since ExecutionEngine doesn't have search methods, this would need to be handled differently or by calling FileHandler directly.
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
            // Handling for Gradle tasks, assuming ExecutionEngine could delegate to GradleHandler
            "task.execute" -> {
                val tasks = cmd.args["tasks"] ?: ""
                // This would ideally call GradleHandler.handleExecuteTask
                "Task execution not directly supported by ExecutionEngine in this sample. Tasks: $tasks"
            }
            "gradle.execute" -> {
                val command = cmd.args["command"] ?: ""
                val projectRoot = cmd.args["projectRoot"] ?: "."
                // This would ideally call GradleHandler.handleExecuteTask or similar
                "Gradle command execution not directly supported by ExecutionEngine in this sample. Command: $command"
            }
            else -> "Unknown command type: ${cmd.type}"
        }

        // Store result in context
        if (ctxId != null && context != null) {
            contextManager.put(ctxId, "lastResult", result)
        }
        return result
    }
}
