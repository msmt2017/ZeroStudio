package android.zero.mcp.http

import android.zero.mcp.meta.McpToolMetadata
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * MCP 工具公开接口：提供 /tools JSON 工具描述，供 AI 查询帮助
 */
fun Application.mcpToolListRoute() {
    routing {
        get("/tools") {
            val data = McpToolMetadata.toolList.map {
                mapOf(
                    "method" to it.method,
                    "description" to it.description,
                    "params" to it.params,
                    "example" to it.example
                )
            }
            call.respond(HttpStatusCode.OK, data)
        }
    }
}
