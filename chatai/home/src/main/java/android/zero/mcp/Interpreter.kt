package android.zero.mcp

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class Interpreter(private val contextManager: ContextManager) {

    fun parse(request: McpRequest): Command? {
        val type = request.type
        return when {
            type.startsWith("file.") -> ExecuteCommand.FileCommand(
                id = request.id,
                contextId = request.contextId,
                action = type.removePrefix("file."),
                args = request.args
            )
            type.startsWith("shell.") -> ExecuteCommand.ShellCommand(
                id = request.id,
                contextId = request.contextId,
                command = request.args["command"] ?: "",
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("gradle.") -> ExecuteCommand.GradleCommand(
                id = request.id,
                contextId = request.contextId,
                task = type.removePrefix("gradle."),
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("task.") -> ExecuteCommand.TaskCommand(
                id = request.id,
                contextId = request.contextId,
                action = type.removePrefix("task."),
                args = request.args
            )
            type.startsWith("tabfile.") -> ExecuteCommand.TabFileCommand(
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