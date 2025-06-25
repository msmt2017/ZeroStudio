package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.project.ProjectManager
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.CRC32

/**
 * Tool: @File:info
 * 功能：获取文件或文件夹的详细信息，包括大小、时间、哈希值等
 * 参数：path=文件或文件夹路径
 * 示例：@File:info:#path=app/src/main/java/MainActivity.kt
 */
class FileInfoTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val path = request.params?.get("path") 
                ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
            
            // 构建完整路径
            val target = if (path.startsWith("/")) {
                File(path)
            } else {
                File(workspaceRoot, path)
            }
            
            if (!target.exists()) {
                return@withContext McpResponse.error("目标不存在: ${target.absolutePath}", request.id)
            }
            
            val info = if (target.isDirectory) {
                getDirectoryInfo(target)
            } else {
                getFileInfo(target)
            }
            
            McpResponse.success(request.id, info)
            
        } catch (e: Exception) {
            McpServerLog.log("FileInfoTool error: ${e.message}")
            McpResponse.error("信息获取失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 获取文件信息
     */
    private fun getFileInfo(file: File): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val bytes = file.readBytes()
        
        return buildString {
            appendLine("📄 文件信息")
            appendLine("=" * 50)
            appendLine("文件名: ${file.name}")
            appendLine("绝对路径: ${file.absolutePath}")
            appendLine("相对路径: ${file.relativeTo(workspaceRoot)}")
            appendLine("文件类型: ${getFileExtension(file.name)}")
            appendLine("文件大小: ${formatFileSize(file.length())}")
            appendLine("创建时间: ${dateFormat.format(Date(file.lastModified()))}")
            appendLine("可读: ${file.canRead()}")
            appendLine("可写: ${file.canWrite()}")
            appendLine("可执行: ${file.canExecute()}")
            appendLine()
            
            // 哈希值信息
            appendLine("🔐 哈希值信息")
            appendLine("-" * 30)
            appendLine("MD5: ${bytes.digest("MD5")}")
            appendLine("SHA-1: ${bytes.digest("SHA-1")}")
            appendLine("SHA-256: ${bytes.digest("SHA-256")}")
            appendLine("CRC32: ${bytes.crc32()}")
            appendLine()
            
            // 文本文件额外信息
            if (isTextFile(file.name)) {
                val content = file.readText(StandardCharsets.UTF_8)
                appendLine("📝 文本信息")
                appendLine("-" * 30)
                appendLine("字符数: ${content.length}")
                appendLine("行数: ${content.lines().size}")
                appendLine("单词数: ${content.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size}")
                appendLine("编码: UTF-8")
            }
        }
    }
    
    /**
     * 获取文件夹信息
     */
    private fun getDirectoryInfo(dir: File): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fileCount = dir.walkTopDown().filter { it.isFile }.count()
        val dirCount = dir.walkTopDown().filter { it.isDirectory }.count() - 1 // 减去自身
        val totalSize = calculateDirectorySize(dir)
        
        return buildString {
            appendLine("📁 文件夹信息")
            appendLine("=" * 50)
            appendLine("文件夹名: ${dir.name}")
            appendLine("绝对路径: ${dir.absolutePath}")
            appendLine("相对路径: ${dir.relativeTo(workspaceRoot)}")
            appendLine("最后修改: ${dateFormat.format(Date(dir.lastModified()))}")
            appendLine("可读: ${dir.canRead()}")
            appendLine("可写: ${dir.canWrite()}")
            appendLine("可执行: ${dir.canExecute()}")
            appendLine()
            
            appendLine("📊 统计信息")
            appendLine("-" * 30)
            appendLine("总大小: ${formatFileSize(totalSize)}")
            appendLine("文件数量: $fileCount")
            appendLine("文件夹数量: $dirCount")
            appendLine("总项目数: ${fileCount + dirCount}")
            appendLine()
            
            // 文件类型统计
            val fileTypes = mutableMapOf<String, Int>()
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val ext = getFileExtension(file.name)
                fileTypes[ext] = fileTypes.getOrDefault(ext, 0) + 1
            }
            
            if (fileTypes.isNotEmpty()) {
                appendLine("📋 文件类型统计")
                appendLine("-" * 30)
                fileTypes.entries.sortedByDescending { it.value }.forEach { (ext, count) ->
                    appendLine("$ext: $count 个文件")
                }
                appendLine()
            }
            
            // 大文件列表（前10个）
            val largeFiles = dir.walkTopDown()
                .filter { it.isFile }
                .sortedByDescending { it.length() }
                .take(10)
                .toList()
            
            if (largeFiles.isNotEmpty()) {
                appendLine("📏 大文件列表（前10个）")
                appendLine("-" * 30)
                largeFiles.forEachIndexed { index, file ->
                    appendLine("${index + 1}. ${file.name} (${formatFileSize(file.length())})")
                }
            }
        }
    }
    
    /**
     * 计算文件夹大小
     */
    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return if (ext.isNotEmpty()) ".$ext" else "无扩展名"
    }
    
    /**
     * 判断是否为文本文件
     */
    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = setOf(
            "txt", "md", "json", "xml", "html", "css", "js", "java", "kt", "ktm", 
            "gradle", "properties", "yml", "yaml", "sh", "bat", "py", "c", "cpp", 
            "h", "hpp", "cs", "php", "rb", "go", "rs", "swift", "scala", "r", 
            "sql", "log", "ini", "cfg", "conf", "config"
        )
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return textExtensions.contains(ext)
    }
    
    /**
     * 计算哈希值
     */
    private fun ByteArray.digest(alg: String): String {
        return MessageDigest.getInstance(alg).digest(this).joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 计算CRC32
     */
    private fun ByteArray.crc32(): String {
        return CRC32().apply { update(this@crc32) }.value.toString()
    }
    
    /**
     * 字符串重复操作符
     */
    private operator fun String.times(count: Int): String {
        return repeat(count)
    }
}
