// File: android/zero/mcp/handlers/WorkspaceHandler.kt
package android.zero.mcp.handlers

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.zero.mcp.LogManager
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import androidx.core.content.FileProvider
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.utils.ApkSignVerifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * [WorkspaceHandler] processes MCP commands related to Android project workspace operations.
 * It interacts with [IProjectManager] and [ApkSignVerifier] from `com.itsaky.androidide`.
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @param projectManager The [IProjectManager] instance for accessing project information.
 * @author Android Zero
 */
class WorkspaceHandler(
    private val scope: CoroutineScope,
    private val context: Context,
    private val projectManager: IProjectManager
) : CommandHandler {

    private val TAG = "WorkspaceHandler"

    override suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>) {
        scope.launch {
            val id = command.id
            val contextId = command.contextId
            val instructionParts = command.instruction.split(":")
            if (instructionParts.size < 3) {
                sendError(id, contextId, "Invalid workspace instruction format.", responseChannel)
                return@launch
            }

            val operation = instructionParts[2].removePrefix("#")

            LogManager.addLog("WorkspaceHandler: Handling operation '$operation'", "DEBUG", TAG)

            when (operation) {
                "getinstallApk" -> {
                    val variant = command.args["variant"]?.jsonPrimitive?.content
                    if (variant == null) {
                        sendError(id, contextId, "Missing 'variant' argument for workspace.getinstallApk.", responseChannel)
                        return@launch
                    }
                    handleInstallApk(id, contextId, variant, responseChannel)
                }
                "getmoduleInfo" -> {
                    handleGetModuleInfo(id, contextId, responseChannel)
                }
                "getGradleWrapperInfo" -> {
                    handleGetGradleWrapperInfo(id, contextId, responseChannel)
                }
                else -> {
                    sendError(id, contextId, "Unknown workspace operation: $operation", responseChannel)
                }
            }
        }
    }

    /**
     * Handles the APK installation request.
     * Searches for the APK, verifies its signature, and initiates system installation.
     */
    private suspend fun handleInstallApk(id: String, contextId: String?, variant: String, responseChannel: SendChannel<String>) {
        val projectPath = projectManager.getProjectPath()
        if (projectPath == null) {
            sendError(id, contextId, "No project currently open.", responseChannel)
            return
        }

        // Construct potential APK paths
        // This is a common path for debug/release APKs in Android projects
        val apkFileName = ".apk" // Assuming 'app' module
        val apkFile = File(projectPath, "app/build/outputs/apk/$variant/$apkFileName")

        if (!apkFile.exists()) {
            sendError(id, contextId, "APK file not found at: ${apkFile.absolutePath}. Ensure project is built.", responseChannel)
            return
        }

        if (!ApkSignVerifier.isApkSigned(apkFile)) {
            sendError(id, contextId, "APK is not signed. Cannot install unsigned APK.", responseChannel)
            return
        }

        try {
            // Use FileProvider for secure URI creation (important for Android N+)
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider", // Authority must match AndroidManifest.xml
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Required for starting from service
            }

            // Start the activity to install the APK
            context.startActivity(installIntent)
            sendSuccess(id, contextId, "APK installation launched for '${apkFile.absolutePath}'.", responseChannel)
        } catch (e: Exception) {
            sendError(id, contextId, "Failed to launch APK installer: ${e.message}", responseChannel)
        }
    }

    /**
     * Handles the request to get all Android module information.
     */
    private suspend fun handleGetModuleInfo(id: String, contextId: String?, responseChannel: SendChannel<String>) {
        val appModules = projectManager.getAndroidAppModules()
        val libraryModules = projectManager.getAndroidLibraryModules()

        val allModules = appModules.map { module ->
            buildJsonObject {
                put("name", module.name)
                put("type", "application")
                put("absolutePath", module.path)
                put("relativePath", module.path.removePrefix(projectManager.getProjectPath() ?: "/"))
            }
        } + libraryModules.map { module ->
            buildJsonObject {
                put("name", module.name)
                put("type", "library")
                put("absolutePath", module.path)
                put("relativePath", module.path.removePrefix(projectManager.getProjectPath() ?: "/"))
            }
        }

        sendSuccess(id, contextId, JsonAdapters.defaultJson.encodeToString(buildJsonArray { allModules.forEach { add(it) } }), responseChannel)
    }

    /**
     * Handles the request to get the content of `gradle-wrapper.properties`.
     */
    private suspend fun handleGetGradleWrapperInfo(id: String, contextId: String?, responseChannel: SendChannel<String>) {
        val projectPath = projectManager.getProjectPath()
        if (projectPath == null) {
            sendError(id, contextId, "No project currently open.", responseChannel)
            return
        }

        val wrapperFile = File(projectPath, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapperFile.exists() || !wrapperFile.isFile) {
            sendError(id, contextId, "gradle-wrapper.properties not found at: ${wrapperFile.absolutePath}", responseChannel)
            return
        }

        try {
            val content = wrapperFile.readText()
            sendSuccess(id, contextId, content, responseChannel)
        } catch (e: Exception) {
            sendError(id, contextId, "Failed to read gradle-wrapper.properties: ${e.message}", responseChannel)
        }
    }


    /**
     * Helper to send a successful MCP response.
     */
    private suspend fun sendSuccess(id: String, contextId: String?, data: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "workspace.success", JsonPrimitive(data), true, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("WorkspaceHandler: Success for $id.", "INFO", TAG)
    }

    /**
     * Helper to send an error MCP response.
     */
    private suspend fun sendError(id: String, contextId: String?, errorMessage: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "workspace.error", JsonPrimitive(errorMessage), false, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("WorkspaceHandler: Error for $id: $errorMessage", "ERROR", TAG)
    }
}
