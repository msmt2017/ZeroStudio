package android.zero.mcp.handler

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.meta.McpToolMetadata
import kotlin.random.Random

/**
 * AI 工具探索用：@tool.random 提供一个随机可用工具作为示例
 */
object ToolRandomSuggestHandler {

    fun handle(request: McpRequest): McpResponse {
        val pool = McpToolMetadata.toolList
        if (pool.isEmpty()) return McpResponse.error("无可用工具", request.id)

        val chosen = pool.random(Random.Default)
        return McpResponse.success(
            id = request.id,
            result = mapOf(
                "method" to chosen.method,
                "description" to chosen.description,
                "params" to chosen.params,
                "example" to chosen.example
            )
        )
    }
}
