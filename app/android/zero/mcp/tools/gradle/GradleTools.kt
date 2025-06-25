package android.zero.mcp.tools.gradle

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.projects.ProjectManager
import com.itsaky.androidide.actions.build.QuickRunWithCancellationAction
import com.itsaky.androidide.actions.build.ProjectSyncAction
import com.itsaky.androidide.actions.ActionData
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Tool: @gradle:run-project
 * 功能：调用IDE的API直接开始运行构建编译，参考QuickRunWithCancellationAction里面的代码逻辑
 * 示例：@gradle:run-project
 */
class RunProjectTool(
    private val quickRunAction: () -> Unit
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("开始执行项目构建...")
            
            // 调用QuickRunWithCancellationAction的逻辑
            quickRunAction()
            
            val result = buildString {
                appendLine("✅ 项目构建已启动")
                appendLine("构建类型: Quick Run")
                appendLine("状态: 正在构建中...")
                appendLine()
                appendLine("💡 提示:")
                appendLine("- 构建过程将在后台进行")
                appendLine("- 可以在构建输出窗口查看进度")
                appendLine("- 构建完成后会自动安装APK")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("RunProjectTool error: ${e.message}")
            McpResponse.error("项目构建失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @gradle:Refresh-project
 * 功能：同步刷新工程项目，参考ProjectSyncAction调用编写的代码逻辑
 * 示例：@gradle:Refresh-project
 */
class RefreshProjectTool() : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("开始同步项目...")
            
            val projectManager = ProjectManager.getInstance()
            val workspaceRoot = projectManager.getWorkspaceRootDir()
            
            if (workspaceRoot == null || !workspaceRoot.exists()) {
                return@withContext McpResponse.error("工作区不存在或未初始化", request.id)
            }
            
            // 执行项目同步
            val syncResult = projectManager.syncProject()
            
            val result = buildString {
                appendLine("✅ 项目同步已启动")
                appendLine("工作区: ${workspaceRoot.absolutePath}")
                appendLine("同步状态: 进行中...")
                appendLine("同步结果: $syncResult")
                appendLine()
                appendLine("💡 提示:")
                appendLine("- 项目同步将在后台进行")
                appendLine("- 同步完成后会重新加载项目结构")
                appendLine("- 可以在构建输出窗口查看同步进度")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("RefreshProjectTool error: ${e.message}")
            McpResponse.error("项目同步失败: ${e.message}", request.id)
        }
    }
} 