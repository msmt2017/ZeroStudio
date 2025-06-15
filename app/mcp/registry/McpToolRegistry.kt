package android.zero.mcp.registry

import android.content.Context
import android.zero.mcp.McpServer
import android.zero.mcp.McpTool
import android.zero.mcp.meta.McpToolMetadata
import android.zero.mcp.tools.* // 假设你的所有工具类都集中在此包

/**
 * MCP 工具注册入口：集中注册所有工具并收集元信息
 */
object McpToolRegistry {

    private val tools = mutableMapOf<String, McpTool>()

    fun registerAllTools(context: Context, server: McpServer) {
        tools.clear()

        listOf(
            TabFileGetCursorTool(),
            TabFileGetLineTool(),
            TabFileSearchTool(),
            FileGetFileTool(),
            FileGetModuleInfoTool(),
            GradleRunProjectTool(),
            TaskRunTaskTool(),
            TaskListTool(),
            ShellExecuteTool(),
            // 更多你的工具类
        ).forEach { register(it) }
    }

    private fun register(tool: McpTool) {
        tools[tool.method] = tool
        McpToolMetadata.register(tool) // 记录说明元数据
    }

    fun getRegisteredTool(method: String): McpTool? = tools[method]

    fun reloadAll() {
        // TODO: 你可加载插件、重新扫描类、重新构建工具列表等
    }
}
