package android.zero.mcp.tools.task

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.gradle.GradleTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @task:#runTask=xxx
 * 功能：运行指定 Gradle 任务
 */
class RunTaskTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val taskName = request.params?.get("runTask")?.trim()
            ?: return@withContext McpResponse.error("缺少 runTask 参数", request.id)

        return@withContext try {
            gradleTaskManager.runTask(taskName)
            McpResponse.success(request.id, "任务已启动: $taskName")
        } catch (e: Exception) {
            McpServerLog.log("RunTaskTool error: ${e.message}")
            McpResponse.error("运行任务失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @task:#taskList
 * 功能：列出当前所有可用 Gradle task
 */
class TaskListTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        return@withContext try {
            val tasks = gradleTaskManager.getAllTasks()
            McpResponse.success(request.id, tasks.joinToString("\n"))
        } catch (e: Exception) {
            McpServerLog.log("TaskListTool error: ${e.message}")
            McpResponse.error("获取任务列表失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @task:#searchTask=xxx
 * 功能：搜索 Gradle task 名称模糊匹配
 */
class SearchTaskTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val keyword = request.params?.get("searchTask")?.trim()
            ?: return@withContext McpResponse.error("缺少 searchTask 参数", request.id)

        return@withContext try {
            val tasks = gradleTaskManager.getAllTasks()
            val filtered = tasks.filter { it.contains(keyword, ignoreCase = true) }
            McpResponse.success(request.id, filtered.joinToString("\n"))
        } catch (e: Exception) {
            McpServerLog.log("SearchTaskTool error: ${e.message}")
            McpResponse.error("搜索任务失败: ${e.message}", request.id)
        }
    }
}
