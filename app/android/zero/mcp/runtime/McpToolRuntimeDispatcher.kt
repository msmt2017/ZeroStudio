package android.zero.mcp.runtime

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.handler.ToolHelpRequestHandler
import android.zero.mcp.registry.McpToolRegistry
import android.zero.mcp.security.McpToolPermissionManager
import android.zero.mcp.policy.ToolExecutionPolicy
import kotlinx.coroutines.runBlocking

/**
 * MCP 请求执行调度器：统一解析请求是否为特殊指令或普通 Tool
 */
object McpToolRuntimeDispatcher {

    fun dispatch(request: McpRequest): McpResponse {
        return when {
            request.method == "@tool.help" -> ToolHelpRequestHandler.handle(request)

            McpToolRegistry.getRegisteredTool(request.method) != null -> {
                if (!McpToolPermissionManager.isAllowed(request.method)) {
                    return McpResponse.error("工具被禁止调用: ${request.method}", request.id)
                }
                if (!ToolExecutionPolicy.isExecutable(request.method)) {
                    return McpResponse.error("模型无权执行该工具", request.id)
                }

                val tool = McpToolRegistry.getRegisteredTool(request.method)!!
                runBlocking { tool.invoke(request) }
            }

            else -> McpResponse.error("未知请求方法: ${request.method}", request.id)
        }
    }
}
