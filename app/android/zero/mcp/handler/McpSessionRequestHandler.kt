package android.zero.mcp.handler

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.session.McpSessionCoordinator

/**
 * AI 请求 @session.* 指令处理器
 */
object McpSessionRequestHandler {

    fun handle(request: McpRequest): McpResponse {
        return when (request.method) {
            "@session.list" -> {
                McpResponse.success(request.id, McpSessionCoordinator.listSessions())
            }
            "@session.close" -> {
                val id = request.params?.get("id") ?: return McpResponse.error("缺少参数 id", request.id)
                McpSessionCoordinator.endSession(id)
                McpResponse.success(request.id, "已关闭会话 $id")
            }
            "@session.status" -> {
                val id = request.params?.get("id") ?: return McpResponse.error("缺少参数 id", request.id)
                val active = McpSessionCoordinator.isActive(id)
                McpResponse.success(request.id, mapOf("id" to id, "active" to active))
            }
            else -> McpResponse.error("未知会话命令: ${request.method}", request.id)
        }
    }
}
