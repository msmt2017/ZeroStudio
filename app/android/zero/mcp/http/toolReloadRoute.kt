package android.zero.mcp.http

import android.zero.mcp.registry.McpToolRegistry
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * MCP 工具热重载接口：刷新注册表 + 元数据
 */
fun Application.toolReloadRoute() {
    routing {
        post("/tools/reload") {
            McpToolRegistry.reloadAll() // 你需确保实现 reloadAll
            call.respondText("🔁 工具已刷新", status = io.ktor.http.HttpStatusCode.OK)
        }
    }
}
