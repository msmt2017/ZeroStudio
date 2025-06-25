package android.zero.mcp.http

import android.zero.mcp.meta.McpToolMetadata
import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.routing.*
import kotlinx.html.*

/**
 * 可视化 HTML 工具列表页面
 */
fun Application.toolDocHtmlPage() {
    routing {
        get("/tools.html") {
            call.respondHtml {
                head { title { +"MCP 工具列表" } }
                body {
                    h1 { +"MCP 工具说明文档" }
                    McpToolMetadata.toolList.forEach { tool ->
                        h2 { +tool.method }
                        ul {
                            li { b { +"描述: " }; +tool.description }
                            li { b { +"参数: " }; +tool.params.joinToString() }
                            li {
                                b { +"示例: " }
                                pre { +tool.example }
                            }
                        }
                    }
                }
            }
        }
    }
}
