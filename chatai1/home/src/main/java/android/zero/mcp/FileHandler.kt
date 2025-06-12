package android.zero.mcp

import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import android.zero.mcp.McpResponse
import java.util.UUID
import java.io.File
import com.itsaky.androidide.projects.ProjectManagerImpl
import com.itsaky.androidide.projects.IProjectManager

/**
 * 处理所有文件相关的 MCP 请求，包括：
 * - 按名称搜索 (searchName)
 * - 按内容搜索 (searchContent)
 * - 列出 settings.gradle 中的 include 模块 (listModuleIncludes)
 * - 列出某模块的所有文件 (listModuleFiles)
 * - 创建文件或文件夹 (create)
 * - 向文件写入内容 (write)
 * - 上传文件内容给前端 AI (upload)
 */
class FileHandler {

    // Kotlinx Serialization 实例，用于将结果编码为 JSON 字符串
    private val json = Json { encodeDefaults = true }

    /**
     * 根据文件名关键字在 projectRoot 目录及子目录下搜索文件/文件夹。
     * args:
     * - "keyword": 要搜索的关键字 (String)
     * - "projectRoot": 项目根目录绝对路径 (String)
     * 返回事件 "file.searchName.result"，data = JSON List<String>（匹配的绝对路径）
     */
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
        // Ensure 'result' parameter is always passed
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.searchName.result", data))
    }

    /**
     * 根据文件内容关键字在 projectRoot 目录下所有文件中搜索。
     * args:
     * - "content": 要搜索的内容字符串 (String)
     * - "projectRoot": 项目根目录绝对路径 (String)
     * 返回事件 "file.searchContent.result"，data = JSON List<String>（匹配的文件绝对路径）
     */
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
        // Ensure 'result' parameter is always passed
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.searchContent.result", data))
    }

    /**
     * 解析 settings.gradle 或 settings.gradle.kts 中的 include(...) 模块名列表。
     * args:
     * - "projectRoot": 项目根目录绝对路径 (String)
     * 返回事件 "file.listModuleIncludes.result"，data = JSON List<Map<String,String>>
     * 每个 Map 包含键: "module", "relativePath", "absolutePath"
     */
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

    /**
     * 列出指定模块目录下的所有文件（递归）。
     * args:
     * - "modulePath": 要列出的模块目录绝对路径 (String)
     * 返回事件 "file.listModuleFiles.result"，data = JSON List<String>（所有文件/目录绝对路径）
     */
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
        // Ensure 'result' parameter is always passed
        channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.listModuleFiles.result", data))
    }

    /**
     * 创建文件或目录。
     * args:
     * - "path": 要创建的绝对路径 (String)
     * - "isDirectory": "true" 表示创建目录，否则创建文件 (String)
     * 返回事件 "file.create.result" 或 "file.create.error"。
     * 成功时 data = '{"success":true}', 失败时 data = errorMessage
     */
    suspend fun handleCreate(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        // 解析@Create:指令格式 path:xxx file:yyy
        val createArg = args["command"] ?: run {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.create.error", "Missing create command"))
            channel.close()
            return
        }
        val pathPattern = Regex("""path:\s*([^\s]+)""")
        val filePattern = Regex("""file:\s*([^\s]+)""")
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
        
        // 获取项目根路径
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

    /**
     * 向指定文件写入内容（覆盖模式）。如果文件不存在，则先创建。
     * args:
     * - "path": 文件绝对路径 (String)
     * - "content": 要写入的文本内容 (String)
     * 返回事件 "file.write.result" 或 "file.write.error"。
     * 成功时 data = '{"success":true}', 失败时 data = errorMessage
     */
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

    /**
     * 上传文件内容给前端 AI 窗口。
     * args:
     * - "path": 文件绝对路径或相对路径 (String)
     * - "projectRoot": 项目根目录绝对路径 (String，可选，当 path 为相对路径时必传)
     * - "recursive": 是否递归上传文件夹内容 (String，可选，仅对文件夹有效)
     * 返回事件 "file.upload.content"（data = 全部文件文本）或 "file.upload.error"（data = 错误信息）。
     */
    suspend fun handleUpload(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val rawPath = args["path"] ?: run {
            channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.error", "Missing path"))
            channel.close()
            return
        }
        val recursive = args["recursive"]?.toBoolean() ?: false
        val projectManager = ProjectManagerImpl.getInstance()
        val projectRootFile = File(projectManager.projectDirPath)
        // 解析并验证路径，防止路径遍历攻击
val resolvedFile = if (rawPath.startsWith(File.separator)) {
    File(rawPath)
} else {
    projectRootFile.resolve(rawPath)
}

// 确保文件在项目根目录内
val canonicalProjectRoot = projectRootFile.canonicalPath
val canonicalFile = resolvedFile.canonicalPath
if (!canonicalFile.startsWith(canonicalProjectRoot)) {
    channel.send(McpResponse(UUID.randomUUID().toString(), "file.upload.error", "Access denied: Path outside project root"))
    channel.close()
    return
}
val file = resolvedFile
        if (file == null || !file.exists()) {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.upload.error", "Invalid file or file not found: $rawPath"))
            channel.close()
            return
        }
        try {
            if (file.isDirectory) {
                // 处理文件夹上传
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
                        // 如果不是目录且需要递归，跳过（理论上不会执行到这里）
                    }
                }
                channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.upload.content", fileContents.toString()))
            } else {
                // 处理文件上传
                val content = file.readText()
                val relativePath = projectRootFile?.let { file.relativeTo(it) } ?: file
                val fullContent = "===== ${relativePath.path} =====\n$content"
                channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.upload.content", fullContent))
            }
        } catch (e: Exception) {
            channel.send(McpResponse(java.util.UUID.randomUUID().toString(), "file.upload.error", e.localizedMessage ?: "Failed to read file content"))
        } finally {
            channel.close() // Close the channel after sending content or error
        }
    }
}
