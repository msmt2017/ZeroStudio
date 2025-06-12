// 文件路径：app/src/main/java/android/zero/mcp/Interpreter.kt
package android.zero.mcp

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

import android.zero.mcp.command.FileCommand

import android.zero.mcp.command.ExecuteCommand
import android.zero.mcp.command.ShellCommand
import android.zero.mcp.command.GradleCommand
import android.zero.mcp.command.TaskCommand
import android.zero.mcp.command.TabFileCommand
import android.zero.mcp.command.QueryCommand

/**
 * 解释端：将 McpRequest 转换为 Command 对象。
 */
class Interpreter(private val contextManager: ContextManager) {

    fun parse(request: McpRequest): Command? {
        val type = request.type
        return when {
            type.startsWith("file.") -> FileCommand(
                id = request.id,
                contextId = request.contextId,
                type = type,
                action = type.removePrefix("file."),
                args = request.args
            )
            type.startsWith("shell.") -> ShellCommand(
                id = request.id,
                contextId = request.contextId,
                type = type,
                command = request.args["command"] as? String ?: "",
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("gradle.") -> GradleCommand(
                id = request.id,
                contextId = request.contextId,
                type = type,
                task = type.removePrefix("gradle."),
                args = (request.args["args"] as? List<*>)?.map { it.toString() } ?: emptyList()
            )
            type.startsWith("task.") -> TaskCommand(
                id = request.id,
                contextId = request.contextId,
                type = type,
                action = type.removePrefix("task."),
                args = request.args
            )
            type.startsWith("tabfile.") -> TabFileCommand(
                id = request.id,
                contextId = request.contextId,
                type = type,
                action = type.removePrefix("tabfile."),
                args = request.args
            )
            type == "query" -> QueryCommand(
                id = request.id,
                contextId = request.contextId,
                query = request.args["query"] as? String ?: "",
                queryType = request.args["queryType"] as? String ?: ""
            )
            else -> throw IllegalArgumentException("Unknown command type: \$type")
        }
    }
}
