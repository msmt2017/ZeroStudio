package android.zero.mcp.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.response.*
import kotlinx.html.*

/**
 * HTTP 调试页面：开发者可通过浏览器访问，查看 MCP 工具及调用演示
 */
fun Application.debugPage() {
    routing {
        get("/debug") {
            call.respondHtml(HttpStatusCode.OK) {
                head {
                    title { +"MCP Debug Page" }
                }
                body {
                    h1 { +"MCP 调试页面" }
                    p { +"以下为所有已注册工具方法名：" }
                    ul {
                        android.zero.mcp.meta.McpToolMetadata.toolList.forEach {
                            li { +it.method }
                        }
                    }
                    hr {}
                    p {
                        +"你可以通过 HTTP POST 请求调用 MCP 工具："
                        br()
                        code { +"POST /mcp { method, params }" }
                    }
                }
            }
        }
    }
}