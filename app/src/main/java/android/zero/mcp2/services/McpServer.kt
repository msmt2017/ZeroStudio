package android.zero.mcp

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.LifecycleService
import io.ktor.application.*
import io.ktor.features.ContentNegotiation
import io.ktor.features.CallLogging
import io.ktor.http.ContentType
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * MCP 服务核心框架，使用 Ktor 搭建本地服务器。
 */
class McpServer(private val context: Context) {

    private val port = 11583
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val tools: MutableMap<String, McpTool> = ConcurrentHashMap()

    fun start() {
        serverScope.launch {
            embeddedServer(Netty, port = port, host = "127.0.0.1") {
                install(ContentNegotiation) {
                    json(Json { prettyPrint = true; ignoreUnknownKeys = true })
                }
                install(CallLogging)
                routing {
                    post("/v1/mcp") {
                        val call = call.receive<McpRequest>()
                        val response = handleMcpCall(call)
                        call.respond(response)
                    }
                }
            }.start(wait = false)
            McpServerLog.log("MCP Server started at http://127.0.0.1:$port")
        }
    }

    private suspend fun handleMcpCall(req: McpRequest): McpResponse {
        val method = req.method ?: return McpResponse.error("Missing method")
        val tool = tools[method] ?: return McpResponse.error("Tool not found: $method")
        return try {
            tool.invoke(req)
        } catch (e: Exception) {
            McpServerLog.log("Error executing tool: ${e.message}\n${Log.getStackTraceString(e)}")
            McpResponse.error("Exception in tool execution: ${e.message}")
        }
    }

    fun registerTool(name: String, tool: McpTool) {
        tools[name] = tool
        McpServerLog.log("Registered tool: $name")
    }
}

interface McpTool {
    suspend fun invoke(request: McpRequest): McpResponse
}

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val method: String? = null,
    val params: Map<String, String>? = null
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: String? = null,
    val result: String? = null,
    val error: String? = null
) {
    companion object {
        fun success(id: String?, result: String): McpResponse = McpResponse(id = id, result = result)
        fun error(message: String, id: String? = null): McpResponse = McpResponse(id = id, error = message)
    }
}

/**
 * MCP Server 日志收集类，后续接入 Compose UI 显示。
 */
object McpServerLog {
    private val logs = mutableListOf<String>()

    fun log(msg: String) {
        logs += "[${System.currentTimeMillis()}] $msg"
        Log.i("McpServer", msg)
        // TODO: 推送日志到 McpServerLogFragment
    }

    fun getLogs(): List<String> = logs.toList()
}
