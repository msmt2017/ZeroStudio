package android.zero.mcp.runtime

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.monitor.ToolStatsTracker

/**
 * 工具运行包装器：封装每次工具执行 + 统计计数
 */
object ToolRuntimeWrapper {

    suspend fun invoke(tool: McpTool, request: McpRequest): McpResponse {
        ToolStatsTracker.recordInvocation(request.method)
        return try {
            tool.invoke(request)
        } catch (e: Exception) {
            ToolStatsTracker.recordError(request.method)
            McpResponse.error("执行失败: ${e.message}", request.id)
        }
    }
}
