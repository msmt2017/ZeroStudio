package android.zero.mcp

import android.content.Context
import android.zero.mcp.registry.McpToolRegistry2
import android.zero.mcp.storage.McpSettingsSerializer
import android.zero.mcp.storage.McpSettingsStorage
import android.zero.mcp.utils.EditorActivityLifecycleListener
import android.zero.mcp.services.McpServiceManager
import android.zero.mcp.services.RikkahubAIIntegrationService
import app.mcp.core.CommandRegistry
import app.mcp.tools.file.WriteFileCommandTool

/**
 * MCP 启动初始化模块：统一初始化配置、工具注册与状态恢复
 *  McpInitManager.initialize(applicationContext, mcpServer)

 */
object McpInitManager {

    private val editorActivityListener = EditorActivityLifecycleListener()
    private var isInitialized = false

    fun initialize(context: Context, server: McpServer) {
        if (isInitialized) {
            return
        }
        
        try {
            // 初始化存储
            McpSettingsStorage.init(context)
            McpSettingsSerializer.loadAll()
            
            // 注册工具
            McpToolRegistry2.registerAllTools(context, server)
            
            // 注册 core 指令工具
            val workspaceRoot = context.filesDir // TODO: 替换为实际工作区根目录
            CommandRegistry.register(WriteFileCommandTool(workspaceRoot))
            
            // 注册编辑器活动生命周期监听器
            if (context is android.app.Application) {
                editorActivityListener.register(context)
            }
            
            // 初始化服务管理器
            McpServiceManager.getInstance(context)
            
            // 初始化AI集成服务
            RikkahubAIIntegrationService.initialize(context)
            
            isInitialized = true
            Logger.log("MCP InitManager initialized successfully")
            
        } catch (e: Exception) {
            Logger.log("Failed to initialize MCP InitManager: ${e.message}")
            throw e
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup(context: Context) {
        try {
            if (context is android.app.Application) {
                editorActivityListener.unregister(context)
            }
            
            // 停止MCP服务
            McpServiceManager.getInstance(context).stopMcpService()
            
            // 关闭AI集成服务
            RikkahubAIIntegrationService.shutdown()
            
            isInitialized = false
            Logger.log("MCP InitManager cleaned up")
            
        } catch (e: Exception) {
            Logger.log("Error during MCP InitManager cleanup: ${e.message}")
        }
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}
