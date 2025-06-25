package android.zero.mcp.server

import android.content.Context
import android.zero.mcp.McpServer
import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpNotification
import android.zero.mcp.registry.McpToolRegistry2
import android.zero.mcp.utils.Logger
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import app.mcp.http.mcpCommandRoute

/**
 * Ktor MCP服务器
 * 提供HTTP和SSE接口的MCP服务器实现
 */
class KtorMcpServer(private val context: Context) {
    
    private var server: NettyApplicationEngine? = null
    private val mcpServer = McpServer.getInstance()
    
    companion object {
        const val DEFAULT_PORT = 8080
        const val DEFAULT_HOST = "0.0.0.0"
    }
    
    /**
     * 启动服务器
     */
    fun start(port: Int = DEFAULT_PORT, host: String = DEFAULT_HOST): NettyApplicationEngine {
        Logger.log("Starting Ktor MCP Server on $host:$port")
        
        // 初始化MCP服务器
        initializeMcpServer()
        
        server = embeddedServer(Netty, port = port, host = host) {
            // 安装插件
            installPlugins()
            
            // 配置路由
            configureRouting()
        }.start(wait = false)
        
        Logger.log("Ktor MCP Server started successfully")
        return server!!
    }
    
    /**
     * 停止服务器
     */
    fun stop() {
        Logger.log("Stopping Ktor MCP Server...")
        server?.stop(1000, 5000)
        server = null
        mcpServer.shutdown()
        Logger.log("Ktor MCP Server stopped")
    }
    
    /**
     * 安装Ktor插件
     */
    private fun Application.installPlugins() {
        // 内容协商插件
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        // CORS插件
        install(CORS) {
            anyHost()
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowHeader("Content-Type")
            allowHeader("Authorization")
        }
        
        // SSE插件
        install(SSE)
    }
    
    /**
     * 配置路由
     */
    private fun Application.configureRouting() {
        routing {
            // 健康检查端点
            get("/health") {
                call.respond(buildJsonObject {
                    put("status", "ok")
                    put("service", "mcp-server")
                    put("version", "1.0.0")
                })
            }
            
            // MCP主端点
            post("/mcp") {
                handleMcpRequest()
            }
            
            // SSE事件端点
            sse("/events") {
                handleSseConnection()
            }
            
            // 工具列表端点
            get("/tools") {
                handleToolsList()
            }
            
            // 服务器信息端点
            get("/info") {
                handleServerInfo()
            }
        }
        // 集成 core 指令体系的 /command 路由
        mcpCommandRoute()
    }
    
    /**
     * 处理MCP请求
     */
    private suspend fun ApplicationCall.handleMcpRequest() {
        try {
            val request = receive<McpRequest>()
            Logger.log("Received MCP request: ${request.method}")
            
            val response = mcpServer.handleRequest(request)
            
            Logger.log("Sending MCP response for: ${request.method}")
            respond(response)
            
        } catch (e: Exception) {
            Logger.log("Error handling MCP request: ${e.message}")
            val errorResponse = McpResponse.error("Request processing error: ${e.message}", "error")
            respond(errorResponse)
        }
    }
    
    /**
     * 处理SSE连接
     */
    private suspend fun ApplicationCall.handleSseConnection() {
        Logger.log("SSE client connected")
        
        try {
            // 发送连接确认
            send(ServerSentEvent(
                data = Json.encodeToString(
                    McpNotification.serializer(),
                    McpNotification(method = "connection.established")
                ),
                event = "connection.established"
            ))
            
            // 监听MCP事件
            mcpServer.getEventFlow().collectLatest { notification ->
                val sseEvent = ServerSentEvent(
                    data = Json.encodeToString(McpNotification.serializer(), notification),
                    event = notification.method
                )
                send(sseEvent)
                Logger.log("Sent SSE event: ${notification.method}")
            }
            
        } catch (e: Exception) {
            Logger.log("SSE connection error: ${e.message}")
        } finally {
            Logger.log("SSE client disconnected")
        }
    }
    
    /**
     * 处理工具列表请求
     */
    private suspend fun ApplicationCall.handleToolsList() {
        val tools = mcpServer.getRegisteredTools()
        val toolsList = buildJsonObject {
            put("tools", buildJsonObject {
                tools.forEach { (name, _) ->
                    put(name, buildJsonObject {
                        put("name", name)
                        put("description", "MCP Tool: $name")
                    })
                }
            })
        }
        respond(toolsList)
    }
    
    /**
     * 处理服务器信息请求
     */
    private suspend fun ApplicationCall.handleServerInfo() {
        val info = mcpServer.getServerInfo()
        respond(info)
    }
    
    /**
     * 初始化MCP服务器
     */
    private fun initializeMcpServer() {
        mcpServer.initialize()
        
        // 注册所有工具
        McpToolRegistry2.registerAllTools(context, mcpServer)
        
        Logger.log("MCP Server initialized with ${mcpServer.getRegisteredTools().size} tools")
    }
}
