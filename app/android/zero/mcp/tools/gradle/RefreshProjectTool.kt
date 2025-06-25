package android.zero.mcp.tools.gradle

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.actions.ProjectSyncAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @gradle:#Refresh-project
 * 功能：调用 ProjectSyncAction 执行工程项目的刷新操作
 */
class RefreshProjectTool : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        return@withContext try {
            val action = ProjectSyncAction()
            action.perform()
            McpResponse.success(request.id, "项目刷新已触发")
        } catch (e: Exception) {
            McpServerLog.log("RefreshProjectTool error: ${e.message}")
            McpResponse.error("刷新工程失败: ${e.message}", request.id)
        }
    }
} 
