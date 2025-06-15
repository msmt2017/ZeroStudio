package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Tool: @File:search
 * 参数：path=搜索路径, content=搜索文本
 */
class FileSearchTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val relPath = request.params?.get("path") ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
        val keyword = request.params["content"] ?: return@withContext McpResponse.error("缺少 content 参数", request.id)

        val dir = File(workspaceRoot, relPath)
        if (!dir.exists() || !dir.isDirectory) {
            return@withContext McpResponse.error("无效路径: ${dir.path}", request.id)
        }

        val results = dir.walkTopDown()
            .filter { it.isFile && it.readText().contains(keyword) }
            .map { it.relativeTo(workspaceRoot).path }
            .toList()

        McpResponse.success(request.id, results.joinToString("\n"))
    }
}


