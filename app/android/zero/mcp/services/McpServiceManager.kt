package android.zero.mcp.services

import android.content.Context
import android.content.Intent
import android.zero.mcp.client.McpClient
import android.zero.mcp.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MCP服务管理器
 * 负责管理MCP服务的启动、停止和客户端连接
 */
class McpServiceManager(private val context: Context) {
    
    private var mcpClient: McpClient? = null
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _serverUrl = MutableStateFlow("http://localhost:8080")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()
    
    companion object {
        @Volatile
        private var INSTANCE: McpServiceManager? = null
        
        fun getInstance(context: Context): McpServiceManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: McpServiceManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * 启动MCP服务
     */
    fun startMcpService() {
        try {
            val intent = Intent(context, KtorMcpService::class.java).apply {
                action = "START_SERVER"
            }
            context.startForegroundService(intent)
            Logger.log("MCP service start intent sent")
        } catch (e: Exception) {
            Logger.log("Failed to start MCP service: ${e.message}")
        }
    }
    
    /**
     * 停止MCP服务
     */
    fun stopMcpService() {
        try {
            val intent = Intent(context, KtorMcpService::class.java).apply {
                action = "STOP_SERVER"
            }
            context.startService(intent)
            Logger.log("MCP service stop intent sent")
        } catch (e: Exception) {
            Logger.log("Failed to stop MCP service: ${e.message}")
        }
    }
    
    /**
     * 连接到MCP服务器
     */
    suspend fun connectToServer(url: String = "http://localhost:8080"): Boolean {
        return try {
            _serverUrl.value = url
            _connectionState.value = ConnectionState.CONNECTING
            
            mcpClient?.close()
            mcpClient = McpClient(url)
            
            val isConnected = mcpClient?.checkConnection() ?: false
            if (isConnected) {
                _connectionState.value = ConnectionState.CONNECTED
                Logger.log("Successfully connected to MCP server at $url")
            } else {
                _connectionState.value = ConnectionState.FAILED
                Logger.log("Failed to connect to MCP server at $url")
            }
            
            isConnected
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.FAILED
            Logger.log("Connection error: ${e.message}")
            false
        }
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        mcpClient?.close()
        mcpClient = null
        _connectionState.value = ConnectionState.DISCONNECTED
        Logger.log("Disconnected from MCP server")
    }
    
    /**
     * 获取MCP客户端
     */
    fun getClient(): McpClient? {
        return mcpClient
    }
    
    /**
     * 检查连接状态
     */
    suspend fun checkConnection(): Boolean {
        return mcpClient?.checkConnection() ?: false
    }
    
    /**
     * 获取服务器信息
     */
    suspend fun getServerInfo() = mcpClient?.getServerInfo()
    
    /**
     * 获取工具列表
     */
    suspend fun getToolsList() = mcpClient?.getToolsList()
    
    /**
     * 连接到事件流
     */
    fun connectToEvents() = mcpClient?.connectToEvents()
    
    /**
     * 连接状态枚举
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }
} 