package android.zero.mcp.handler

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.meta.McpToolMetadata

/**
 * 支持 AI 请求 @tool.search 指令，根据关键词搜索工具说明
 */
object ToolSearchRequestHandler {

    fun handle(request: McpRequest): McpResponse {
        val keyword = request.params?.get("keyword")?.lowercase()
            ?: return McpResponse.error("缺少参数 keyword", request.id)

        val matches = McpToolMetadata.toolList.filter {
            it.method.lowercase().contains(keyword) ||
            it.description.lowercase().contains(keyword)
        }.map {
            mapOf(
                "method" to it.method,
                "description" to it.description,
                "params" to it.params,
                "example" to it.example
            )
        }

        return McpResponse.success(
            id = request.id,
            result = matches
        )
    }
}
