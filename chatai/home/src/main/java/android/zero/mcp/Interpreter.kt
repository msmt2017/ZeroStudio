// 文件路径：app/src/main/java/android/zero/mcp/Interpreter.kt
package android.zero.mcp

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * 解释端：将 McpRequest 转换为 Command 对象。
 */
class Interpreter(private val contextManager: ContextManager) {

    fun parse(request: McpRequest): Command? {
        return when {
            request.type.startsWith("file.")
                    || request.type.startsWith("shell.")
                    || request.type.startsWith("gradle.")
                    || request.type.startsWith("task.") -> {
                ExecuteCommand(
                    id = request.args["id"] ?: "",            // 如果你在 args 里带上 id
                    contextId = request.args["contextId"],
                    type = request.type,
                    args = request.args.filterKeys { it != "id" && it != "contextId" }
                )
            }
            request.type == "query" -> {
                val query = request.args["q"] ?: ""
                QueryCommand(request.args["id"] ?: "", request.args["contextId"], query)
            }
            else -> null
        }
    }
}
