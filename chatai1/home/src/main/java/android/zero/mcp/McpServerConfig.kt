
package android.zero.mcp

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * MCP 配置基类，支持 SSE & WebSocket 两种模式。
 */
@Serializable
sealed class McpServerConfig {

    /** 通用字段 */
    @Serializable
    data class CommonOptions(
        val id: String = UUID.randomUUID().toString(),
        val name: String = "",
        val enable: Boolean = false,            // ★ 默认关闭
        val headers: List<Pair<String, String>> = emptyList(),
    )

    abstract val commonOptions: CommonOptions

    /** SSE 模式 */
    @Serializable
    data class SseTransportServer(
        override val commonOptions: CommonOptions = CommonOptions(),
        val url: String = "http://127.0.0.1:11583/sse",
    ) : McpServerConfig()

    /** WebSocket 模式（示例，占位） */
    @Serializable
    data class WebSocketServer(
        override val commonOptions: CommonOptions = CommonOptions(),
        val url: String = "ws://127.0.0.1:11584/ws",
    ) : McpServerConfig()
}
