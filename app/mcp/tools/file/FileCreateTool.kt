

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
 * Tool: @File:create
 * 参数：path=相对路径, folder=是否创建文件夹(true/false), files=文件名（可选）
 */
class FileCreateTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val path = request.params?.get("path") ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
        val isFolder = request.params["folder"]?.toBoolean() ?: false
        val fileName = request.params["files"]

        return@withContext try {
            val target = if (fileName != null) File(File(workspaceRoot, path), fileName) else File(workspaceRoot, path)
            if (target.exists()) return@withContext McpResponse.error("文件或目录已存在: ${target.path}", request.id)
            if (isFolder) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                target.createNewFile()
            }
            McpResponse.success(request.id, "创建成功: ${target.path}")
        } catch (e: Exception) {
            McpServerLog.log("FileCreateTool error: ${e.message}")
            McpResponse.error("创建失败: ${e.message}", request.id)
        }
    }
}