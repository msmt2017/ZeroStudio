// File: android/zero/mcp/handlers/GradleHandler.kt
package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.LogManager
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/**
 * [GradleHandler] processes MCP commands related to Gradle operations and tasks.
 * It interacts with the IDE's [BuildService] and [IProjectManager].
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @param buildService The [BuildService] instance for executing Gradle tasks.
 * @param projectManager The [IProjectManager] instance for accessing project information.
 * @author Android Zero
 */
class GradleHandler(
    private val scope: CoroutineScope,
    private val context: Context,
    private val buildService: BuildService,
    private val projectManager: IProjectManager
) : CommandHandler {

    private val TAG = "GradleHandler"

    override suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>) {
        scope.launch {
            val id = command.id
            val contextId = command.contextId
            val instructionParts = command.instruction.split(":")
            if (instructionParts.size < 2) {
                sendError(id, contextId, "Invalid Gradle instruction format.", responseChannel)
                return@launch
            }

            val operation = instructionParts.last().removePrefix("#") // Get the last part after ':' and remove '#'

            LogManager.addLog("GradleHandler: Handling operation '$operation'", "DEBUG", TAG)

            when (operation) {
                "run-project" -> {
                    handleRunProject(id, contextId, responseChannel)
                }
                "Refresh-project" -> {
                    handleRefreshProject(id, contextId, responseChannel)
                }
                "modifyversion" -> {
                    val projectPath = command.args["projectPath"]?.jsonPrimitive?.content
                    val newVersion = command.args["newVersion"]?.jsonPrimitive?.content
                    if (projectPath == null || newVersion == null) {
                        sendError(id, contextId, "Missing 'projectPath' or 'newVersion' for gradle.modifyversion.", responseChannel)
                        return@launch
                    }
                    handleModifyGradleVersion(id, contextId, projectPath, newVersion, responseChannel)
                }
                "runTask" -> {
                    val taskName = command.args["taskName"]?.jsonPrimitive?.content
                    if (taskName == null) {
                        sendError(id, contextId, "Missing 'taskName' for task.runTask.", responseChannel)
                        return@launch
                    }
                    handleRunTask(id, contextId, taskName, responseChannel)
                }
                "taskList", "searchTask" -> { // searchTask is an alias for taskList with search term
                    val searchTerm = command.args["searchTerm"]?.jsonPrimitive?.content
                    handleTaskList(id, contextId, searchTerm, responseChannel)
                }
                else -> {
                    sendError(id, contextId, "Unknown Gradle/Task operation: $operation", responseChannel)
                }
            }
        }
    }

    /**
     * Handles the "run-project" command, initiating a default build/run.
     * This usually translates to `:app:assembleDebug` or similar.
     */
    private suspend fun handleRunProject(id: String, contextId: String?, responseChannel: SendChannel<String>) {
        if (buildService.isBuildInProgress()) {
            sendError(id, contextId, "A build is already in progress.", responseChannel)
            return
        }
        val appModules = projectManager.getAndroidAppModules()
        val defaultModule = appModules.firstOrNull()

        if (defaultModule == null) {
            sendError(id, contextId, "No Android application module found to run.", responseChannel)
            return
        }

        val defaultTask = "${defaultModule.path.removePrefix(":")}:assembleDebug" // Default build task
        LogManager.addLog("GradleHandler: Running default project task: $defaultTask", "INFO", TAG)

        try {
            // Note: The mock BuildService.executeTasks directly returns a completed future.
            // In a real scenario, you'd await the actual build completion.
            val result = withContext(Dispatchers.IO) {
                buildService.executeTasks(defaultTask).get()
            }

            if (result.isSuccessful) {
                sendSuccess(id, contextId, "Project run successful for task: $defaultTask", responseChannel)
            } else {
                sendError(id, contextId, "Project run failed: ${result.failureReason?.message}", responseChannel)
            }
        } catch (e: Exception) {
            sendError(id, contextId, "Error running project: ${e.message}", responseChannel)
        }
    }

    /**
     * Handles the "Refresh-project" command, triggering a Gradle sync.
     */
    private suspend fun handleRefreshProject(id: String, contextId: String?, responseChannel: SendChannel<String>) {
        val projectPath = projectManager.getProjectPath()
        if (projectPath == null) {
            sendError(id, contextId, "No project currently open to refresh.", responseChannel)
            return
        }
        LogManager.addLog("GradleHandler: Refreshing project: $projectPath", "INFO", TAG)
        try {
            // The ProjectManager.loadProject is used for refresh/sync logic in the mock.
            // In a real IDE, this might call a specific sync method on BuildService or ProjectManager.
            val result = withContext(Dispatchers.IO) {
                projectManager.loadProject(projectPath).get()
            }
            if (result.isSuccessful) {
                sendSuccess(id, contextId, "Project refresh successful.", responseChannel)
            } else {
                sendError(id, contextId, "Project refresh failed: ${result.failureReason?.message}", responseChannel)
            }
        } catch (e: Exception) {
            sendError(id, contextId, "Error refreshing project: ${e.message}", responseChannel)
        }
    }

    /**
     * Handles the "modifyversion" command to update Gradle Wrapper version.
     */
    private suspend fun handleModifyGradleVersion(id: String, contextId: String?, projectPath: String, newVersion: String, responseChannel: SendChannel<String>) {
        val wrapperFile = File(projectPath, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperFile.exists()) {
            sendError(id, contextId, "gradle-wrapper.properties not found at: ${wrapperFile.absolutePath}", responseChannel)
            return
        }

        try {
            var content = FileUtil.readFile(wrapperFile) ?: ""
            val regex = Regex("""(distributionUrl=.*gradle-)(\d+\.\d+)(-.+\.zip)""")
            val match = regex.find(content)

            if (match != null) {
                val (prefix, oldVersion, suffix) = match.destructured
                val updatedContent = content.replaceFirst(regex, "${prefix}${newVersion}${suffix}")

                if (FileUtil.writeFile(wrapperFile, updatedContent, false)) {
                    sendSuccess(id, contextId, "Gradle version updated from $oldVersion to $newVersion in gradle-wrapper.properties", responseChannel)
                } else {
                    sendError(id, contextId, "Failed to write updated content to gradle-wrapper.properties.", responseChannel)
                }
            } else {
                sendError(id, contextId, "Could not find 'distributionUrl' with version pattern in gradle-wrapper.properties. Manual update might be needed.", responseChannel)
            }
        } catch (e: Exception) {
            sendError(id, contextId, "Error modifying gradle-wrapper.properties: ${e.message}", responseChannel)
        }
    }


    /**
     * Handles the "runTask" command, executing a specific Gradle task.
     */
    private suspend fun handleRunTask(id: String, contextId: String?, taskName: String, responseChannel: SendChannel<String>) {
        if (buildService.isBuildInProgress()) {
            sendError(id, contextId, "A build is already in progress.", responseChannel)
            return
        }
        LogManager.addLog("GradleHandler: Running custom Gradle task: $taskName", "INFO", TAG)
        try {
            val result = withContext(Dispatchers.IO) {
                buildService.executeTasks(taskName).get()
            }
            if (result.isSuccessful) {
                sendSuccess(id, contextId, "Gradle task '$taskName' executed successfully.", responseChannel)
            } else {
                sendError(id, contextId, "Gradle task '$taskName' failed: ${result.failureReason?.message}", responseChannel)
            }
        } catch (e: Exception) {
            sendError(id, contextId, "Error running Gradle task '$taskName': ${e.message}", responseChannel)
        }
    }

    /**
     * Handles "taskList" and "searchTask" commands.
     */
    private suspend fun handleTaskList(id: String, contextId: String?, searchTerm: String?, responseChannel: SendChannel<String>) {
        val appModules = projectManager.getAndroidAppModules()
        val libraryModules = projectManager.getAndroidLibraryModules()

        val allTasks = mutableSetOf<String>()
        appModules.forEach { module ->
            module.gradleModule?.getTasks()?.forEach { task ->
                allTasks.add("${module.path}:${task.name}") // Add full path task
                allTasks.add(task.name) // Add short task name
            }
        }
        libraryModules.forEach { module ->
            module.gradleModule?.getTasks()?.forEach { task ->
                allTasks.add("${module.path}:${task.name}") // Add full path task
                allTasks.add(task.name) // Add short task name
            }
        }

        val filteredTasks = if (searchTerm.isNullOrBlank()) {
            allTasks.sorted()
        } else {
            allTasks.filter { it.contains(searchTerm, ignoreCase = true) }.sorted()
        }

        if (filteredTasks.isNotEmpty()) {
            sendSuccess(id, contextId, filteredTasks.joinToString("\n"), responseChannel)
        } else {
            sendError(id, contextId, "No Gradle tasks found matching '$searchTerm'.", responseChannel)
        }
    }

    /**
     * Helper to send a successful MCP response.
     */
    private suspend fun sendSuccess(id: String, contextId: String?, data: String, responseChannel: SendChannel<String>) {
        val event = when {
            id.startsWith("gradle") -> "gradle.success"
            id.startsWith("task") -> "task.success"
            else -> "success"
        }
        val response = McpResponse(id, event, JsonPrimitive(data), true, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("GradleHandler: Success for $id.", "INFO", TAG)
    }

    /**
     * Helper to send an error MCP response.
     */
    private suspend fun sendError(id: String, contextId: String?, errorMessage: String, responseChannel: SendChannel<String>) {
        val event = when {
            id.startsWith("gradle") -> "gradle.error"
            id.startsWith("task") -> "task.error"
            else -> "error"
        }
        val response = McpResponse(id, event, JsonPrimitive(errorMessage), false, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("GradleHandler: Error for $id: $errorMessage", "ERROR", TAG)
    }
}
