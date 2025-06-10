// 文件路径：app/src/main/java/android/zero/mcp/FileHandler.kt
package android.zero.mcp

import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 处理所有文件相关的 MCP 请求，包括：
 *  - 按名称搜索 (searchName)
 *  - 按内容搜索 (searchContent)
 *  - 列出 settings.gradle 中的 include 模块 (listModuleIncludes)
 *  - 列出某模块的所有文件 (listModuleFiles)
 *  - 创建文件或文件夹 (create)
 *  - 向文件写入内容 (write)
 *  - 上传文件内容给前端 AI (upload)
 */
class FileHandler {

    // Kotlinx Serialization 实例，用于将结果编码为 JSON 字符串
    private val json = Json { encodeDefaults = true }

    /**
     * 根据文件名关键字在 projectRoot 目录及子目录下搜索文件/文件夹。
     * args:
     *   - "keyword": 要搜索的关键字 (String)
     *   - "projectRoot": 项目根目录绝对路径 (String)
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
        channel.send(McpResponse("file.searchName.result", data))
    }

    /**
     * 根据文件内容关键字在 projectRoot 目录下所有文件中搜索。
     * args:
     *   - "content": 要搜索的内容字符串 (String)
     *   - "projectRoot": 项目根目录绝对路径 (String)
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
                        // 忽略无法读取的文件
                    }
                }
            }
        }
        val data = json.encodeToString(results)
        channel.send(McpResponse("file.searchContent.result", data))
    }

    /**
     * 解析 settings.gradle 或 settings.gradle.kts 中的 include(...) 模块名列表。
     * args:
     *   - "projectRoot": 项目根目录绝对路径 (String)
     * 返回事件 "file.listModuleIncludes.result"，data = JSON List<Map<String,String>>
     * 每个 Map 包含键: "module", "relativePath", "absolutePath"
     */
    suspend fun handleListIncludes(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val projectRoot = File(args["projectRoot"] ?: ".")
        // 查找 settings.gradle 或 settings.gradle.kts
        val settingsFile = projectRoot.resolve("settings.gradle").takeIf { it.exists() }
            ?: projectRoot.resolve("settings.gradle.kts").takeIf { it.exists() }
        val includes = mutableListOf<String>()
        settingsFile?.readLines()?.forEach { line ->
            val regex = Regex("include\\(([^)]+)\\)")
            val match = regex.find(line)
            match?.groups?.get(1)?.value?.split(",")?.map { it.trim().trim('"', '\'') }
                ?.forEach { includes.add(it) }
        }
        val pathsList = includes.map { module ->
            val relative = module.replace(":", File.separator)
            val absolute = projectRoot.resolve(relative).absolutePath
            mapOf(
                "module" to module,
                "relativePath" to relative,
                "absolutePath" to absolute
            )
        }
        val data = json.encodeToString(pathsList)
        channel.send(McpResponse("file.listModuleIncludes.result", data))
    }

    /**
     * 列出指定模块目录下的所有文件（递归）。
     * args:
     *   - "modulePath": 要列出的模块目录绝对路径 (String)
     * 返回事件 "file.listModuleFiles.result"，data = JSON List<String>（所有文件/目录绝对路径）
     */
    suspend fun handleListModuleFiles(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val modulePath = args["modulePath"] ?: return
        val dir = File(modulePath)
        val results = mutableListOf<String>()
        if (dir.exists() && dir.isDirectory) {
            dir.walkTopDown().forEach { file ->
                results.add(file.absolutePath)
            }
        }
        val data = json.encodeToString(results)
        channel.send(McpResponse("file.listModuleFiles.result", data))
    }

    /**
     * 创建文件或目录。
     * args:
     *   - "path": 要创建的绝对路径 (String)
     *   - "isDirectory": "true" 表示创建目录，否则创建文件 (String)
     * 返回事件 "file.create.result" 或 "file.create.error"。
     *   成功时 data = '{"success":true}', 失败时 data = errorMessage
     */
    suspend fun handleCreate(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val path = args["path"] ?: return
        val isDirectory = args["isDirectory"]?.toBoolean() ?: false
        val file = File(path)
        try {
            val created = if (isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            channel.send(McpResponse("file.create.result", "{\"success\":$created}"))
        } catch (e: Exception) {
            channel.send(McpResponse("file.create.error", e.localizedMessage ?: "Unknown error"))
        }
    }

    /**
     * 向指定文件写入内容（覆盖模式）。如果文件不存在，则先创建。
     * args:
     *   - "path": 文件绝对路径 (String)
     *   - "content": 要写入的文本内容 (String)
     * 返回事件 "file.write.result" 或 "file.write.error"。
     *   成功时 data = '{"success":true}', 失败时 data = errorMessage
     */
    suspend fun handleWrite(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val path = args["path"] ?: return
        val content = args["content"] ?: ""
        val file = File(path)
        try {
            if (!file.exists()) {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }
            file.writeText(content)
            channel.send(McpResponse("file.write.result", "{\"success\":true}"))
        } catch (e: Exception) {
            channel.send(McpResponse("file.write.error", e.localizedMessage ?: "Unknown error"))
        }
    }

    /**
     * 上传文件内容给前端 AI 窗口。
     * args:
     *   - "path": 文件绝对路径或相对路径 (String)
     *   - "projectRoot": 项目根目录绝对路径 (String，可选，当 path 为相对路径时必传)
     * 返回事件 "file.upload.content"（data = 全部文件文本）或 "file.upload.error"（data = 错误信息）。
     */
    suspend fun handleUpload(args: Map<String, String>, channel: SendChannel<McpResponse>) {
        val rawPath = args["path"] ?: run {
            channel.send(McpResponse("file.upload.error", "Missing path"))
            channel.close()
            return
        }
        val projectRootFile = args["projectRoot"]?.let { File(it) }
        val file = if (rawPath.startsWith(File.separator)) {
            File(rawPath)
        } else {
            projectRootFile?.resolve(rawPath)
        }
        if (file == null || !file.exists() || !file.isFile) {
            channel.send(McpResponse("file.upload.error", "Invalid file: $rawPath"))
            channel.close()
            return
