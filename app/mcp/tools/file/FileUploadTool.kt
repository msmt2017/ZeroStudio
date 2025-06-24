package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import android.zero.mcp.services.FileUploadService
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Tool: @File:Upload
 * 功能：上传指定文件给AI
 * 参数：Files=指定路径下的文件, getFolder=是否上传文件夹下所有文件
 * 示例：@File:Upload:#Files=app/src/main/java/MainActivity.kt
 * 示例：@File:Upload:#getFolder=app/src/main/java
 */
class FileUploadTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val files = request.params?.get("Files")
            val getFolder = request.params?.get("getFolder")
            
            if (files == null && getFolder == null) {
                return@withContext McpResponse.error("缺少 Files 或 getFolder 参数", request.id)
            }
            
            val result = if (files != null) {
                uploadSingleFile(files)
            } else {
                uploadFolderFiles(getFolder!!)
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("FileUploadTool error: ${e.message}")
            McpResponse.error("上传失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 上传单个文件
     */
    private suspend fun uploadSingleFile(filePath: String): String {
        val targetFile = if (filePath.startsWith("/")) {
            File(filePath)
        } else {
            File(workspaceRoot, filePath)
        }
        
        if (!targetFile.exists()) {
            return "❌ 文件不存在: ${targetFile.absolutePath}"
        }
        
        if (!targetFile.isFile) {
            return "❌ 目标不是文件: ${targetFile.absolutePath}"
        }
        
        return try {
            val content = targetFile.readText(StandardCharsets.UTF_8)
            
            val fileInfo = mapOf(
                "fileName" to targetFile.name,
                "filePath" to targetFile.absolutePath,
                "fileSize" to targetFile.length(),
                "lineCount" to content.lines().size,
                "fileContent" to content,
                "fileExtension" to targetFile.extension,
                "autoSend" to true,
                "uploadTime" to System.currentTimeMillis()
            )
            
            val uploadResult = FileUploadService.uploadToAI(fileInfo)
            
            if (uploadResult.success) {
                buildString {
                    appendLine("✅ 文件上传成功")
                    appendLine("文件名: ${targetFile.name}")
                    appendLine("路径: ${targetFile.absolutePath}")
                    appendLine("大小: ${formatFileSize(targetFile.length())}")
                    appendLine("行数: ${content.lines().size}")
                    appendLine("上传ID: ${uploadResult.uploadId}")
                }
            } else {
                "❌ 文件上传失败: ${uploadResult.message}"
            }
            
        } catch (e: Exception) {
            "❌ 读取文件失败: ${e.message}"
        }
    }
    
    /**
     * 上传文件夹下所有文件
     */
    private suspend fun uploadFolderFiles(folderPath: String): String {
        val targetFolder = if (folderPath.startsWith("/")) {
            File(folderPath)
        } else {
            File(workspaceRoot, folderPath)
        }
        
        if (!targetFolder.exists()) {
            return "❌ 文件夹不存在: ${targetFolder.absolutePath}"
        }
        
        if (!targetFolder.isDirectory) {
            return "❌ 目标不是文件夹: ${targetFolder.absolutePath}"
        }
        
        return try {
            val files = targetFolder.walkTopDown()
                .filter { it.isFile && it.canRead() }
                .filter { isUploadableFile(it.name) }
                .toList()
            
            if (files.isEmpty()) {
                return "⚠️ 文件夹中没有可上传的文件: ${targetFolder.absolutePath}"
            }
            
            var successCount = 0
            var failCount = 0
            val failedFiles = mutableListOf<String>()
            
            files.forEach { file ->
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    
                    val fileInfo = mapOf(
                        "fileName" to file.name,
                        "filePath" to file.absolutePath,
                        "fileSize" to file.length(),
                        "lineCount" to content.lines().size,
                        "fileContent" to content,
                        "fileExtension" to file.extension,
                        "autoSend" to false, // 批量上传时不自动发送
                        "uploadTime" to System.currentTimeMillis()
                    )
                    
                    val uploadResult = FileUploadService.uploadToAI(fileInfo)
                    if (uploadResult.success) {
                        successCount++
                    } else {
                        failCount++
                        failedFiles.add(file.name)
                    }
                } catch (e: Exception) {
                    failCount++
                    failedFiles.add("${file.name} (${e.message})")
                }
            }
            
            buildString {
                appendLine("📁 文件夹上传完成")
                appendLine("文件夹: ${targetFolder.absolutePath}")
                appendLine("成功: $successCount 个文件")
                appendLine("失败: $failCount 个文件")
                appendLine("总计: ${files.size} 个文件")
                
                if (failedFiles.isNotEmpty()) {
                    appendLine()
                    appendLine("❌ 失败的文件:")
                    failedFiles.forEach { fileName ->
                        appendLine("  - $fileName")
                    }
                }
            }
            
        } catch (e: Exception) {
            "❌ 读取文件夹失败: ${e.message}"
        }
    }
    
    /**
     * 判断是否为可上传的文件
     */
    private fun isUploadableFile(fileName: String): Boolean {
        val uploadableExtensions = setOf(
            "java", "kt", "ktm", "xml", "gradle", "properties", "yml", "yaml",
            "json", "txt", "md", "html", "css", "js", "sh", "bat", "py", "c", "cpp",
            "h", "hpp", "cs", "php", "rb", "go", "rs", "swift", "scala", "r", "sql"
        )
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return uploadableExtensions.contains(ext)
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