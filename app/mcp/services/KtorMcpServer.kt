package android.zero.mcp.server

import android.zero.mcp.handlers.*
import android.zero.mcp.utils.Logger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * 单例对象，用于创建和管理嵌入式 Ktor 服务器。
 */
object KtorMcpServer {

    // 用于广播 SSE 事件的通道
    val sseChannel = Channel<McpNotification>(Channel.UNLIMITED)

    fun start(): NettyApplicationEngine {
        return embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            // 安装内容协商插件，使用 Kotlinx.serialization 进行 JSON 处理
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true      // 格式化JSON输出，便于调试
                    isLenient = true        // 允许非标准JSON
                    ignoreUnknownKeys = true // 忽略JSON中未在数据类中定义的键
                })
            }
            // 安装服务器发送事件 (SSE) 插件
            install(SSE)

            // 定义路由
            routing {
                // MCP 主命令端点
                post("/mcp") {
                    try {
                        val request = call.receive<McpRequest>()
                        Logger.log("--> RECV ${request.id}: ${request.method}")
                        
                        val response = handleMcpRequest(request)

                        Logger.log("<-- RESP ${request.id}: ${response::class.simpleName}")
                        call.respond(response)
                    } catch (e: Exception) {
                        Logger.log("!!! ERROR processing MCP request: ${e.message}\n${e.stackTraceToString()}")
                        // 返回标准 JSON-RPC 错误
                        call.respond(McpErrorResponse(id = "error-unknown-request", error = McpError.internalError("Error: ${e.message}")))
                    }
                }

                // 用于服务器到客户端通知的 SSE 端点
                sse("/events") {
                    Logger.log("SSE client connected.")
                    try {
                        // 从通道消费通知并发送给客户端
                        sseChannel.consumeAsFlow()
                            .map { notification ->
                                Logger.log("SSE --> SENT: ${notification.method}")
                                ServerSentEvent(
                                    data = Json.encodeToString(McpNotification.serializer(), notification),
                                    event = notification.method
                                )
                            }
                            .collect { sseEvent ->
                                send(sseEvent)
                            }
                    } catch (e: Exception) {
                        Logger.log("!!! SSE connection error: ${e.message}")
                    } finally {
                        Logger.log("SSE client disconnected.")
                    }
                }
            }
        }.start(wait = false) // `wait = false` 使其在后台线程中启动
    }

    /**
     * 根据请求的方法将请求分派给正确的处理器。
     */
    private suspend fun handleMcpRequest(request: McpRequest): Any {
        val handler: McpHandler = when {
            request.method.startsWith("file:") -> FileHandler
            request.method.startsWith("gradle:") -> GradleHandler
            request.method.startsWith("task:") -> TaskHandler
            request.method.startsWith("tabfile:") -> TabFileHandler
            request.method.startsWith("shell:") -> ShellHandler
            request.method == "initialize" -> InitializeHandler
            else -> return McpErrorResponse(id = request.id, error = McpError.methodNotFound(request.method))
        }
        
        return try {
            handler.handle(request)
        } catch (e: Exception) {
            Logger.log("!!! ERROR in handler for ${request.method}: ${e.message}\n${e.stackTraceToString()}")
            McpErrorResponse(request.id, McpError.internalError("Handler error: ${e.message}"))
        }
    }
}
