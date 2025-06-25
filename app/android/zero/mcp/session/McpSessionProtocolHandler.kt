package android.zero.mcp.session

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.meta.McpToolMetadata
import android.zero.mcp.session.McpSessionCoordinator.dispatchRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * MCP 会话协议处理器：统一处理 AI 会话中 Host-Client-Server 的请求响应/通知链
 */
object McpSessionProtocolHandler {

    fun handleClientRequest(sessionId: String, request: McpRequest): McpResponse {
        // AI 或用户从 Host 发起的操作请求，如调用某工具
        return dispatchRequest(sessionId, request)
    }

    fun handleServerPush(sessionId: String, message: String): Flow<String> = flow {
        // 示例：服务器推送通知，AI 或 UI 可监听更新
        emit("[$sessionId] 状态变更: $message")
    }

    fun getSupportedToolList(): List<Map<String, Any>> {
        return McpToolMetadata.toolList.map {
            mapOf(
                "method" to it.method,
                "description" to it.description,
                "params" to it.params,
                "example" to it.example
            )
        }
    }
}
