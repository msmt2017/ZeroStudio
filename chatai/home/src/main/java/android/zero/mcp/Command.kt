// 文件路径：app/src/main/java/android/zero/mcp/Command.kt
package android.zero.mcp

/**
 * 抽象命令类型。
 */
sealed class Command {
    abstract val id: String          // 原始消息 ID，用于响应关联
    abstract val contextId: String?  // 会话上下文 ID
}

/** 普通执行命令，如 shell、file、gradle 等 */
data class ExecuteCommand(
    override val id: String,
    override val contextId: String?,
    val type: String,               // e.g. "file.searchName", "shell.execute"
    val args: Map<String, String>
) : Command()

/** 查询或其它类型可继续扩展 */
data class QueryCommand(
    override val id: String,
    override val contextId: String?,
    val query: String
) : Command()
