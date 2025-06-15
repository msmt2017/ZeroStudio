package android.zero.mcp.handler

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.meta.McpToolMetadata

/**
 * 支持 AI 请求 @tool.help 指令时，返回指令帮助说明
 */
object ToolHelpRequestHandler {

    fun handle(request: McpRequest): McpResponse {
        val target = request.params?.get("name") ?: return McpResponse.error("缺少参数 name", request.id)
        val meta = McpToolMetadata.getToolHelp(target)
            ?: return McpResponse.error("未找到工具: $target", request.id)

        return McpResponse.success(
            id = request.id,
            result = mapOf(
                "method" to meta.method,
                "description" to meta.description,
                "params" to meta.params,
                "example" to meta.example
            )
        )
    }
}
