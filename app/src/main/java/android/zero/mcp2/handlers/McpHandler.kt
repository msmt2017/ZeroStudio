package android.zero.mcp.handlers

import android.zero.mcp.server.McpRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive

/**
 * 所有 MCP 指令处理器的通用接口。
 */
interface McpHandler {
    /**
     * 处理一个 MCP 请求并返回一个响应对象（McpResponse 或 McpErrorResponse）。
     * @param request 要处理的 MCP 请求。
     * @return 响应结果。
     */
    suspend fun handle(request: McpRequest): Any

    /**
     * 从参数映射中安全地提取字符串。
     * @param params 请求参数。
     * @param key 要提取的键。
     * @return 提取的字符串，如果不存在或类型不匹配则返回 null。
     */
    fun getStringParam(params: JsonElement?, key: String): String? {
        return params?.jsonObject?.get(key)?.jsonPrimitive?.contentOrNull
    }
}
