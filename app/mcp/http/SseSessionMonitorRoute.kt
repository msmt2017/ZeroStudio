package android.zero.mcp.http

import android.zero.mcp.sse.SseSessionMonitor
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * SSE 会话监控接口：/sse/monitor 展示当前连接状态
 */
fun Application.sseSessionMonitorRoute() {
    routing {
        get("/sse/monitor") {
            call.respond(
                mapOf(
                    "activeCount" to SseSessionMonitor.activeSessionCount(),
                    "sessions" to SseSessionMonitor.getSnapshot()
                )
            )
        }
    }
}
