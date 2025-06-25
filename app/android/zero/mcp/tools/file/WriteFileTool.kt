package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Tool: @File:WriteFile
 * 功能：写入文件内容，支持指定行写入
 * 参数：path=文件路径, content=写入内容, writeLine=指定行号（可选）
 * 示例：@File:WriteFile:#path=app/src/main/java/MainActivity.kt,#content=public class MainActivity,#writeLine=5
 */
class WriteFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val filePath = request.params?.get("path") 
                ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
            val content = request.params["content"] 
                ?: return@withContext McpResponse.error("缺少 content 参数", request.id)
            val writeLineStr = request.params["writeLine"]
            
            // 构建完整文件路径
            val targetFile = if (filePath.startsWith("/")) {
                File(filePath)
            } else {
                File(workspaceRoot, filePath)
            }
            
            // 确保父目录存在
            targetFile.parentFile?.mkdirs()
            
            // 如果文件不存在，创建文件
            if (!targetFile.exists()) {
                targetFile.createNewFile()
            }
            
            val result = if (writeLineStr != null) {
                // 指定行写入
                val writeLine = writeLineStr.toIntOrNull() 
                    ?: return@withContext McpResponse.error("无效的行号: $writeLineStr", request.id)
                
                if (writeLine < 1) {
                    return@withContext McpResponse.error("行号必须大于0", request.id)
                }
                
                writeToSpecificLine(targetFile, content, writeLine)
            } else {
                // 追加写入
                appendToFile(targetFile, content)
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("WriteFileTool error: ${e.message}")
            McpResponse.error("写入失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 写入指定行
     */
    private fun writeToSpecificLine(file: File, content: String, lineNumber: Int): String {
        val lines = file.readLines(StandardCharsets.UTF_8).toMutableList()
        
        // 如果行号超出范围，用空行填充
        while (lines.size < lineNumber) {
            lines.add("")
        }
        
        // 插入内容到指定行（行号从1开始，数组从0开始）
        lines.add(lineNumber - 1, content)
        
        // 写入文件
        file.writeText(lines.joinToString("\n"), StandardCharsets.UTF_8)
        
        return buildString {
            appendLine("✅ 写入成功")
            appendLine("文件: ${file.absolutePath}")
            appendLine("行号: $lineNumber")
            appendLine("内容: $content")
            appendLine("总行数: ${lines.size}")
        }
    }
    
    /**
     * 追加到文件
     */
    private fun appendToFile(file: File, content: String): String {
        val originalContent = if (file.exists()) file.readText(StandardCharsets.UTF_8) else ""
        val newContent = if (originalContent.isNotEmpty() && !originalContent.endsWith("\n")) {
            "$originalContent\n$content"
        } else {
            "$originalContent$content"
        }
        
        file.writeText(newContent, StandardCharsets.UTF_8)
        
        return buildString {
            appendLine("✅ 追加写入成功")
            appendLine("文件: ${file.absolutePath}")
            appendLine("追加内容: $content")
            appendLine("文件大小: ${file.length()} 字节")
        }
    }
}

/**
 * Tool: @File:Rename
 * 功能：重命名文件或文件夹
 * 参数：DestinationPath=原始路径, RenameContent=新名称
 * 示例：@File:Rename:#DestinationPath=app/src/main/java/OldName.kt,#RenameContent=NewName.kt
 */
class RenameFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val oldPath = request.params?.get("DestinationPath") 
                ?: return@withContext McpResponse.error("缺少 DestinationPath 参数", request.id)
            val newName = request.params["RenameContent"] 
                ?: return@withContext McpResponse.error("缺少 RenameContent 参数", request.id)
            
            // 构建完整路径
            val sourceFile = if (oldPath.startsWith("/")) {
                File(oldPath)
            } else {
                File(workspaceRoot, oldPath)
            }
            
            if (!sourceFile.exists()) {
                return@withContext McpResponse.error("源文件不存在: ${sourceFile.absolutePath}", request.id)
            }
            
            // 检查新名称是否包含路径分隔符
            if (newName.contains("/") || newName.contains("\\")) {
                return@withContext McpResponse.error("新名称不能包含路径分隔符", request.id)
            }
            
            val targetFile = File(sourceFile.parentFile, newName)
            
            // 检查目标文件是否已存在
            if (targetFile.exists()) {
                return@withContext McpResponse.error("目标文件已存在: ${targetFile.absolutePath}", request.id)
            }
            
            // 执行重命名
            if (sourceFile.renameTo(targetFile)) {
                val result = buildString {
                    appendLine("✅ 重命名成功")
                    appendLine("原路径: ${sourceFile.absolutePath}")
                    appendLine("新路径: ${targetFile.absolutePath}")
                    appendLine("文件类型: ${if (targetFile.isDirectory) "文件夹" else "文件"}")
                    if (targetFile.isFile) {
                        appendLine("文件大小: ${targetFile.length()} 字节")
                    }
                }
                McpResponse.success(request.id, result)
            } else {
                McpResponse.error("重命名失败，可能是权限不足或文件被占用", request.id)
            }
            
        } catch (e: Exception) {
            McpServerLog.log("RenameFileTool error: ${e.message}")
            McpResponse.error("重命名异常: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:move
 * 功能：移动文件或文件夹
 * 参数：movePath=源路径, DestinationPath=目标路径
 * 示例：@File:move:#movePath=app/src/main/java/oldpackage,#DestinationPath=app/src/main/java/newpackage
 */
class MoveFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val srcPath = request.params?.get("movePath") 
                ?: return@withContext McpResponse.error("缺少 movePath 参数", request.id)
            val destPath = request.params["DestinationPath"] 
                ?: return@withContext McpResponse.error("缺少 DestinationPath 参数", request.id)
            
            // 构建完整路径
            val sourceFile = if (srcPath.startsWith("/")) {
                File(srcPath)
            } else {
                File(workspaceRoot, srcPath)
            }
            
            val destDir = if (destPath.startsWith("/")) {
                File(destPath)
            } else {
                File(workspaceRoot, destPath)
            }
            
            if (!sourceFile.exists()) {
                return@withContext McpResponse.error("源文件不存在: ${sourceFile.absolutePath}", request.id)
            }
            
            // 确保目标目录存在
            destDir.mkdirs()
            
            val targetFile = File(destDir, sourceFile.name)
            
            // 检查目标文件是否已存在
            if (targetFile.exists()) {
                return@withContext McpResponse.error("目标文件已存在: ${targetFile.absolutePath}", request.id)
            }
            
            // 执行移动
            if (sourceFile.renameTo(targetFile)) {
                val result = buildString {
                    appendLine("✅ 移动成功")
                    appendLine("源路径: ${sourceFile.absolutePath}")
                    appendLine("目标路径: ${targetFile.absolutePath}")
                    appendLine("文件类型: ${if (targetFile.isDirectory) "文件夹" else "文件"}")
                    if (targetFile.isFile) {
                        appendLine("文件大小: ${targetFile.length()} 字节")
                    }
                }
                McpResponse.success(request.id, result)
            } else {
                McpResponse.error("移动失败，可能是权限不足或文件被占用", request.id)
            }
            
        } catch (e: Exception) {
            McpServerLog.log("MoveFileTool error: ${e.message}")
            McpResponse.error("移动异常: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:copy
 * 功能：复制文件或文件夹
 * 参数：copyPath=源路径, DestinationPath=目标路径
 * 示例：@File:copy:#copyPath=app/src/main/java/MyClass.kt,#DestinationPath=app/src/main/java/backup/
 */
class CopyFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val srcPath = request.params?.get("copyPath") 
                ?: return@withContext McpResponse.error("缺少 copyPath 参数", request.id)
            val destPath = request.params["DestinationPath"] 
                ?: return@withContext McpResponse.error("缺少 DestinationPath 参数", request.id)
            
            // 构建完整路径
            val sourceFile = if (srcPath.startsWith("/")) {
                File(srcPath)
            } else {
                File(workspaceRoot, srcPath)
            }
            
            val destDir = if (destPath.startsWith("/")) {
                File(destPath)
            } else {
                File(workspaceRoot, destPath)
            }
            
            if (!sourceFile.exists()) {
                return@withContext McpResponse.error("源文件不存在: ${sourceFile.absolutePath}", request.id)
            }
            
            // 确保目标目录存在
            destDir.mkdirs()
            
            val targetFile = File(destDir, sourceFile.name)
            
            // 执行复制
            if (sourceFile.isDirectory) {
                // 复制文件夹
                copyDirectory(sourceFile, targetFile)
                val result = buildString {
                    appendLine("✅ 文件夹复制成功")
                    appendLine("源路径: ${sourceFile.absolutePath}")
                    appendLine("目标路径: ${targetFile.absolutePath}")
                    appendLine("文件夹大小: ${calculateDirectorySize(targetFile)} 字节")
                }
                McpResponse.success(request.id, result)
            } else {
                // 复制文件
                sourceFile.copyTo(targetFile, overwrite = true)
                val result = buildString {
                    appendLine("✅ 文件复制成功")
                    appendLine("源路径: ${sourceFile.absolutePath}")
                    appendLine("目标路径: ${targetFile.absolutePath}")
                    appendLine("文件大小: ${targetFile.length()} 字节")
                }
                McpResponse.success(request.id, result)
            }
            
        } catch (e: Exception) {
            McpServerLog.log("CopyFileTool error: ${e.message}")
            McpResponse.error("复制异常: ${e.message}", request.id)
        }
    }
    
    /**
     * 复制文件夹
     */
    private fun copyDirectory(source: File, target: File) {
        if (!target.exists()) {
            target.mkdirs()
        }
        
        source.listFiles()?.forEach { file ->
            val targetFile = File(target, file.name)
            if (file.isDirectory) {
                copyDirectory(file, targetFile)
            } else {
                file.copyTo(targetFile, overwrite = true)
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
}
