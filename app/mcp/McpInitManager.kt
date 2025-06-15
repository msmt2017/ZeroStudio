package android.zero.mcp

import android.content.Context
import android.zero.mcp.registry.McpToolRegistry
import android.zero.mcp.storage.McpSettingsSerializer
import android.zero.mcp.storage.McpSettingsStorage

/**
 * MCP 启动初始化模块：统一初始化配置、工具注册与状态恢复
 *  McpInitManager.initialize(applicationContext, mcpServer)

 */
object McpInitManager.initialize(applicationContext, mcpServer)
 {

    fun initialize(context: Context, server: McpServer) {
        McpSettingsStorage.init(context)
        McpSettingsSerializer.loadAll()
        McpToolRegistry.registerAllTools(context, server)
    }
}
