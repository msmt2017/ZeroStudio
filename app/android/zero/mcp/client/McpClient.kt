package android.zero.mcp.client

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpNotification
import android.zero.mcp.utils.Logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MCP客户端
 * 用于连接MCP服务器并发送请求
 */
class McpClient(
    private val serverUrl: String = "http://localhost:8080",
    private val timeout: Long = 30000
) {
    
    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        install(SSE) {
            // SSE配置
        }
        
        engine {
            // 配置超时
            config {
                connectTimeout(timeout)
                readTimeout(timeout)
                writeTimeout(timeout)
            }
        }
    }
    
    companion object {
        private const val DEFAULT_SERVER_URL = "http://localhost:8080"
        private const val DEFAULT_TIMEOUT = 30000L
    }
    
    /**
     * 检查服务器连接状态
     */
    suspend fun checkConnection(): Boolean {
        return try {
            val response = httpClient.get("$serverUrl/health")
            response.status == HttpStatusCode.OK
        } catch (e: Exception) {
            Logger.log("Connection check failed: ${e.message}")
            false
        }
    }
    
    /**
     * 发送MCP请求
     */
    suspend fun sendRequest(request: McpRequest): McpResponse {
        return try {
            Logger.log("Sending MCP request: ${request.method}")
            
            val response = httpClient.post("$serverUrl/mcp") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            response.body<McpResponse>()
        } catch (e: Exception) {
            Logger.log("Request failed: ${e.message}")
            McpResponse.error("Request failed: ${e.message}", request.id)
        }
    }
    
    /**
     * 获取服务器信息
     */
    suspend fun getServerInfo(): JsonElement? {
        return try {
            val response = httpClient.get("$serverUrl/info")
            if (response.status == HttpStatusCode.OK) {
                response.body<JsonElement>()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.log("Failed to get server info: ${e.message}")
            null
        }
    }
    
    /**
     * 获取工具列表
     */
    suspend fun getToolsList(): JsonElement? {
        return try {
            val response = httpClient.get("$serverUrl/tools")
            if (response.status == HttpStatusCode.OK) {
                response.body<JsonElement>()
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.log("Failed to get tools list: ${e.message}")
            null
        }
    }
    
    /**
     * 连接到SSE事件流
     */
    fun connectToEvents(): Flow<McpNotification> {
        return httpClient.sse("$serverUrl/events")
            .map { event ->
                try {
                    Json.decodeFromString(McpNotification.serializer(), event.data)
                } catch (e: Exception) {
                    Logger.log("Failed to parse SSE event: ${e.message}")
                    McpNotification(method = "error", params = buildJsonObject {
                        put("error", "Failed to parse event")
                    })
                }
            }
    }
    
    /**
     * 关闭客户端
     */
    fun close() {
        httpClient.close()
    }
} 