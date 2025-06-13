// File: Interpreter.kt
package android.zero.mcp

class Interpreter(private val contextManager: ContextManager) {

    fun parse(request: McpRequest): Command? {
        val type = request.type
        // FIX: Changed to create new top-level command classes instead of nested ones.
        return when {
            type.startsWith("file.") -> FileCommand(
                id = request.id,
                contextId = request.contextId,
                action = type.removePrefix("file."),
                args = request.args
            )
            type.startsWith("shell.") -> ShellCommand(
                id = request.id,
                contextId = request.contextId,
                command = request.args["command"] ?: "",
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("gradle.") -> GradleCommand(
                id = request.id,
                contextId = request.contextId,
                task = type.removePrefix("gradle."),
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("task.") -> TaskCommand(
                id = request.id,
                contextId = request.contextId,
                action = type.removePrefix("task."),
                args = request.args
            )
            type.startsWith("tabfile.") -> TabFileCommand(
                id = request.id,
                contextId = request.contextId,
                action = type.removePrefix("tabfile."),
                args = request.args
            )
            type == "query" -> QueryCommand(
                id = request.id,
                contextId = request.contextId,
                query = request.args["query"] ?: ""
            )
            else -> null
        }
    }
}