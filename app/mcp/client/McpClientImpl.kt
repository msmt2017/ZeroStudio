package android.zero.mcp.client

import android.zero.mcp.McpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * MCP 客户端调用器：负责将请求发送至 MCP 服务端 (本地 http://127.0.0.1:mcpPort)
 */
class McpClientImpl(
    private val baseUrl: String = "http://127.0.0.1:11583",
    private val httpClient: OkHttpClient = OkHttpClient()
) {
    suspend fun send(request: McpRequest): String = withContext(Dispatchers.IO) {
        val json = JSONObject()
        json.put("id", request.id)
        json.put("jsonrpc", request.jsonrpc)
        json.put("method", request.method)
        json.put("params", request.params ?: emptyMap<String, String>())

        val body = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val req = Request.Builder()
            .url("$baseUrl/mcp")
            .post(body)
            .build()

        val resp = httpClient.newCall(req).execute()
        return@withContext resp.body?.string() ?: ""
    }
}
