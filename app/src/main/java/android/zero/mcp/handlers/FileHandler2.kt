// File: android/zero/mcp/handlers/FileHandler.kt
package android.zero.mcp.handlers

import android.zero.mcp.LogManager
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import android.zero.mcp.protocol.errorResponse
import android.zero.mcp.protocol.successResponse
import kotlinx.coroutines.channels.SendChannel
import java.io.File
import java.io.IOException

/**
 * Handles file-related commands for the MCP server.
 * This class provides methods for creating, reading, writing, deleting, moving, copying,
 * and listing files and directories.
 * All operations are designed to be robust with error handling and logging.
 */
class FileHandler2 {

    private val TAG = "FileHandler"

    /**
     * Dispatches the incoming file command to the appropriate handler method.
     *
     * @param command The [McpCommand] object containing the file operation details.
     * @param responseChannel The channel to send back asynchronous responses or logs.
     * @return An [McpResponse] indicating the initial outcome of the command dispatch.
     */
    suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<McpResponse>): McpResponse {
        LogManager.addLog("Handling file command: ${command.command}", "INFO", TAG)
        val path = command.args["path"]
        val content = command.args["content"]
        val newPath = command.args["newPath"]
        val writeLine = command.args["writeLine"]?.toIntOrNull()
        val folderName = command.args["folder"]
        val fileName = command.args["files"]

        return try {
            when (command.command) {
                "file.create" -> {
                    when {
                        path != null && folderName != null -> createFileOrFolder(path, folderName, true) // Create folder
                        path != null && fileName != null -> createFileOrFolder(path, fileName, false) // Create file
                        else -> command.id.errorResponse("file.create.error", "Missing 'path' and 'folder'/'files' arguments.", command.contextId)
                    }
                }
                "file.write" -> {
                    if (path != null && content != null) {
                        writeFile(path, content, writeLine)
                    } else {
                        command.id.errorResponse("file.write.error", "Missing 'path' or 'content' arguments.", command.contextId)
                    }
                }
                "file.info" -> {
                    if (path != null) {
                        getFileInfo(path)
                    } else {
                        command.id.errorResponse("file.info.error", "Missing 'path' argument.", command.contextId)
                    }
                }
                "file.delete" -> {
                    if (path != null) {
                        deleteFileOrFolder(path)
                    } else {
                        command.id.errorResponse("file.delete.error", "Missing 'path' argument.", command.contextId)
                    }
                }
                "file.move" -> {
                    if (path != null && newPath != null) {
                        moveFileOrFolder(path, newPath)
                    } else {
                        command.id.errorResponse("file.move.error", "Missing 'path' or 'newPath' arguments.", command.contextId)
                    }
                }
                "file.copy" -> {
                    if (path != null && newPath != null) {
                        copyFileOrFolder(path, newPath)
                    } else {
                        command.id.errorResponse("file.copy.error", "Missing 'path' or 'newPath' arguments.", command.contextId)
                    }
                }
                "file.read" -> {
                    if (path != null) {
                        readFile(path)
                    } else {
                        command.id.errorResponse("file.read.error", "Missing 'path' argument.", command.contextId)
                    }
                }
                "file.list" -> {
                    if (path != null) {
                        listFiles(path)
                    } else {
                        command.id.errorResponse("file.list.error", "Missing 'path' argument.", command.contextId)
                    }
                }
                else -> {
                    val errorMessage = "Unsupported file command: ${command.command}"
                    LogManager.addLog(errorMessage, "WARN", TAG)
                    command.id.errorResponse("file.unsupported", errorMessage, command.contextId)
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Error processing file command '${command.command}': ${e.message}"
            LogManager.addLog(errorMessage, "ERROR", TAG)
            command.id.errorResponse("file.exception", errorMessage, command.contextId)
        }
    }

    /**
     * Creates a new file or folder at the specified path.
     *
     * @param parentPath The parent directory where the file/folder will be created.
     * @param name The name of the file or folder to create.
     * @param isFolder True to create a folder, false to create a file.
     * @return A success or error response.
     */
    private fun createFileOrFolder(parentPath: String, name: String, isFolder: Boolean): McpResponse {
        val parentFile = File(parentPath)
        if (!parentFile.exists()) {
            val created = parentFile.mkdirs()
            if (!created) {
                LogManager.addLog("Failed to create parent directories for $parentPath", "ERROR", TAG)
                return "file.create.error".errorResponse("file.create.error", "Failed to create parent directories for $parentPath")
            }
        }

        val targetFile = File(parentFile, name)
        return if (isFolder) {
            if (targetFile.exists()) {
                LogManager.addLog("Folder already exists: ${targetFile.absolutePath}", "WARN", TAG)
                "file.create.success".successResponse("file.create.success", "Folder already exists: ${targetFile.absolutePath}")
            } else {
                val created = targetFile.mkdirs()
                if (created) {
                    LogManager.addLog("Folder created: ${targetFile.absolutePath}", "INFO", TAG)
                    "file.create.success".successResponse("file.create.success", "Folder created successfully: ${targetFile.absolutePath}")
                } else {
                    LogManager.addLog("Failed to create folder: ${targetFile.absolutePath}", "ERROR", TAG)
                    "file.create.error".errorResponse("file.create.error", "Failed to create folder: ${targetFile.absolutePath}")
                }
            }
        } else {
            if (targetFile.exists()) {
                LogManager.addLog("File already exists: ${targetFile.absolutePath}", "WARN", TAG)
                "file.create.success".successResponse("file.create.success", "File already exists: ${targetFile.absolutePath}")
            } else {
                val created = targetFile.createNewFile()
                if (created) {
                    LogManager.addLog("File created: ${targetFile.absolutePath}", "INFO", TAG)
                    "file.create.success".successResponse("file.create.success", "File created successfully: ${targetFile.absolutePath}")
                } else {
                    LogManager.addLog("Failed to create file: ${targetFile.absolutePath}", "ERROR", TAG)
                    "file.create.error".errorResponse("file.create.error", "Failed to create file: ${targetFile.absolutePath}")
                }
            }
        }
    }

    /**
     * Writes content to a specified file.
     * If writeLine is specified, it inserts content at that line. Otherwise, it overwrites the file.
     *
     * @param path The path to the target file.
     * @param content The content to write.
     * @param writeLine Optional line number to insert content.
     * @return A success or error response.
     */
    private fun writeFile(path: String, content: String, writeLine: Int?): McpResponse {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            LogManager.addLog("File not found or is not a file: $path", "ERROR", TAG)
            return "file.write.error".errorResponse("file.write.error", "File not found or is not a file: $path")
        }

        return try {
            if (writeLine != null && writeLine > 0) {
                val lines = file.readLines().toMutableList()
                val insertIndex = (writeLine - 1).coerceAtMost(lines.size) // 0-indexed, handle out of bounds
                lines.add(insertIndex, content)
                file.writeText(lines.joinToString("\n"))
                LogManager.addLog("Content inserted at line $writeLine in file: $path", "INFO", TAG)
                "file.write.success".successResponse("file.write.success", "Content inserted at line $writeLine in file: $path")
            } else {
                file.writeText(content)
                LogManager.addLog("Content written to file: $path", "INFO", TAG)
                "file.write.success".successResponse("file.write.success", "Content written to file: $path")
            }
        } catch (e: IOException) {
            LogManager.addLog("Failed to write to file $path: ${e.message}", "ERROR", TAG)
            "file.write.error".errorResponse("file.write.error", "Failed to write to file $path: ${e.message}")
        }
    }

    /**
     * Gets information about a file or folder.
     *
     * @param path The path to the file or folder.
     * @return A success response with file info in JSON or an error response.
     */
    private fun getFileInfo(path: String): McpResponse {
        val file = File(path)
        if (!file.exists()) {
            LogManager.addLog("File or folder not found: $path", "ERROR", TAG)
            return "file.info.error".errorResponse("file.info.error", "File or folder not found: $path")
        }

        val info = mapOf(
            "name" to file.name,
            "path" to file.absolutePath,
            "isDirectory" to file.isDirectory.toString(),
            "isFile" to file.isFile.toString(),
            "exists" to file.exists().toString(),
            "length" to file.length().toString(), // Size in bytes
            "lastModified" to file.lastModified().toString() // Unix timestamp
        )
        val infoJson = info.entries.joinToString(separator = ", ") { (key, value) -> "\"$key\": \"$value\"" }.let { "{ $it }" }
        LogManager.addLog("Retrieved info for $path: $infoJson", "INFO", TAG)
        return "file.info.success".successResponse("file.info.success", infoJson)
    }

    /**
     * Deletes a file or folder. If it's a folder, it deletes its contents recursively.
     *
     * @param path The path to the file or folder to delete.
     * @return A success or error response.
     */
    private fun deleteFileOrFolder(path: String): McpResponse {
        val file = File(path)
        if (!file.exists()) {
            LogManager.addLog("File or folder not found for deletion: $path", "WARN", TAG)
            return "file.delete.success".successResponse("file.delete.success", "File or folder not found: $path (no action taken)")
        }

        return try {
            val deleted = file.deleteRecursively()
            if (deleted) {
                LogManager.addLog("Deleted file or folder: $path", "INFO", TAG)
                "file.delete.success".successResponse("file.delete.success", "Successfully deleted: $path")
            } else {
                LogManager.addLog("Failed to delete file or folder: $path", "ERROR", TAG)
                "file.delete.error".errorResponse("file.delete.error", "Failed to delete: $path")
            }
        } catch (e: Exception) {
            LogManager.addLog("Error deleting file or folder $path: ${e.message}", "ERROR", TAG)
            "file.delete.error".errorResponse("file.delete.error", "Error deleting $path: ${e.message}")
        }
    }

    /**
     * Moves a file or folder from source to destination.
     *
     * @param sourcePath The current path of the file or folder.
     * @param destinationPath The new path for the file or folder.
     * @return A success or error response.
     */
    private fun moveFileOrFolder(sourcePath: String, destinationPath: String): McpResponse {
        val sourceFile = File(sourcePath)
        val destinationFile = File(destinationPath)

        if (!sourceFile.exists()) {
            LogManager.addLog("Source file or folder not found for move: $sourcePath", "ERROR", TAG)
            return "file.move.error".errorResponse("file.move.error", "Source not found: $sourcePath")
        }

        return try {
            sourceFile.renameTo(destinationFile)
            LogManager.addLog("Moved $sourcePath to $destinationPath", "INFO", TAG)
            "file.move.success".successResponse("file.move.success", "Moved $sourcePath to $destinationPath")
        } catch (e: SecurityException) {
            LogManager.addLog("Permission denied to move $sourcePath: ${e.message}", "ERROR", TAG)
            "file.move.error".errorResponse("file.move.error", "Permission denied: ${e.message}")
        } catch (e: IOException) {
            LogManager.addLog("Failed to move $sourcePath to $destinationPath: ${e.message}", "ERROR", TAG)
            "file.move.error".errorResponse("file.move.error", "Failed to move: ${e.message}")
        }
    }

    /**
     * Copies a file or folder from source to destination.
     *
     * @param sourcePath The current path of the file or folder.
     * @param destinationPath The new path for the copied file or folder.
     * @return A success or error response.
     */
    private fun copyFileOrFolder(sourcePath: String, destinationPath: String): McpResponse {
        val sourceFile = File(sourcePath)
        val destinationFile = File(destinationPath)

        if (!sourceFile.exists()) {
            LogManager.addLog("Source file or folder not found for copy: $sourcePath", "ERROR", TAG)
            return "file.copy.error".errorResponse("file.copy.error", "Source not found: $sourcePath")
        }

        return try {
            sourceFile.copyRecursively(destinationFile, overwrite = true)
            LogManager.addLog("Copied $sourcePath to $destinationPath", "INFO", TAG)
            "file.copy.success".successResponse("file.copy.success", "Copied $sourcePath to $destinationPath")
        } catch (e: IOException) {
            LogManager.addLog("Failed to copy $sourcePath to $destinationPath: ${e.message}", "ERROR", TAG)
            "file.copy.error".errorResponse("file.copy.error", "Failed to copy: ${e.message}")
        } catch (e: Exception) {
            LogManager.addLog("Error copying $sourcePath to $destinationPath: ${e.message}", "ERROR", TAG)
            "file.copy.error".errorResponse("file.copy.error", "Error copying: ${e.message}")
        }
    }

    /**
     * Reads the content of a file.
     *
     * @param path The path to the file.
     * @return A success response with file content or an error response.
     */
    private fun readFile(path: String): McpResponse {
        val file = File(path)
        if (!file.exists() || !file.isFile) {
            LogManager.addLog("File not found or is not a file for reading: $path", "ERROR", TAG)
            return "file.read.error".errorResponse("file.read.error", "File not found or is not a file: $path")
        }

        return try {
            val content = file.readText()
            LogManager.addLog("Read content from file: $path", "INFO", TAG)
            "file.read.success".successResponse("file.read.success", content)
        } catch (e: IOException) {
            LogManager.addLog("Failed to read file $path: ${e.message}", "ERROR", TAG)
            "file.read.error".errorResponse("file.read.error", "Failed to read file $path: ${e.message}")
        }
    }

    /**
     * Lists the contents of a directory.
     *
     * @param path The path to the directory.
     * @return A success response with a JSON array of file/folder names or an error response.
     */
    private fun listFiles(path: String): McpResponse {
        val directory = File(path)
        if (!directory.exists() || !directory.isDirectory) {
            LogManager.addLog("Directory not found or is not a directory: $path", "ERROR", TAG)
            return "file.list.error".errorResponse("file.list.error", "Directory not found or is not a directory: $path")
        }

        return try {
            val files = directory.listFiles()
            val fileList = files?.map {
                val type = if (it.isDirectory) "directory" else "file"
                "{ \"name\": \"${it.name}\", \"type\": \"$type\" }"
            }?.joinToString(separator = ", ") ?: ""

            val jsonOutput = "[ $fileList ]"
            LogManager.addLog("Listed contents of directory: $path", "INFO", TAG)
            "file.list.success".successResponse("file.list.success", jsonOutput)
        } catch (e: SecurityException) {
            LogManager.addLog("Permission denied to list directory $path: ${e.message}", "ERROR", TAG)
            "file.list.error".errorResponse("file.list.error", "Permission denied: ${e.message}")
        } catch (e: Exception) {
            LogManager.addLog("Error listing directory $path: ${e.message}", "ERROR", TAG)
            "file.list.error".errorResponse("file.list.error", "Error listing directory: ${e.message}")
        }
    }
}
