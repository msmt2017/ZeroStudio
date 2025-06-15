package android.zero.mcp.sse

import io.ktor.server.application.*
import io.ktor.server.plugins.sse.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 *  SSE 会话：集成 SseSessionMonitor 自动记录
 */
fun Application.sseWithMonitoring() {
    install(SSE)

    routing {
        get("/sse/session") {
            val sessionId = call.parameters["id"] ?: "guest-${System.currentTimeMillis()}"
            SseSessionMonitor.register(sessionId)
            call.respondSse {
                send(SseEvent(data = "连接成功: $sessionId", id = sessionId))
                flow {
                    repeat(10) {
                        emit("SSE 时间戳: ${Instant.now()}")
                        delay(1000)
                    }
                }.collect {
                    SseSessionMonitor.incrementEvent(sessionId)
                    send(SseEvent(data = it, id = sessionId))
                }
            }
            SseSessionMonitor.unregister(sessionId)
        }
    }
}
