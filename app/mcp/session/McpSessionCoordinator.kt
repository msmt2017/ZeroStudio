package android.zero.mcp.session

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpServerLog
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP 活动会话协调器：管理 Host-Client-Server 协议交互生命周期
 */
object McpSessionCoordinator {

    private val activeSessions = ConcurrentHashMap<String, McpSession>()

    fun startSession(sessionId: String): Boolean {
        if (activeSessions.containsKey(sessionId)) return false
        activeSessions[sessionId] = McpSession(sessionId)
        McpServerLog.log("启动会话: $sessionId")
        return true
    }

    fun endSession(sessionId: String) {
        activeSessions.remove(sessionId)
        McpServerLog.log("结束会话: $sessionId")
    }

    fun dispatchRequest(sessionId: String, request: McpRequest): McpResponse {
        val session = activeSessions[sessionId] ?: return McpResponse.error("无效会话: $sessionId", request.id)
        return session.process(request)
    }

    fun isActive(sessionId: String): Boolean = activeSessions.containsKey(sessionId)

    fun listSessions(): List<String> = activeSessions.keys().toList()
}

class McpSession(val sessionId: String) {
    fun process(request: McpRequest): McpResponse {
        McpServerLog.log("[$sessionId] 收到请求: ${request.method}")
        // 可扩展绑定上下文、模型 ID、用户身份等
        return McpResponse.success(request.id, "模拟会话响应: ${request.method}")
    }
}
