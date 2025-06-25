package android.zero.mcp.sse

import java.util.concurrent.ConcurrentHashMap

/**
 * SSE 会话监控器：记录当前连接用户与事件数
 */
object SseSessionMonitor {
    private val sessions = ConcurrentHashMap<String, Int>()

    fun register(sessionId: String) {
        sessions[sessionId] = (sessions[sessionId] ?: 0) + 1
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
    }

    fun getSnapshot(): List<Map<String, Any>> {
        return sessions.map { (id, count) ->
            mapOf("sessionId" to id, "events" to count)
        }
    }

    fun incrementEvent(sessionId: String) {
        sessions[sessionId] = (sessions[sessionId] ?: 0) + 1
    }

    fun activeSessionCount(): Int = sessions.size
}
