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
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tool: @File:create
 * 功能：创建文件或文件夹
 * 参数：path=路径, folder=是否创建文件夹(true/false), files=文件名（可选）
 * 示例：@File:create:#path=app/src/main/java,#folder=false,#files=NewClass.kt
 */
class FileCreateTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val path = request.params?.get("path") 
                ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
            val isFolder = request.params["folder"]?.toBoolean() ?: false
            val fileName = request.params["files"]
            
            // 构建完整路径
            val targetPath = if (path.startsWith("/")) {
                path
            } else {
                File(workspaceRoot, path).absolutePath
            }
            
            val result = if (isFolder) {
                createDirectory(targetPath)
            } else {
                createFile(targetPath, fileName)
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("FileCreateTool error: ${e.message}")
            McpResponse.error("创建失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 创建文件夹
     */
    private fun createDirectory(path: String): String {
        val dir = File(path)
        
        if (dir.exists()) {
            return if (dir.isDirectory) {
                "⚠️ 文件夹已存在: ${dir.absolutePath}"
            } else {
                "❌ 错误：路径已存在但不是文件夹: ${dir.absolutePath}"
            }
        }
        
        return try {
            if (dir.mkdirs()) {
                "✅ 文件夹创建成功: ${dir.absolutePath}"
            } else {
                "❌ 文件夹创建失败: ${dir.absolutePath}"
            }
        } catch (e: Exception) {
            "❌ 创建文件夹异常: ${e.message}"
        }
    }
    
    /**
     * 创建文件
     */
    private fun createFile(path: String, fileName: String?): String {
        val targetFile = if (fileName != null) {
            File(path, fileName)
        } else {
            File(path)
        }
        
        if (targetFile.exists()) {
            return if (targetFile.isFile) {
                "⚠️ 文件已存在: ${targetFile.absolutePath}"
            } else {
                "❌ 错误：路径已存在但不是文件: ${targetFile.absolutePath}"
            }
        }
        
        return try {
            // 确保父目录存在
            targetFile.parentFile?.mkdirs()
            
            if (targetFile.createNewFile()) {
                // 根据文件扩展名添加默认内容
                val defaultContent = getDefaultContent(targetFile.name)
                if (defaultContent.isNotEmpty()) {
                    targetFile.writeText(defaultContent, StandardCharsets.UTF_8)
                }
                
                "✅ 文件创建成功: ${targetFile.absolutePath}"
            } else {
                "❌ 文件创建失败: ${targetFile.absolutePath}"
            }
        } catch (e: Exception) {
            "❌ 创建文件异常: ${e.message}"
        }
    }
    
    /**
     * 根据文件扩展名获取默认内容
     */
    private fun getDefaultContent(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "kt" -> {
                """package 

class ${fileName.substringBeforeLast('.')} {
    
}
"""
            }
            "java" -> {
                """package ;

public class ${fileName.substringBeforeLast('.')} {
    
}
"""
            }
            "xml" -> {
                """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

</LinearLayout>
"""
            }
            "gradle" -> {
                """// Gradle configuration file
"""
            }
            "properties" -> {
                """# Properties file
"""
            }
            "json" -> {
                """{
    
}
"""
            }
            "md" -> {
                """# ${fileName.substringBeforeLast('.')}

## 描述

## 用法

"""
            }
            "txt" -> {
                """${fileName.substringBeforeLast('.')}
================

"""
            }
            else -> ""
        }
    }
}