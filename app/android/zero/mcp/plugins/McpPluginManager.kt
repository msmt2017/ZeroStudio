package android.zero.mcp.plugins

import android.zero.mcp.McpServerLog
import android.zero.mcp.McpTool

/**
 * MCP 插件管理器：支持动态添加、移除、查询 MCP 工具
 */
object McpPluginManager {

    private val registeredTools = mutableMapOf<String, McpTool>()

    fun registerTool(name: String, tool: McpTool): Boolean {
        return if (registeredTools.containsKey(name)) {
            McpServerLog.log("插件 [$name] 已存在，忽略重复注册")
            false
        } else {
            registeredTools[name] = tool
            McpServerLog.log("注册插件 [$name] 成功")
            true
        }
    }

    fun unregisterTool(name: String): Boolean {
        return if (registeredTools.remove(name) != null) {
            McpServerLog.log("卸载插件 [$name] 成功")
            true
        } else {
            McpServerLog.log("卸载插件 [$name] 失败：未找到")
            false
        }
    }

    fun listTools(): List<String> = registeredTools.keys.sorted()

    fun getTool(name: String): McpTool? = registeredTools[name]

    fun clearAll() {
        registeredTools.clear()
        McpServerLog.log("所有插件工具已清空")
    }
}
