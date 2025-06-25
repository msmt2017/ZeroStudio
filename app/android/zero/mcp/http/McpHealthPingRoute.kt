package android.zero.mcp.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * MCP 健康检查接口：提供 /ping /health 两个简单的响应
 */
fun Application.mcpHealthPingRoute() {
    routing {
        get("/ping") {
            call.respondText("pong", ContentType.Text.Plain)
        }
        get("/health") {
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to "healthy",
                "uptime" to System.currentTimeMillis()
            ))
        }
    }
}
