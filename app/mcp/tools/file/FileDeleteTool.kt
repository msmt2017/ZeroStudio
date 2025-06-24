package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool: @File:delete
 * 功能：删除文件或文件夹
 * 参数：path=文件或文件夹路径
 * 示例：@File:delete:#path=app/src/main/java/OldFile.kt
 */
class FileDeleteTool(private val workspaceRoot: File) : McpTool {
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
            
            val result = performDelete(target)
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("FileDeleteTool error: ${e.message}")
            McpResponse.error("删除失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 执行删除操作
     */
    private fun performDelete(target: File): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        return try {
            if (target.isDirectory) {
                // 删除文件夹
                val fileCount = target.walkTopDown().filter { it.isFile }.count()
                val dirCount = target.walkTopDown().filter { it.isDirectory }.count() - 1
                val totalSize = calculateDirectorySize(target)
                
                if (target.deleteRecursively()) {
                    buildString {
                        appendLine("✅ 文件夹删除成功")
                        appendLine("路径: ${target.absolutePath}")
                        appendLine("删除时间: ${dateFormat.format(Date())}")
                        appendLine("删除的文件数: $fileCount")
                        appendLine("删除的文件夹数: $dirCount")
                        appendLine("总大小: ${formatFileSize(totalSize)}")
                    }
                } else {
                    "❌ 文件夹删除失败: ${target.absolutePath}"
                }
            } else {
                // 删除文件
                val fileSize = target.length()
                val lastModified = target.lastModified()
                
                if (target.delete()) {
                    buildString {
                        appendLine("✅ 文件删除成功")
                        appendLine("路径: ${target.absolutePath}")
                        appendLine("删除时间: ${dateFormat.format(Date())}")
                        appendLine("文件大小: ${formatFileSize(fileSize)}")
                        appendLine("原修改时间: ${dateFormat.format(Date(lastModified))}")
                    }
                } else {
                    "❌ 文件删除失败: ${target.absolutePath}"
                }
            }
        } catch (e: Exception) {
            "❌ 删除异常: ${e.message}"
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
}
