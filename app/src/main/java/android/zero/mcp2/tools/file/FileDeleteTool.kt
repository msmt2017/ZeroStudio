package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tool: @File:delete
 * 参数：path=相对路径（文件或目录）
 */
class FileDeleteTool(
    private val workspaceRoot: File
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val path = request.params?.get("path") ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
        val file = File(workspaceRoot, path)

        if (!file.exists()) return@withContext McpResponse.error("文件不存在: $path", request.id)

        return@withContext try {
            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (deleted) {
                McpResponse.success(request.id, "已删除: ${file.path}")
            } else {
                McpResponse.error("删除失败: ${file.path}", request.id)
            }
        } catch (e: Exception) {
            McpServerLog.log("FileDeleteTool error: ${e.message}")
            McpResponse.error("删除异常: ${e.message}", request.id)
        }
    }
}
