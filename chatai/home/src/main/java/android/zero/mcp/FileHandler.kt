package android.zero.mcp

import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.zero.mcp.McpResponse
import java.util.UUID
import java.io.File
import com.itsaky.androidide.projects.ProjectManagerImpl
import com.itsaky.androidide.projects.IProjectManager

class FileHandler {
    private val json = Json { encodeDefaults = true }

    suspend fun handleSearchByName(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val keyword = args["keyword"] ?: ""
        val projectRoot = File(args["projectRoot"] ?: ".")
        val results = mutableListOf<String>()
        if (projectRoot.exists() && projectRoot.isDirectory) {
            projectRoot.walkTopDown().forEach { file ->
                if (file.name.contains(keyword, ignoreCase = true)) {
                    results.add(file.absolutePath)
                }
            }
        }
        val data = json.encodeToString(results)
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.searchName.result", data))
    }

    suspend fun handleSearchByContent(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val content = args["content"] ?: ""
        val projectRoot = File(args["projectRoot"] ?: ".")
        val results = mutableListOf<String>()
        if (projectRoot.exists() && projectRoot.isDirectory) {
            projectRoot.walkTopDown().forEach { file ->
                if (file.isFile) {
                    try {
                        val text = file.readText()
                        if (text.contains(content, ignoreCase = true)) {
                            results.add(file.absolutePath)
                        }
                    } catch (_: Exception) {
                        // Ignore files that cannot be read
                    }
                }
            }
        }
        val data = json.encodeToString(results)
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.searchContent.result", data))
    }

    private fun getProjectModules(): List<String> {
        val projectManager = ProjectManagerImpl.getInstance()
        return projectManager.rootProject?.subProjects?.map { it.path.substring(1) } ?: emptyList()
    }

    private fun getModulePath(moduleName: String): String {
        val projectManager = ProjectManagerImpl.getInstance()
        return projectManager.rootProject?.subProjects?.find { it.path == ":$moduleName" }?.projectDir?.absolutePath ?: ""
    }

    suspend fun handleListIncludes(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val modules = getProjectModules()
        val pathsList = modules.map { module ->
            val absolutePath = getModulePath(module)
            mapOf(
                "module" to module,
                "relativePath" to module.replace(":", File.separator),
                "absolutePath" to absolutePath
            )
        }
        val data = json.encodeToString(pathsList)
        channel.send(McpResponse(UUID.randomUUID().toString(), "file.listModuleIncludes.result", data))
    }

    suspend fun handleListModuleFiles(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val modulePath = args["modulePath"] ?: run {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.listModuleFiles.error", "Missing modulePath"))
            channel.close()
            return
        }
        val dir = File(modulePath)
        val results = mutableListOf<String>()
        if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().forEach { file ->
                results.add(file.absolutePath)
            }
        }
        val data = json.encodeToString(results)
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.listModuleFiles.result", data))
    }

    suspend fun handleCreate(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val createArg = args["command"] ?: run {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.create.error", "Missing create command"))
            channel.close()
            return
        }
        val pathPattern = Regex("path:\\s*([^\\s]+)")
        val filePattern = Regex("file:\\s*([^\\s]+)")
        val pathMatch = pathPattern.find(createArg)
        val fileMatch = filePattern.find(createArg)
        if (pathMatch == null || fileMatch == null) {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.create.error", "Invalid command format. Use @Create: path:xxx file:yyy"))
            channel.close()
            return
        }
        val dirPath = pathMatch.groupValues[1]
        val fileName = fileMatch.groupValues[1]
        val isDirectory = args["isDirectory"]?.toBoolean() ?: false
        
        val projectManager = ProjectManagerImpl.getInstance()
        val projectRoot = File(projectManager.projectDirPath)
        val file = if (dirPath.startsWith(File.separator)) {
            File(dirPath, fileName)
        } else {
            projectRoot.resolve(dirPath).resolve(fileName)
        }
        try {
            val created = if (isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.create.result", "{\"success\":$created}"))
        } catch (e: Exception) {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.create.error", e.localizedMessage ?: "Unknown error"))
        }
    }

    suspend fun handleWrite(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val path = args["path"] ?: run {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.write.error", "Missing path"))
            channel.close()
            return
        }
        val content = args["content"] ?: ""
        val file = File(path)
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            file.writeText(content)
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.write.result", "{\"success\":true}"))
        } catch (e: Exception) {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.write.error", e.localizedMessage ?: "Unknown error"))
        }
    }

    suspend fun handleUpload(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val rawPath = args["path"] ?: run {
            channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.error", "Missing path"))
            channel.close()
            return
        }
        val recursive = args["recursive"]?.toBoolean() ?: false
        val projectManager = ProjectManagerImpl.getInstance()
        val projectRootFile = File(projectManager.projectDirPath)
        val file = if (rawPath.startsWith(File.separator)) {
            File(rawPath)
        } else {
            projectRootFile.resolve(rawPath)
        }
        if (file == null || !file.exists()) {
            channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.error", "Invalid file or file not found: $rawPath"))
            channel.close()
            return
        }
        try {
            if (file.isDirectory) {
                val fileContents = StringBuilder()
                file.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        try {
                            val relativePath = projectRootFile?.let { f.relativeTo(it) } ?: f
                            fileContents.append("===== ${relativePath.path} =====\n")
                            fileContents.append(f.readText())
                            fileContents.append("\n\n")
                        } catch (e: Exception) {
                            fileContents.append("===== ${f.absolutePath} =====\n")
                            fileContents.append("Error reading file: ${e.localizedMessage}\n\n")
                        }
                    } else if (!f.isDirectory && recursive) {
                        // If not a directory and recursive, skip (shouldn't reach here)
                    }
                }
                channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.content", fileContents.toString()))
            } else {
                val content = file.readText()
                val relativePath = projectRootFile?.let { file.relativeTo(it) } ?: file
                val fullContent = "===== ${relativePath.path} =====\n$content"
                channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.content", fullContent))
            }
        } catch (e: Exception) {
            channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.error", e.localizedMessage ?: "Failed to read file content"))
        } finally {
            channel.close()
        }
    }
}