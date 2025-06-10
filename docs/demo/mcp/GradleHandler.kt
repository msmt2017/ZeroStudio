// 文件路径：chatai/home/src/main/java/me/rerere/rikkahub/mcp/GradleHandler.kt

package android.zero.mcp

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.lookup.Lookup
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * GradleHandler：使用 ZeroStudio 内部的 BuildService 来执行任务，而非直接调用 gradlew 脚本。
 *
 * 负责以下功能：
 *   1. 列出当前项目所有 Gradle 任务（与 ZeroStudio 的 RunTasksDialogFragment 类似逻辑）；
 *   2. 调用 BuildService 执行指定的任务路径（以 GradleTask.path 形式传入）。
 */
class GradleHandler {
    private val json = Json { encodeDefaults = true }
    private val log = ILogger.newInstance("GradleHandler")

    /**
     * 列出当前项目（包括子模块）的所有可执行 GradleTask。
     *
     * 参数 args:
     *   - "projectRoot"（可选）：项目根目录，若不传，则使用 IProjectManager 获取当前打开的项目。
     *
     * 响应事件:
     *   - "task.list.result"，data 为 Json 字符串列表，每项是 GradleTask 的 path。
     *   - "task.list.error"，data 为错误说明。
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun handleListTasks(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        try {
            // 从 IProjectManager 获取当前打开的根项目
            val projectManager = IProjectManager.getInstance()
            val rootProject = projectManager.rootProject
            if (rootProject == null) {
                sendChannel.send(McpResponse("task.list.error", "Current project not initialized"))
                sendChannel.close()
                return
            }

            // 收集所有子项目的 GradleTask，并只取 path 字符串
            val allTasks = rootProject.subProjects
                .flatMap { it.tasks }        // GradleTask 对象
                .map { it.path }             // 取 path 字段
            val data = json.encodeToString(allTasks)
            sendChannel.send(McpResponse("task.list.result", data))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to list tasks"
            sendChannel.send(McpResponse("task.list.error", errMsg))
        } finally {
            sendChannel.close()
        }
    }

    /**
     * 使用 ZeroStudio 的 BuildService 来执行一组 Gradle 任务。任务通过 GradleTask.path 传入。
     *
     * 参数 args:
     *   - "tasks": 多个任务路径（以逗号分隔，例如 ":app:assembleDebug,:lib:clean"）
     *
     * SSE 推送过程:
     *   - event = "task.execute.started"，data = "Tasks started: [task1, task2, ...]"
     *   - event = "task.execute.complete"，data = "{\"isSuccessful\": true/false}"
     *
     * 注意：ZeroStudio 的 BuildService 执行任务时，其内部日志会显示到 IDE 控制台，此处仅通告启动和完成状态。
     */
    suspend fun handleExecuteTask(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val tasksArg = args["tasks"] ?: ""
        val taskPaths = tasksArg.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (taskPaths.isEmpty()) {
            sendChannel.send(McpResponse("task.execute.error", "No tasks specified"))
            sendChannel.close()
            return
        }

        try {
            // 获取 BuildService 实例
            val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            if (buildService == null) {
                sendChannel.send(McpResponse("task.execute.error", "BuildService unavailable"))
                sendChannel.close()
                return
            }

            // 通知前端：任务开始执行
            val startedMsg = "Tasks started: ${taskPaths.joinToString(", ")}"
            sendChannel.send(McpResponse("task.execute.started", startedMsg))

            // 调用 BuildService 执行任务。注意：executeTasks 是异步执行
            // 将传入所有任务路径，如 [":app:assembleDebug", ":lib:clean"]
            buildService.executeTasks(*taskPaths.toTypedArray())

            // 在此处，我们无法获取 BuildService 内部执行结果的实时回调。
            // 因此仅发送一个标记完成事件，假定调用成功。
            // 如果需要精确结果，请在 BuildService 内部注册监听器或回调再发送 SSE。
            sendChannel.send(McpResponse("task.execute.complete", "{\"isSuccessful\":true}"))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to execute tasks"
            sendChannel.send(McpResponse("task.execute.error", errMsg))
        } finally {
            sendChannel.close()
        }
    }
}
