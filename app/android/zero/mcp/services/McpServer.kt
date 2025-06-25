package android.zero.mcp

import android.zero.mcp.meta.McpToolMetadata
import android.zero.mcp.registry.McpToolRegistry2
import android.zero.mcp.utils.Logger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * MCP服务器核心类
 * 负责管理工具注册、请求处理和事件通知
 */
class McpServer {
    
    private val requestIdCounter = AtomicLong(1)
    private val registeredTools = ConcurrentHashMap<String, McpTool>()
    private val eventFlow = MutableSharedFlow<McpNotification>(replay = 100)
    
    companion object {
        @Volatile
        private var INSTANCE: McpServer? = null
        
        fun getInstance(): McpServer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: McpServer().also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 注册工具
     */
    fun registerTool(name: String, tool: McpTool) {
        registeredTools[name] = tool
        Logger.log("Registered tool: $name")
    }
    
    /**
     * 注销工具
     */
    fun unregisterTool(name: String) {
        registeredTools.remove(name)
        Logger.log("Unregistered tool: $name")
    }
    
    /**
     * 获取所有注册的工具
     */
    fun getRegisteredTools(): Map<String, McpTool> {
        return registeredTools.toMap()
    }
    
    /**
     * 处理MCP请求
     */
    suspend fun handleRequest(request: McpRequest): McpResponse {
        return try {
            Logger.log("Processing request: ${request.method}")
            
            val tool = registeredTools[request.method]
            if (tool == null) {
                return McpResponse.error("Tool not found: ${request.method}", request.id)
            }
            
            val result = tool.invoke(request)
            Logger.log("Request completed: ${request.method}")
            result
            
        } catch (e: Exception) {
            Logger.log("Error processing request ${request.method}: ${e.message}")
            McpResponse.error("Internal error: ${e.message}", request.id)
        }
    }
    
    /**
     * 发送通知
     */
    suspend fun sendNotification(method: String, params: JsonElement? = null) {
        val notification = McpNotification(method = method, params = params)
        eventFlow.emit(notification)
        Logger.log("Sent notification: $method")
    }
    
    /**
     * 获取事件流
     */
    fun getEventFlow() = eventFlow
    
    /**
     * 生成请求ID
     */
    fun generateRequestId(): String {
        return "req_${requestIdCounter.getAndIncrement()}"
    }
    
    /**
     * 获取服务器信息
     */
    fun getServerInfo(): JsonElement {
        return buildJsonObject {
            put("name", "AndroidIDE MCP Server")
            put("version", "1.0.0")
            put("tools", buildJsonObject {
                registeredTools.keys.forEach { toolName ->
                    put(toolName, buildJsonObject {
                        put("description", McpToolMetadata.getToolHelp(toolName)?.description ?: "No description")
                    })
                }
            })
        }
    }
    
    /**
     * 初始化服务器
     */
    fun initialize() {
        Logger.log("Initializing MCP Server...")
        // 服务器初始化逻辑
        Logger.log("MCP Server initialized with ${registeredTools.size} tools")
    }
    
    /**
     * 关闭服务器
     */
    fun shutdown() {
        Logger.log("Shutting down MCP Server...")
        registeredTools.clear()
        Logger.log("MCP Server shutdown complete")
    }
} 