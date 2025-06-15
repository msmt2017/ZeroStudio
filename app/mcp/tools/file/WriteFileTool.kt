package android.zero.mcp.tools.file

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tool: @File:WriteFile
 * 参数：path=路径, content=文本, writeLine=可选行号（追加在该行）
 */
class WriteFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val filePath = request.params?.get("path") ?: return@withContext McpResponse.error("缺少 path 参数", request.id)
        val content = request.params["content"] ?: return@withContext McpResponse.error("缺少 content 参数", request.id)
        val lineNum = request.params["writeLine"]?.toIntOrNull()

        return@withContext try {
            val file = File(workspaceRoot, filePath)
            if (!file.exists()) file.createNewFile()
            val lines = file.readLines().toMutableList()
            if (lineNum != null && lineNum in 0..lines.size) {
                lines.add(lineNum, content)
                file.writeText(lines.joinToString("\n"))
            } else {
                file.appendText("\n$content")
            }
            McpResponse.success(request.id, "写入成功: ${file.absolutePath}")
        } catch (e: Exception) {
            McpServerLog.log("WriteFileTool error: ${e.message}")
            McpResponse.error("写入失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:Rename
 * 参数：DestinationPath=原始路径, RenameContent=重命名为
 */
class RenameFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val oldPath = request.params?.get("DestinationPath") ?: return@withContext McpResponse.error("缺少 DestinationPath", request.id)
        val newName = request.params["RenameContent"] ?: return@withContext McpResponse.error("缺少 RenameContent", request.id)

        val file = File(workspaceRoot, oldPath)
        val renamed = File(file.parentFile, newName)

        return@withContext try {
            if (!file.exists()) return@withContext McpResponse.error("文件不存在", request.id)
            if (file.renameTo(renamed)) {
                McpResponse.success(request.id, "重命名成功 -> ${renamed.path}")
            } else {
                McpResponse.error("重命名失败", request.id)
            }
        } catch (e: Exception) {
            McpServerLog.log("RenameFileTool error: ${e.message}")
            McpResponse.error("异常: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:move
 * 参数：movePath=源路径, DestinationPath=目标路径
 */
class MoveFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val src = request.params?.get("movePath") ?: return@withContext McpResponse.error("缺少 movePath", request.id)
        val dest = request.params["DestinationPath"] ?: return@withContext McpResponse.error("缺少 DestinationPath", request.id)

        val srcFile = File(workspaceRoot, src)
        val destFile = File(workspaceRoot, dest, srcFile.name)

        return@withContext try {
            srcFile.copyTo(destFile, overwrite = true)
            srcFile.delete()
            McpResponse.success(request.id, "移动成功 -> ${destFile.path}")
        } catch (e: Exception) {
            McpServerLog.log("MoveFileTool error: ${e.message}")
            McpResponse.error("移动失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:copy
 * 参数：copyPath=源路径, DestinationPath=目标路径
 */
class CopyFileTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val src = request.params?.get("copyPath") ?: return@withContext McpResponse.error("缺少 copyPath", request.id)
        val dest = request.params["DestinationPath"] ?: return@withContext McpResponse.error("缺少 DestinationPath", request.id)

        val srcFile = File(workspaceRoot, src)
        val destFile = File(workspaceRoot, dest, srcFile.name)

        return@withContext try {
            srcFile.copyTo(destFile, overwrite = true)
            McpResponse.success(request.id, "复制成功 -> ${destFile.path}")
        } catch (e: Exception) {
            McpServerLog.log("CopyFileTool error: ${e.message}")
            McpResponse.error("复制失败: ${e.message}", request.id)
        }
    }
}
