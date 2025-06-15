package android.zero.mcp.tools.gradle

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @gradle:#run-project
 * 功能：直接运行当前项目的构建，无需选择具体 task
 * 参考类：QuickRunWithCancellationAction
 */
class RunProjectTool(
    private val quickRunAction: () -> Boolean
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val success = quickRunAction()
            if (success) {
                McpResponse.success(request.id, "项目构建启动成功")
            } else {
                McpResponse.error("构建启动失败", request.id)
            }
        } catch (e: Exception) {
            McpServerLog.log("RunProjectTool error: ${e.message}")
            McpResponse.error("运行构建异常: ${e.message}", request.id)
        }
    }
} 
