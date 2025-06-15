package android.zero.mcp.server

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpServerLog
import android.zero.mcp.policy.ToolExecutionPolicy
import android.zero.mcp.registry.McpToolRegistry
import android.zero.mcp.security.McpToolPermissionManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.mcpServerCore() {
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
    }

    routing {

        post("/mcp") {
            val request = call.receive<McpRequest>()
            McpServerLog.log("收到 MCP 请求: ${request.method}")

            val tool = McpToolRegistry.getRegisteredTool(request.method)

            if (tool == null) {
                call.respond(HttpStatusCode.NotFound, McpResponse.error("未找到工具: ${request.method}", request.id))
                return@post
            }

            if (!McpToolPermissionManager.isAllowed(request.method)) {
                call.respond(HttpStatusCode.Forbidden, McpResponse.error("工具被禁止调用: ${request.method}", request.id))
                return@post
            }

            if (!ToolExecutionPolicy.isExecutable(request.method)) {
                call.respond(HttpStatusCode.Forbidden, McpResponse.error("模型无权调用该工具: ${request.method}", request.id))
                return@post
            }

            val result = tool.invoke(request)
            call.respond(result)
        }

        get("/ping") {
            call.respondText("MCP Ktor 服务器运行中", ContentType.Text.Plain)
        }
    }
}
