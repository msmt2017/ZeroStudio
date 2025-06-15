// File: android/zero/mcp/McpService.kt
package android.zero.mcp

import android.content.Context
import android.zero.mcp.handlers.*
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import com.itsaky.androidide.lookup.Lookup
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.builder.BuildService
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * [McpService] is the core component of the Model Context Protocol (MCP) server.
 * It manages the lifecycle of command handlers, dispatches incoming [McpCommand]s,
 * and sends [McpResponse]s back to the Ktor server for client consumption.
 *
 * This class ensures that all operations are handled asynchronously and logs are
 * consistently generated via [LogManager].
 *
 * @param applicationContext The Android application context for accessing system services and resources.
 * @author Android Zero
 */
class McpService(private val applicationContext: Context) {

    // Coroutine scope for all asynchronous operations within the MCP service.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Channel for internal communication of raw JSON responses to the Ktor server.
    private val responseChannel = Channel<String>(Channel.UNLIMITED)

    // Exposed flow for Ktor server to collect responses.
    val responses = responseChannel.receiveAsFlow()

    // Handlers for different command categories.
    private val fileHandler: FileHandler
    private val workspaceHandler: WorkspaceHandler
    private val gradleHandler: GradleHandler
    private val shellHandler: ShellHandler
    private val tabFileHandler: TabFileHandler

    // Reference to the CodeEditorProvider, set by the host application (e.g., MainActivity).
    private val codeEditorProviderRef = AtomicReference<CodeEditorProvider?>(null)

    init {
        LogManager.addLog("McpService: Initializing handlers...", "INFO", "McpService")

        // Initialize core IDE services needed by handlers using Lookup.
        // These are expected to be registered by the host application (e.g., McpApplication).
        val projectManager = Lookup.getDefault().lookup(IProjectManager::class.java)
            ?: throw IllegalStateException("IProjectManager not registered with Lookup!")
        val buildService = Lookup.getDefault().lookup(BuildService::class.java)
            ?: throw IllegalStateException("BuildService not registered with Lookup!")

        fileHandler = FileHandler(serviceScope, applicationContext)
        workspaceHandler = WorkspaceHandler(serviceScope, applicationContext, projectManager)
        gradleHandler = GradleHandler(serviceScope, applicationContext, buildService, projectManager)
        shellHandler = ShellHandler(serviceScope, applicationContext)
        tabFileHandler = TabFileHandler(serviceScope, applicationContext, codeEditorProviderRef)

        LogManager.addLog("McpService: Handlers initialized.", "INFO", "McpService")
    }

    /**
     * Sets the [CodeEditorProvider] instance. This must be called by the host application
     * (e.g., [MainActivity]) to enable [TabFileHandler] functionality.
     *
     * @param provider The implementation of [CodeEditorProvider].
     */
    fun setCodeEditorProvider(provider: CodeEditorProvider) {
        codeEditorProviderRef.set(provider)
        LogManager.addLog("McpService: CodeEditorProvider set.", "INFO", "McpService")
    }

    /**
     * Dispatches an incoming [McpCommand] to the appropriate handler.
     * All responses (including logs and errors) will be sent back through the [responseChannel].
     *
     * @param commandJson The raw JSON string of the command received from the client.
     */
    suspend fun dispatchCommand(commandJson: String) {
        LogManager.addLog("McpService: Received command: $commandJson", "DEBUG", "McpService")

        val command: McpCommand
        try {
            command = JsonAdapters.defaultJson.decodeFromString(McpCommand.serializer(), commandJson)
        } catch (e: Exception) {
            val errorId = UUID.randomUUID().toString()
            LogManager.addLog("McpService: Failed to parse command JSON: ${e.message}", "ERROR", "McpService")
            sendErrorResponse(errorId, null, "Invalid command format: ${e.message}")
            return
        }

        val handler: CommandHandler? = when {
            command.instruction.startsWith("@File:") -> fileHandler
            command.instruction.startsWith("@File:workspace:") -> workspaceHandler
            command.instruction.startsWith("@gradle:") -> gradleHandler
            command.instruction.startsWith("@task:") -> gradleHandler // Task commands are also handled by GradleHandler
            command.instruction.startsWith("@shell:") -> shellHandler
            command.instruction.startsWith("@TabFile:") -> tabFileHandler
            else -> null
        }

        if (handler == null) {
            LogManager.addLog("McpService: No handler found for instruction: ${command.instruction}", "WARN", "McpService")
            sendErrorResponse(command.id, command.contextId, "No handler found for instruction: ${command.instruction}")
            return
        }

        serviceScope.launch {
            try {
                handler.handleCommand(command, responseChannel)
            } catch (e: Exception) {
                LogManager.addLog("McpService: Error handling command ${command.instruction}: ${e.message}", "ERROR", "McpService")
                sendErrorResponse(command.id, command.contextId, "Internal server error: ${e.message}")
            }
        }
    }

    /**
     * Sends an error response back to the client.
     *
     * @param id The ID of the original command.
     * @param contextId The context ID of the original command.
     * @param errorMessage The error message to send.
     */
    private suspend fun sendErrorResponse(id: String, contextId: String?, errorMessage: String) {
        val errorResponse = McpResponse(
            id = id,
            event = "error",
            data = JsonPrimitive(errorMessage),
            success = false,
            contextId = contextId
        )
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(errorResponse))
    }

    /**
     * Destroys the MCP service, canceling all running coroutines and releasing resources.
     * This should be called when the application or service is terminating.
     */
    fun destroy() {
        LogManager.addLog("McpService: Shutting down...", "INFO", "McpService")
        serviceScope.cancel() // Cancels all coroutines launched in this scope
        responseChannel.close() // Close the channel
        LogManager.addLog("McpService: Shutdown complete.", "INFO", "McpService")
    }
}
