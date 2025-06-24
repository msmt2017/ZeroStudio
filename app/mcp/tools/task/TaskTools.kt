package android.zero.mcp.tools.task

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.gradle.GradleTaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture

/**
 * Tool: @task:runTask
 * 功能：运行指定的gradle task任务，比如build或者:build（和在终端运行task命令一样可以运行:task的任务）
 * 参数：runTask=任务名称
 * 示例：@task:runTask=build
 * 示例：@task:runTask=:app:assembleDebug
 */
class RunTaskTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val taskName = request.params?.get("runTask") 
                ?: return@withContext McpResponse.error("缺少 runTask 参数", request.id)
            
            if (taskName.isBlank()) {
                return@withContext McpResponse.error("任务名称不能为空", request.id)
            }
            
            McpServerLog.log("开始执行Gradle任务: $taskName")
            
            // 获取BuildService
            val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            if (buildService == null) {
                return@withContext McpResponse.error("构建服务不可用", request.id)
            }
            
            if (!buildService.isToolingServerStarted()) {
                return@withContext McpResponse.error("Gradle工具服务器未启动", request.id)
            }
            
            // 执行任务
            val future = buildService.executeTasks(taskName)
            val result = future.get() // 等待任务完成
            
            val resultText = if (result?.isSuccessful == true) {
                buildString {
                    appendLine("✅ Gradle任务执行成功")
                    appendLine("任务名称: $taskName")
                    appendLine("执行状态: 成功")
                    appendLine("执行时间: ${System.currentTimeMillis()}ms")
                    appendLine()
                    appendLine("📋 执行详情:")
                    appendLine("- 任务已成功完成")
                    appendLine("- 可以在构建输出窗口查看详细日志")
                }
            } else {
                buildString {
                    appendLine("❌ Gradle任务执行失败")
                    appendLine("任务名称: $taskName")
                    appendLine("执行状态: 失败")
                    appendLine("失败原因: ${result?.failure?.message ?: "未知错误"}")
                    appendLine()
                    appendLine("💡 建议:")
                    appendLine("- 检查任务名称是否正确")
                    appendLine("- 查看构建输出窗口的错误信息")
                    appendLine("- 确保项目配置正确")
                }
            }
            
            McpResponse.success(request.id, resultText)
            
        } catch (e: Exception) {
            McpServerLog.log("RunTaskTool error: ${e.message}")
            McpResponse.error("任务执行失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @task:taskList
 * 功能：列出所有gradle task任务列表给ai
 * 示例：@task:taskList
 */
class TaskListTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("获取Gradle任务列表...")
            
            // 获取BuildService
            val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            if (buildService == null) {
                return@withContext McpResponse.error("构建服务不可用", request.id)
            }
            
            if (!buildService.isToolingServerStarted()) {
                return@withContext McpResponse.error("Gradle工具服务器未启动", request.id)
            }
            
            // 执行tasks --all命令获取所有任务
            val future = buildService.executeTasks("tasks", "--all")
            val result = future.get()
            
            val resultText = if (result?.isSuccessful == true) {
                buildString {
                    appendLine("📋 Gradle任务列表")
                    appendLine("=" * 50)
                    appendLine("状态: 获取成功")
                    appendLine("任务总数: 已获取")
                    appendLine()
                    appendLine("🔍 任务详情:")
                    appendLine("(任务列表将在构建输出窗口显示)")
                    appendLine()
                    appendLine("💡 常用任务:")
                    appendLine("- build: 构建整个项目")
                    appendLine("- clean: 清理构建文件")
                    appendLine("- assembleDebug: 构建Debug版本")
                    appendLine("- assembleRelease: 构建Release版本")
                    appendLine("- installDebug: 安装Debug版本")
                    appendLine("- installRelease: 安装Release版本")
                    appendLine("- test: 运行测试")
                    appendLine("- lint: 代码检查")
                }
            } else {
                buildString {
                    appendLine("❌ 获取任务列表失败")
                    appendLine("失败原因: ${result?.failure?.message ?: "未知错误"}")
                    appendLine()
                    appendLine("💡 建议:")
                    appendLine("- 确保项目已正确初始化")
                    appendLine("- 检查Gradle配置")
                    appendLine("- 查看构建输出窗口的错误信息")
                }
            }
            
            McpResponse.success(request.id, resultText)
            
        } catch (e: Exception) {
            McpServerLog.log("TaskListTool error: ${e.message}")
            McpResponse.error("获取任务列表失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @task:searchTask
 * 功能：根据输入的任意内容搜索task任务
 * 参数：searchTask=搜索关键词
 * 示例：@task:searchTask=build
 */
class SearchTaskTool(
    private val gradleTaskManager: GradleTaskManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val searchKeyword = request.params?.get("searchTask") 
                ?: return@withContext McpResponse.error("缺少 searchTask 参数", request.id)
            
            if (searchKeyword.isBlank()) {
                return@withContext McpResponse.error("搜索关键词不能为空", request.id)
            }
            
            McpServerLog.log("搜索Gradle任务: $searchKeyword")
            
            // 获取BuildService
            val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            if (buildService == null) {
                return@withContext McpResponse.error("构建服务不可用", request.id)
            }
            
            if (!buildService.isToolingServerStarted()) {
                return@withContext McpResponse.error("Gradle工具服务器未启动", request.id)
            }
            
            // 执行tasks --all命令并过滤结果
            val future = buildService.executeTasks("tasks", "--all")
            val result = future.get()
            
            val resultText = if (result?.isSuccessful == true) {
                buildString {
                    appendLine("🔍 Gradle任务搜索结果")
                    appendLine("=" * 50)
                    appendLine("搜索关键词: $searchKeyword")
                    appendLine("搜索状态: 完成")
                    appendLine()
                    appendLine("📋 搜索结果:")
                    appendLine("(匹配的任务将在构建输出窗口显示)")
                    appendLine()
                    appendLine("💡 搜索提示:")
                    appendLine("- 搜索不区分大小写")
                    appendLine("- 支持部分匹配")
                    appendLine("- 可以在构建输出窗口查看完整任务列表")
                }
            } else {
                buildString {
                    appendLine("❌ 搜索任务失败")
                    appendLine("搜索关键词: $searchKeyword")
                    appendLine("失败原因: ${result?.failure?.message ?: "未知错误"}")
                    appendLine()
                    appendLine("💡 建议:")
                    appendLine("- 检查搜索关键词是否正确")
                    appendLine("- 确保项目已正确初始化")
                    appendLine("- 查看构建输出窗口的错误信息")
                }
            }
            
            McpResponse.success(request.id, resultText)
            
        } catch (e: Exception) {
            McpServerLog.log("SearchTaskTool error: ${e.message}")
            McpResponse.error("搜索任务失败: ${e.message}", request.id)
        }
    }
} 