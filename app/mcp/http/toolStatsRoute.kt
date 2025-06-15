package android.zero.mcp.http

import android.zero.mcp.monitor.ToolStatsTracker
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * MCP 工具统计数据接口：/tools/stats 返回 JSON 调用数据
 */
fun Application.toolStatsRoute() {
    routing {
        get("/tools/stats") {
            call.respond(ToolStatsTracker.getStats())
        }
    }
}
