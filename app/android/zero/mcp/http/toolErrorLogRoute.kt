package android.zero.mcp.http

import android.zero.mcp.monitor.ToolErrorLogManager
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 提供 /errors/logs 接口：展示近期错误日志
 */
fun Application.toolErrorLogRoute() {
    routing {
        get("/errors/logs") {
            call.respond(ToolErrorLogManager.getAll())
        }
    }
}
