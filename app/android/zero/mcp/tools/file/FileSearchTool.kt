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
import java.util.regex.Pattern

/**
 * Tool: @File:search
 * 功能：在指定路径下搜索文件内容
 * 参数：path=搜索路径, content=搜索内容
 * 示例：@File:search:#path=app/src/main/java,#content=MainActivity
 */
class FileSearchTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val searchPath = request.params?.get("path") 
                ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
            val searchContent = request.params["content"] 
                ?: return@withContext McpResponse.error("缺少 content 参数", request.id)
            
            // 构建完整搜索路径
            val searchDir = if (searchPath.startsWith("/")) {
                File(searchPath)
            } else {
                File(workspaceRoot, searchPath)
            }
            
            if (!searchDir.exists() || !searchDir.isDirectory) {
                return@withContext McpResponse.error("无效的搜索路径: ${searchDir.absolutePath}", request.id)
            }
            
            val results = performSearch(searchDir, searchContent)
            
            if (results.isEmpty()) {
                McpResponse.success(request.id, "🔍 搜索完成，未找到匹配内容")
            } else {
                val resultText = buildSearchResults(results, searchContent)
                McpResponse.success(request.id, resultText)
            }
            
        } catch (e: Exception) {
            McpServerLog.log("FileSearchTool error: ${e.message}")
            McpResponse.error("搜索失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 执行搜索
     */
    private fun performSearch(searchDir: File, searchContent: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val pattern = Pattern.compile(Pattern.quote(searchContent), Pattern.CASE_INSENSITIVE)
        
        // 定义要搜索的文件扩展名
        val searchableExtensions = setOf(
            "java", "kt", "ktm", "xml", "gradle", "properties", "yml", "yaml",
            "json", "txt", "md", "html", "css", "js", "sh", "bat", "py", "c", "cpp",
            "h", "hpp", "cs", "php", "rb", "go", "rs", "swift", "scala", "r", "sql"
        )
        
        searchDir.walkTopDown()
            .filter { it.isFile && it.canRead() }
            .filter { file ->
                val ext = file.extension.lowercase()
                searchableExtensions.contains(ext) || ext.isEmpty()
            }
            .forEach { file ->
                try {
                    val content = file.readText(StandardCharsets.UTF_8)
                    val matcher = pattern.matcher(content)
                    val matches = mutableListOf<MatchInfo>()
                    
                    while (matcher.find()) {
                        val start = matcher.start()
                        val end = matcher.end()
                        val lineNumber = content.substring(0, start).count { it == '\n' } + 1
                        val lineStart = content.lastIndexOf('\n', start) + 1
                        val lineEnd = content.indexOf('\n', end).let { if (it == -1) content.length else it }
                        val lineContent = content.substring(lineStart, lineEnd)
                        
                        matches.add(MatchInfo(
                            lineNumber = lineNumber,
                            startColumn = start - lineStart + 1,
                            endColumn = end - lineStart + 1,
                            lineContent = lineContent,
                            matchedText = content.substring(start, end)
                        ))
                    }
                    
                    if (matches.isNotEmpty()) {
                        results.add(SearchResult(
                            file = file,
                            relativePath = file.relativeTo(workspaceRoot).path,
                            matches = matches
                        ))
                    }
                } catch (e: Exception) {
                    // 忽略无法读取的文件
                    McpServerLog.log("无法读取文件: ${file.absolutePath}, 错误: ${e.message}")
                }
            }
        
        return results.sortedBy { it.relativePath }
    }
    
    /**
     * 构建搜索结果
     */
    private fun buildSearchResults(results: List<SearchResult>, searchContent: String): String {
        return buildString {
            appendLine("🔍 搜索结果")
            appendLine("=" * 60)
            appendLine("搜索内容: \"$searchContent\"")
            appendLine("找到文件数: ${results.size}")
            appendLine("总匹配数: ${results.sumOf { it.matches.size }}")
            appendLine()
            
            results.forEachIndexed { fileIndex, result ->
                appendLine("📄 文件 ${fileIndex + 1}: ${result.relativePath}")
                appendLine("-" * 50)
                appendLine("绝对路径: ${result.file.absolutePath}")
                appendLine("文件大小: ${formatFileSize(result.file.length())}")
                appendLine("匹配数: ${result.matches.size}")
                appendLine()
                
                result.matches.forEachIndexed { matchIndex, match ->
                    appendLine("  匹配 ${matchIndex + 1} (第${match.lineNumber}行):")
                    appendLine("    ${match.lineContent}")
                    appendLine("    ${" " * (match.startColumn - 1)}${"^" * (match.endColumn - match.startColumn)}")
                    appendLine()
                }
                
                if (fileIndex < results.size - 1) {
                    appendLine()
                }
            }
        }
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
     * 字符串重复操作符
     */
    private operator fun String.times(count: Int): String {
        return repeat(count)
    }
}

/**
 * 搜索结果
 */
data class SearchResult(
    val file: File,
    val relativePath: String,
    val matches: List<MatchInfo>
)

/**
 * 匹配信息
 */
data class MatchInfo(
    val lineNumber: Int,
    val startColumn: Int,
    val endColumn: Int,
    val lineContent: String,
    val matchedText: String
)


