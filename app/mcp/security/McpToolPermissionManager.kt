package android.zero.mcp.security

import android.zero.mcp.McpServerLog

/**
 * MCP 工具权限管理器：可限制某些工具是否对 AI 开放
 */
object McpToolPermissionManager {

    private val allowedTools = mutableSetOf<String>()
    private val deniedTools = mutableSetOf<String>()

    /**;
     * 设置允许访问的工具（清空旧列表）
     */
    fun allowOnly(vararg tools: String) {
        allowedTools.clear()
        allowedTools.addAll(tools)
        McpServerLog.log("权限：仅允许使用工具: ${tools.toList()}")
    }

    /**
     * 添加允许的工具
     */
    fun allow(tool: String) {
        allowedTools += tool
        deniedTools -= tool
        McpServerLog.log("权限：已允许 $tool")
    }

    /**
     * 禁止某个工具
     */
    fun deny(tool: String) {
        deniedTools += tool
        allowedTools -= tool
        McpServerLog.log("权限：已禁止 $tool")
    }

    /**
     * 判断是否允许某个工具执行
     */
    fun isAllowed(tool: String): Boolean {
        return when {
            deniedTools.contains(tool) -> false
            allowedTools.isEmpty() -> true // 默认为允许
            else -> allowedTools.contains(tool)
        }
    }

    fun listAllowed(): List<String> = allowedTools.toList().sorted()
    fun listDenied(): List<String> = deniedTools.toList().sorted()
}
