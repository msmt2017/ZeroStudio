package android.zero.mcp.http

import android.zero.mcp.McpRequest
import android.zero.mcp.McpTool
import android.zero.mcp.McpResponse
import android.zero.mcp.registry.McpToolRegistry
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.runBlocking

/**
 * 简易测试路由：可通过 GET /test/tool?method=xxx&k=v 测试调用工具
 * GET /test/tool?method=File.search&path=src&content=main

 */
fun Application.toolTestRoute() {
    routing {
        get("/test/tool") {
            val method = call.parameters["method"] ?: return@get call.respondText(
                "缺少参数 method", status = HttpStatusCode.BadRequest
            )
            val tool: McpTool = McpToolRegistry.getRegisteredTool(method)
                ?: return@get call.respondText("未找到工具: $method", status = HttpStatusCode.NotFound)

            val params = call.parameters.toMap().filterKeys { it != "method" }.mapValues { it.value.first() }
            val response: McpResponse = runBlocking {
                tool.invoke(
                    McpRequest(
                        id = "test-${System.currentTimeMillis()}",
                        method = method,
                        params = params.toMutableMap()
                    )
                )
            }

            call.respond(response)
        }
    }
}
