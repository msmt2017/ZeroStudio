// File: android/zero/mcp/handlers/FileHandler.kt
package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.LogManager
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * [FileHandler] processes MCP commands related to general file system operations.
 * It uses [FileUtil] from `com.itsaky.androidide.utils` to perform operations
 * like create, write, read, delete, move, copy, and list files/directories.
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @author Android Zero
 */
class FileHandler(
    private val scope: CoroutineScope,
    private val context: Context
) : CommandHandler {

    private val TAG = "FileHandler"

    override suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>) {
        scope.launch {
            val id = command.id
            val contextId = command.contextId
            val instructionParts = command.instruction.split(":")
            if (instructionParts.size < 2) {
                sendError(id, contextId, "Invalid file instruction format.", responseChannel)
                return@launch
            }

            val operation = instructionParts[1].removePrefix("#") // Remove '#' if present
            val path = command.args["path"]?.jsonPrimitive?.content
            val newPath = command.args["newPath"]?.jsonPrimitive?.content
            val content = command.args["content"]?.jsonPrimitive?.content
            val writeLine = command.args["writeLine"]?.jsonPrimitive?.content?.toIntOrNull()
            val folderName = command.args["folder"]?.jsonPrimitive?.content
            val fileName = command.args["files"]?.jsonPrimitive?.content

            LogManager.addLog("FileHandler: Handling operation '$operation' for path '$path'", "DEBUG", TAG)

            when (operation) {
                "create" -> {
                    if (path == null) {
                        sendError(id, contextId, "Missing 'path' argument for file.create.", responseChannel)
                        return@launch
                    }
                    val targetFile = File(path, folderName ?: fileName ?: "")
                    if (folderName != null) {
                        if (FileUtil.mkdirs(targetFile)) {
                            sendSuccess(id, contextId, "Folder created successfully: ${targetFile.absolutePath}", responseChannel)
                        } else {
                            sendError(id, contextId, "Failed to create folder: ${targetFile.absolutePath}", responseChannel)
                        }
                    } else if (fileName != null) {
                        try {
                            if (FileUtil.mkdirs(targetFile.parentFile)) { // Ensure parent dir exists
                                if (targetFile.createNewFile()) {
                                    sendSuccess(id, contextId, "File created successfully: ${targetFile.absolutePath}", responseChannel)
                                } else {
                                    sendError(id, contextId, "Failed to create file (might already exist): ${targetFile.absolutePath}", responseChannel)
                                }
                            } else {
                                sendError(id, contextId, "Failed to create parent directories for file: ${targetFile.parentFile?.absolutePath}", responseChannel)
                            }
                        } catch (e: Exception) {
                            sendError(id, contextId, "Error creating file: ${e.message}", responseChannel)
                        }
                    } else {
                        sendError(id, contextId, "Missing 'folder' or 'files' argument for file.create.", responseChannel)
                    }
                }
                "write" -> {
                    if (path == null || content == null) {
                        sendError(id, contextId, "Missing 'path' or 'content' argument for file.write.", responseChannel)
                        return@launch
                    }
                    val file = File(path)
                    if (!file.exists() || !file.isFile) {
                        sendError(id, contextId, "File does not exist or is not a file: $path", responseChannel)
                        return@launch
                    }

                    try {
                        if (writeLine != null && writeLine > 0) {
                            // Read all lines, modify the specific line, then write back
                            val lines = file.readLines().toMutableList()
                            val lineIndex = writeLine - 1 // Convert to 0-indexed
                            if (lineIndex >= 0 && lineIndex < lines.size) {
                                lines[lineIndex] = content
                                FileUtil.writeFile(file, lines.joinToString("\n"), false)
                                sendSuccess(id, contextId, "Content written to file at line $writeLine: $path", responseChannel)
                            } else {
                                sendError(id, contextId, "Invalid line number $writeLine for file: $path", responseChannel)
                            }
                        } else {
                            // Overwrite entire file
                            if (FileUtil.writeFile(file, content, false)) {
                                sendSuccess(id, contextId, "Content written to file: $path", responseChannel)
                            } else {
                                sendError(id, contextId, "Failed to write content to file: $path", responseChannel)
                            }
                        }
                    } catch (e: Exception) {
                        sendError(id, contextId, "Error writing to file: ${e.message}", responseChannel)
                    }
                }
                "info" -> {
                    if (path == null) {
                        sendError(id, contextId, "Missing 'path' argument for file.info.", responseChannel)
                        return@launch
                    }
                    val file = File(path)
                    val info = buildJsonObject {
                        put("name", file.name)
                        put("path", file.absolutePath)
                        put("isDirectory", file.isDirectory)
                        put("isFile", file.isFile)
                        put("exists", file.exists())
                        put("length", file.length())
                        put("lastModified", file.lastModified())
                    }
                    sendSuccess(id, contextId, JsonAdapters.defaultJson.encodeToString(info), responseChannel)
                }
                "delete" -> {
                    if (path == null) {
                        sendError(id, contextId, "Missing 'path' argument for file.delete.", responseChannel)
                        return@launch
                    }
                    val file = File(path)
                    if (FileUtil.deleteFile(file)) {
                        sendSuccess(id, contextId, "Successfully deleted: $path", responseChannel)
                    } else {
                        sendError(id, contextId, "Failed to delete: $path", responseChannel)
                    }
                }
                "move" -> {
                    if (path == null || newPath == null) {
                        sendError(id, contextId, "Missing 'path' or 'newPath' argument for file.move.", responseChannel)
                        return@launch
                    }
                    val srcFile = File(path)
                    val destFile = File(newPath)
                    if (FileUtil.moveFile(srcFile, destFile)) {
                        sendSuccess(id, contextId, "Moved $path to $newPath", responseChannel)
                    } else {
                        sendError(id, contextId, "Failed to move $path to $newPath", responseChannel)
                    }
                }
                "copy" -> {
                    if (path == null || newPath == null) {
                        sendError(id, contextId, "Missing 'path' or 'newPath' argument for file.copy.", responseChannel)
                        return@launch
                    }
                    val srcFile = File(path)
                    val destFile = File(newPath)
                    if (FileUtil.copyFile(srcFile, destFile)) {
                        sendSuccess(id, contextId, "Copied $path to $newPath", responseChannel)
                    } else {
                        sendError(id, contextId, "Failed to copy $path to $newPath", responseChannel)
                    }
                }
                "read" -> {
                    if (path == null) {
                        sendError(id, contextId, "Missing 'path' argument for file.read.", responseChannel)
                        return@launch
                    }
                    val file = File(path)
                    val fileContent = FileUtil.readFile(file)
                    if (fileContent != null) {
                        sendSuccess(id, contextId, fileContent, responseChannel)
                    } else {
                        sendError(id, contextId, "Failed to read file: $path", responseChannel)
                    }
                }
                "list" -> {
                    if (path == null) {
                        sendError(id, contextId, "Missing 'path' argument for file.list.", responseChannel)
                        return@launch
                    }
                    val directory = File(path)
                    if (!directory.exists() || !directory.isDirectory) {
                        sendError(id, contextId, "Directory does not exist or is not a directory: $path", responseChannel)
                        return@launch
                    }
                    val fileList = directory.listFiles()?.map {
                        buildJsonObject {
                            put("name", it.name)
                            put("type", if (it.isDirectory) "directory" else "file")
                            put("path", it.absolutePath)
                        }
                    } ?: emptyList()
                    sendSuccess(id, contextId, JsonAdapters.defaultJson.encodeToString(fileList), responseChannel)
                }
                else -> {
                    sendError(id, contextId, "Unknown file operation: $operation", responseChannel)
                }
            }
        }
    }

    /**
     * Helper to send a successful MCP response.
     */
    private suspend fun sendSuccess(id: String, contextId: String?, data: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "${id.split(":")[0].toLowerCase()}.success", JsonPrimitive(data), true, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("FileHandler: Success for $id.", "INFO", TAG)
    }

    /**
     * Helper to send an error MCP response.
     */
    private suspend fun sendError(id: String, contextId: String?, errorMessage: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "${id.split(":")[0].toLowerCase()}.error", JsonPrimitive(errorMessage), false, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("FileHandler: Error for $id: $errorMessage", "ERROR", TAG)
    }
}
