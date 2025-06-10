// 文件路径：app/src/main/java/android/zero/mcp/McpStatus.kt
package android.zero.mcp

/**
 * 表示一个 MCP 配置当前的连接状态。
 */
sealed class McpStatus {
    object Idle : McpStatus()                 // 未连接 / 已停止
    object Connecting : McpStatus()           // 正在尝试连接
    object Connected : McpStatus()            // 已连接
    data class Error(val message: String) : McpStatus() // 连接/运行异常
}
