package android.zero.mcp

import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.tooling.api.models.GradleTask
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.lookup.Lookup
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.zero.mcp.McpResponse
import java.util.UUID

class GradleHandler {
    private val json = Json { encodeDefaults = true }
    private val log = ILogger.newInstance("GradleHandler")

    @Suppress("UNCHECKED_CAST")
    suspend fun handleListTasks(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        try {
            val projectManager = IProjectManager.getInstance()
            val rootProject = projectManager.rootProject
            if (rootProject == null) {
                sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.list.error", "", "Current project not initialized"))
                sendChannel.close()
                return
            }

            val allTasks = rootProject.subProjects
                .flatMap { it.tasks }
                .map { it.path }
            val data = json.encodeToString(allTasks)
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.list.result", data))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to list tasks"
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.list.error", "", errMsg))
        } finally {
            sendChannel.close()
        }
    }

    suspend fun handleExecuteTask(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val tasksArg = args["tasks"] ?: ""
        val taskPaths = tasksArg.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (taskPaths.isEmpty()) {
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.execute.error", "", "No tasks specified"))
            sendChannel.close()
            return
        }

        try {
            val buildService = Lookup.getDefault().lookup(BuildService.KEY_BUILD_SERVICE)
            if (buildService == null) {
                sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.execute.error", "", "BuildService unavailable"))
                sendChannel.close()
                return
            }

            val startedMsg = "Tasks started: ${taskPaths.joinToString(", ")}"
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.execute.started", startedMsg))

            buildService.executeTasks(*taskPaths.toTypedArray())
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.execute.complete", "{\"isSuccessful\":true}"))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to execute tasks"
            sendChannel.send(McpResponse(UUID.randomUUID().toString(), "task.execute.error", "", errMsg))
        } finally {
            sendChannel.close()
        }
    }
}