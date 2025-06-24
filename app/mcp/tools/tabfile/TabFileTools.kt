package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import android.zero.mcp.utils.EditorActivityManager
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.ui.CodeEditorView
import com.itsaky.androidide.models.Range
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

/**
 * TabFile工具集 - 用于操作编辑器中的标签页文件
 * 支持获取文件内容、光标位置、函数信息、搜索等功能
 */

/**
 * Tool: @TabFile:getCursor
 * 功能：获取光标所在行的完整内容
 */
class GetCursorTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val cursor = currentEditor.editor.cursor
            val content = currentEditor.editor.text
            
            val line = cursor.leftLine
            if (line !in 0 until content.lineCount) {
                return@withContext McpResponse.error("光标行越界: $line", request.id)
            }
            
            val lineText = content.getLineString(line)
            val result = mapOf(
                "line" to (line + 1), // 转换为1基索引
                "column" to (cursor.leftColumn + 1),
                "content" to lineText,
                "file" to currentEditor.file.absolutePath
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetCursorTool error: ${e.message}")
            McpResponse.error("获取光标内容失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:getLine
 * 功能：获取指定行内容，支持单行或多行范围
 * 参数：lineRange (格式: "5" 或 "5-10")
 */
class GetLineTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val lineRange = request.params?.get("lineRange") 
                ?: return@withContext McpResponse.error("缺少 lineRange 参数", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val content = currentEditor.editor.text
            val totalLines = content.lineCount
            
            val lines = parseLineRange(lineRange, totalLines)
            if (lines.isEmpty()) {
                return@withContext McpResponse.error("无效的行范围: $lineRange", request.id)
            }
            
            val result = mutableMapOf<String, Any>()
            result["file"] = currentEditor.file.absolutePath
            result["totalLines"] = totalLines
            result["requestedRange"] = lineRange
            
            if (lines.size == 1) {
                // 单行
                val lineNum = lines.first()
                val lineText = content.getLineString(lineNum)
                result["line"] = (lineNum + 1) // 转换为1基索引
                result["content"] = lineText
            } else {
                // 多行范围
                val lineContents = mutableListOf<String>()
                lines.forEach { lineNum ->
                    lineContents.add(content.getLineString(lineNum))
                }
                result["startLine"] = (lines.first() + 1) // 转换为1基索引
                result["endLine"] = (lines.last() + 1)
                result["content"] = lineContents.joinToString("\n")
            }
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetLineTool error: ${e.message}")
            McpResponse.error("获取行内容失败: ${e.message}", request.id)
        }
    }
    
    private fun parseLineRange(range: String, totalLines: Int): List<Int> {
        return try {
            if (range.contains("-")) {
                val parts = range.split("-")
                if (parts.size != 2) return emptyList()
                
                val start = parts[0].trim().toInt() - 1 // 转换为0基索引
                val end = parts[1].trim().toInt() - 1
                
                if (start < 0 || end >= totalLines || start > end) {
                    return emptyList()
                }
                
                (start..end).toList()
            } else {
                val lineNum = range.trim().toInt() - 1 // 转换为0基索引
                if (lineNum < 0 || lineNum >= totalLines) {
                    return emptyList()
                }
                listOf(lineNum)
            }
        } catch (e: NumberFormatException) {
            emptyList()
        }
    }
}

/**
 * Tool: @TabFile:getFile
 * 功能：获取当前活动标签页文件的完整内容
 * 注意：获取的是用户当前选中的活动标签页文件
 */
class GetFileTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前活动标签页", request.id)
            
            val content = currentEditor.editor.text
            val file = currentEditor.file
            
            val result = mapOf(
                "file" to file.absolutePath,
                "name" to file.name,
                "size" to content.length(),
                "lines" to content.lineCount,
                "content" to content.toString(),
                "modified" to currentEditor.isModified,
                "isActiveTab" to true,
                "tabIndex" to editorActivity.getCurrentTabIndex(),
                "totalTabs" to editorActivity.getTotalTabCount()
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetFileTool error: ${e.message}")
            McpResponse.error("获取当前活动标签页文件失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:searchTabFile
 * 功能：在当前标签页文件中搜索内容
 * 参数：searchContent (要搜索的内容)
 */
class SearchTabFileTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val searchContent = request.params?.get("searchContent") 
                ?: return@withContext McpResponse.error("缺少 searchContent 参数", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val content = currentEditor.editor.text
            val file = currentEditor.file
            
            val results = mutableListOf<Map<String, Any>>()
            val pattern = Pattern.compile(Pattern.quote(searchContent), Pattern.CASE_INSENSITIVE)
            
            for (lineIndex in 0 until content.lineCount) {
                val lineText = content.getLineString(lineIndex)
                val matcher = pattern.matcher(lineText)
                
                while (matcher.find()) {
                    val start = matcher.start()
                    val end = matcher.end()
                    val matchedText = lineText.substring(start, end)
                    
                    results.add(mapOf(
                        "line" to (lineIndex + 1), // 转换为1基索引
                        "column" to (start + 1),
                        "matchedText" to matchedText,
                        "lineContent" to lineText,
                        "startPos" to start,
                        "endPos" to end
                    ))
                }
            }
            
            val result = mapOf(
                "file" to file.absolutePath,
                "searchContent" to searchContent,
                "totalMatches" to results.size,
                "results" to results
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("SearchTabFileTool error: ${e.message}")
            McpResponse.error("搜索文件内容失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:getFunction
 * 功能：获取指定函数名的完整内容
 * 参数：functionName (函数名)
 */
class GetFunctionTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val functionName = request.params?.get("functionName") 
                ?: return@withContext McpResponse.error("缺少 functionName 参数", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val content = currentEditor.editor.text
            val file = currentEditor.file
            
            // 支持Java和Kotlin的函数定义模式
            val patterns = listOf(
                // Java方法: public/private/protected + 返回类型 + 方法名 + 参数
                Pattern.compile("\\b(public|private|protected|static|final|abstract|synchronized|native|strictfp)\\s+[\\w<>\\[\\]\\s]+\\s+$functionName\\s*\\(", Pattern.CASE_INSENSITIVE),
                // Kotlin函数: fun + 函数名 + 参数
                Pattern.compile("\\bfun\\s+$functionName\\s*\\(", Pattern.CASE_INSENSITIVE),
                // 构造函数: 类名 + 参数
                Pattern.compile("\\bclass\\s+$functionName\\s*\\(", Pattern.CASE_INSENSITIVE),
                // 简单函数名匹配
                Pattern.compile("\\b$functionName\\s*\\(", Pattern.CASE_INSENSITIVE)
            )
            
            val results = mutableListOf<Map<String, Any>>()
            
            for (lineIndex in 0 until content.lineCount) {
                val lineText = content.getLineString(lineIndex)
                
                for (pattern in patterns) {
                    val matcher = pattern.matcher(lineText)
                    if (matcher.find()) {
                        // 找到函数定义，尝试获取完整的函数体
                        val functionBody = extractFunctionBody(content, lineIndex)
                        
                        results.add(mapOf(
                            "line" to (lineIndex + 1),
                            "functionName" to functionName,
                            "definition" to lineText.trim(),
                            "body" to functionBody,
                            "startLine" to (lineIndex + 1),
                            "endLine" to (lineIndex + functionBody.lines().size + 1)
                        ))
                        break
                    }
                }
            }
            
            val result = mapOf(
                "file" to file.absolutePath,
                "functionName" to functionName,
                "totalMatches" to results.size,
                "results" to results
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetFunctionTool error: ${e.message}")
            McpResponse.error("获取函数内容失败: ${e.message}", request.id)
        }
    }
    
    private fun extractFunctionBody(content: Content, startLine: Int): String {
        val lines = mutableListOf<String>()
        var braceCount = 0
        var started = false
        
        for (i in startLine until content.lineCount) {
            val line = content.getLineString(i)
            
            if (!started) {
                // 找到第一个左大括号
                if (line.contains("{")) {
                    started = true
                    braceCount = line.count { it == '{' } - line.count { it == '}' }
                    lines.add(line)
                }
            } else {
                lines.add(line)
                braceCount += line.count { it == '{' } - line.count { it == '}' }
                
                if (braceCount <= 0) {
                    break // 函数体结束
                }
            }
        }
        
        return lines.joinToString("\n")
    }
}

/**
 * Tool: @TabFile:insertLine
 * 功能：在指定行插入内容
 * 参数：line (行号), content (要插入的内容)
 */
class InsertLineTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val lineStr = request.params?.get("line") 
                ?: return@withContext McpResponse.error("缺少 line 参数", request.id)
            val content = request.params?.get("content") 
                ?: return@withContext McpResponse.error("缺少 content 参数", request.id)
            
            val line = lineStr.toIntOrNull() 
                ?: return@withContext McpResponse.error("无效的行号: $lineStr", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val editor = currentEditor.editor
            val text = editor.text
            
            if (line < 1 || line > text.lineCount + 1) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            
            // 计算插入位置
            val insertIndex = if (line == 1) {
                0
            } else if (line > text.lineCount) {
                text.length
            } else {
                text.getIndexer().getCharPosition(line - 1, 0).index
            }
            
            // 插入内容
            editor.text.insert(insertIndex, 0, content + "\n")
            
            val result = mapOf(
                "file" to currentEditor.file.absolutePath,
                "line" to line,
                "insertedContent" to content,
                "success" to true
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("InsertLineTool error: ${e.message}")
            McpResponse.error("插入行内容失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:replaceLine
 * 功能：替换指定行的内容
 * 参数：line (行号), content (新的内容)
 */
class ReplaceLineTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val lineStr = request.params?.get("line") 
                ?: return@withContext McpResponse.error("缺少 line 参数", request.id)
            val content = request.params?.get("content") 
                ?: return@withContext McpResponse.error("缺少 content 参数", request.id)
            
            val line = lineStr.toIntOrNull() 
                ?: return@withContext McpResponse.error("无效的行号: $lineStr", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val editor = currentEditor.editor
            val text = editor.text
            
            if (line < 1 || line > text.lineCount) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            
            val lineIndex = line - 1
            val oldContent = text.getLineString(lineIndex)
            
            // 计算行的起始和结束位置
            val startIndex = text.getIndexer().getCharPosition(lineIndex, 0).index
            val endIndex = if (lineIndex == text.lineCount - 1) {
                text.length
            } else {
                text.getIndexer().getCharPosition(lineIndex + 1, 0).index
            }
            
            // 替换内容
            editor.text.replace(startIndex, endIndex - startIndex, content)
            
            val result = mapOf(
                "file" to currentEditor.file.absolutePath,
                "line" to line,
                "oldContent" to oldContent,
                "newContent" to content,
                "success" to true
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("ReplaceLineTool error: ${e.message}")
            McpResponse.error("替换行内容失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:deleteLine
 * 功能：删除指定行
 * 参数：line (行号)
 */
class DeleteLineTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val lineStr = request.params?.get("line") 
                ?: return@withContext McpResponse.error("缺少 line 参数", request.id)
            
            val line = lineStr.toIntOrNull() 
                ?: return@withContext McpResponse.error("无效的行号: $lineStr", request.id)
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val editor = currentEditor.editor
            val text = editor.text
            
            if (line < 1 || line > text.lineCount) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            
            val lineIndex = line - 1
            val deletedContent = text.getLineString(lineIndex)
            
            // 计算行的起始和结束位置
            val startIndex = text.getIndexer().getCharPosition(lineIndex, 0).index
            val endIndex = if (lineIndex == text.lineCount - 1) {
                text.length
            } else {
                text.getIndexer().getCharPosition(lineIndex + 1, 0).index
            }
            
            // 删除内容
            editor.text.delete(startIndex, endIndex - startIndex)
            
            val result = mapOf(
                "file" to currentEditor.file.absolutePath,
                "line" to line,
                "deletedContent" to deletedContent,
                "success" to true
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("DeleteLineTool error: ${e.message}")
            McpResponse.error("删除行失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:getCurrentFile
 * 功能：获取当前标签页文件信息
 */
class GetCurrentFileTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val file = currentEditor.file
            val content = currentEditor.editor.text
            
            val result = mapOf(
                "file" to file.absolutePath,
                "name" to file.name,
                "size" to file.length(),
                "lines" to content.lineCount,
                "modified" to currentEditor.isModified,
                "exists" to file.exists(),
                "canRead" to file.canRead(),
                "canWrite" to file.canWrite()
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetCurrentFileTool error: ${e.message}")
            McpResponse.error("获取当前文件信息失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:uploadFileToAI
 * 功能：将当前活动标签页文件上传发送到AI窗口
 * 参数：autoSend (可选，是否自动发送到AI，默认true)
 */
class UploadFileToAITool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val autoSend = request.params?.jsonObject?.get("autoSend")?.jsonPrimitive?.boolean ?: true
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前活动标签页", request.id)
            
            val content = currentEditor.editor.text
            val file = currentEditor.file
            
            // 构建上传到AI的文件信息
            val fileInfo = mapOf(
                "fileName" to file.name,
                "filePath" to file.absolutePath,
                "fileSize" to content.length(),
                "lineCount" to content.lineCount,
                "fileContent" to content.toString(),
                "fileExtension" to file.extension,
                "autoSend" to autoSend,
                "uploadTime" to System.currentTimeMillis()
            )
            
            // 调用文件上传服务
            val uploadResult = FileUploadService.uploadToAI(fileInfo)
            
            val result = mapOf(
                "success" to uploadResult.success,
                "message" to uploadResult.message,
                "fileInfo" to fileInfo,
                "uploadId" to uploadResult.uploadId
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("UploadFileToAITool error: ${e.message}")
            McpResponse.error("上传文件到AI失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:getAllEditorContent
 * 功能：获取当前编辑器窗口的所有文本内容
 * 参数：includeFileInfo (可选，是否包含文件信息，默认true)
 */
class GetAllEditorContentTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val includeFileInfo = request.params?.jsonObject?.get("includeFileInfo")?.jsonPrimitive?.boolean ?: true
            
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前编辑器", request.id)
            
            val content = currentEditor.editor.text
            val file = currentEditor.file
            
            val result = mutableMapOf<String, Any>()
            result["totalContent"] = content.toString()
            result["totalLength"] = content.length()
            result["totalLines"] = content.lineCount
            result["hasContent"] = content.length() > 0
            
            if (includeFileInfo) {
                result["fileInfo"] = mapOf(
                    "fileName" to file.name,
                    "filePath" to file.absolutePath,
                    "fileSize" to file.length(),
                    "fileExtension" to file.extension,
                    "isModified" to currentEditor.isModified
                )
            }
            
            // 添加内容统计信息
            result["contentStats"] = mapOf(
                "charCount" to content.length(),
                "lineCount" to content.lineCount,
                "wordCount" to content.toString().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size,
                "nonEmptyLines" to (0 until content.lineCount).count { content.getLineString(it).trim().isNotEmpty() }
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetAllEditorContentTool error: ${e.message}")
            McpResponse.error("获取编辑器所有内容失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:getActiveTabInfo
 * 功能：获取当前活动标签页的详细信息
 */
class GetActiveTabInfoTool : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        try {
            val editorActivity = EditorActivityManager.getCurrentEditorActivity() 
                ?: return@withContext McpResponse.error("未找到编辑器活动", request.id)
            
            val currentEditor = editorActivity.getCurrentEditor() 
                ?: return@withContext McpResponse.error("未找到当前活动标签页", request.id)
            
            val file = currentEditor.file
            val content = currentEditor.editor.text
            
            val result = mapOf(
                "isActive" to true,
                "tabIndex" to editorActivity.getCurrentTabIndex(),
                "totalTabs" to editorActivity.getTotalTabCount(),
                "fileName" to file.name,
                "filePath" to file.absolutePath,
                "fileExtension" to file.extension,
                "fileSize" to file.length(),
                "contentLength" to content.length(),
                "lineCount" to content.lineCount,
                "isModified" to currentEditor.isModified,
                "lastModified" to file.lastModified(),
                "canRead" to file.canRead(),
                "canWrite" to file.canWrite()
            )
            
            McpResponse.success(request.id, result)
        } catch (e: Exception) {
            McpServerLog.log("GetActiveTabInfoTool error: ${e.message}")
            McpResponse.error("获取活动标签页信息失败: ${e.message}", request.id)
        }
    }
} 