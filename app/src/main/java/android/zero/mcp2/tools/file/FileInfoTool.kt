
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
 * Tool: @File:info
 * 参数：path=相对路径（可为文件或目录）
 */
class FileInfoTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val path = request.params?.get("path") ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
        val target = File(workspaceRoot, path)
        if (!target.exists()) return@withContext McpResponse.error("目标不存在: $path", request.id)

        return@withContext try {
            val info = buildString {
                append("路径: ${target.absolutePath}\n")
                append("类型: ${if (target.isDirectory) "目录" else "文件"}\n")
                append("大小: ${target.length()} 字节\n")
                append("最后修改: ${target.lastModified()}\n")

                if (target.isFile) {
                    val bytes = target.readBytes()
                    append("MD5: ${bytes.digest("MD5")}\n")
                    append("SHA-1: ${bytes.digest("SHA-1")}\n")
                    append("CRC32: ${bytes.crc32()}\n")
                }
            }
            McpResponse.success(request.id, info)
        } catch (e: Exception) {
            McpServerLog.log("FileInfoTool error: ${e.message}")
            McpResponse.error("信息获取失败: ${e.message}", request.id)
        }
    }

    private fun ByteArray.digest(alg: String): String =
        MessageDigest.getInstance(alg).digest(this).joinToString("") { "%02x".format(it) }

    private fun ByteArray.crc32(): String =
        CRC32().apply { update(this@crc32) }.value.toString()
}
