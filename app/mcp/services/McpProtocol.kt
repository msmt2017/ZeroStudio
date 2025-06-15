package android.zero.mcp.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 代表一个 MCP 请求。
 * @param jsonrpc 协议版本，必须是 "2.0"。
 * @param id 请求的唯一标识符。
 * @param method 要调用的方法名称。
 * @param params 方法的参数。
 */
@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null
)

/**
 * 代表一个成功的 MCP 响应。
 * @param id 必须与对应请求的 id 相同。
 * @param result 方法调用的结果。
 */
@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String,
    val result: JsonElement
) {
    companion object {
        fun success(id: String, message: String): McpResponse {
            return McpResponse(id, buildJsonObject { put("message", message) })
        }
    }
}

/**
 * 代表一个错误的 MCP 响应。
 * @param id 必须与对应请求的 id 相同。对于解析错误等情况，可以为 null。
 * @param error 包含错误详情的 McpError 对象。
 */
@Serializable
data class McpErrorResponse(
    val jsonrpc: String = "2.0",
    val id: String?,
    val error: McpError
)

/**
 * 代表一个 JSON-RPC 错误对象。
 * @param code 错误代码。
 * @param message 错误的简短描述。
 * @param data 可选的、包含额外错误信息的附加数据。
 */
@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        fun parseError(message: String = "Parse error") = McpError(-32700, message)
        fun invalidRequest(message: String = "Invalid Request") = McpError(-32600, message)
        fun methodNotFound(method: String) = McpError(-32601, "Method not found: $method")
        fun invalidParams(message: String = "Invalid params") = McpError(-32602, message)
        fun internalError(message: String = "Internal error") = McpError(-32603, message)
        fun serverError(code: Int, message: String) = McpError(code, message)
    }
}


/**
 * 代表一个 MCP 通知。
 * 通知是单向的，不需要响应。
 * @param method 调用的方法名。
 * @param params 可选参数。
 */
@Serializable
data class McpNotification(
    val jsonrpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null
)
