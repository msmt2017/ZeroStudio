package android.zero.mcp.http

import android.zero.mcp.meta.McpToolMetadata
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 提供 /tools.md 页面：以 Markdown 方式展示所有 MCP 工具信息
 */
fun Application.mcpToolsMarkdownRoute() {
    routing {
        get("/tools.md") {
            val md = buildString {
                append("# MCP 工具列表\n\n")
                McpToolMetadata.toolList.forEach { tool ->
                    append("## ${tool.method}\n")
                    append("- 描述: ${tool.description}\n")
                    append("- 参数: ${tool.params.joinToString(", ")}\n")
                    append("- 示例: \n\n\t${tool.example}\n\n")
                }
            }
            call.respondText(md, ContentType.Text.Markdown)
        }
    }
}
