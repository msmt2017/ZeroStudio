package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @TabFile:#insertLine
 * 参数：insertLine=行号, content=插入内容
 */
class InsertLineTool(
    private val getEditorContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val line = request.params?.get("insertLine")?.toIntOrNull()
            ?: return@withContext McpResponse.error("缺少 insertLine 参数，或格式非法", request.id)
        val insertText = request.params["content"] ?: return@withContext McpResponse.error("缺少 content 参数", request.id)

        val content = getEditorContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)

        return@withContext try {
            if (line !in 0..content.getLineCount()) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            content.insert(line, 0, insertText + "\n")
            McpResponse.success(request.id, "已插入内容到第 $line 行")
        } catch (e: Exception) {
            McpServerLog.log("InsertLineTool error: ${e.message}")
            McpResponse.error("插入失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:#replaceLine
 * 参数：replaceLine=行号, content=新内容
 */
class ReplaceLineTool(
    private val getEditorContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val line = request.params?.get("replaceLine")?.toIntOrNull()
            ?: return@withContext McpResponse.error("缺少 replaceLine 参数，或格式非法", request.id)
        val newText = request.params["content"] ?: return@withContext McpResponse.error("缺少 content 参数", request.id)

        val content = getEditorContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)

        return@withContext try {
            if (line !in 0 until content.getLineCount()) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            val start = content.getLineOffset(line)
            val end = start + content.getLine(line).length
            content.delete(start, end)
            content.insert(start, newText + "\n")
            McpResponse.success(request.id, "已替换第 $line 行内容")
        } catch (e: Exception) {
            McpServerLog.log("ReplaceLineTool error: ${e.message}")
            McpResponse.error("替换失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @TabFile:#deleteLine
 * 参数：deleteLine=行号
 */
class DeleteLineTool(
    private val getEditorContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val line = request.params?.get("deleteLine")?.toIntOrNull()
            ?: return@withContext McpResponse.error("缺少 deleteLine 参数，或格式非法", request.id)

        val content = getEditorContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)

        return@withContext try {
            if (line !in 0 until content.getLineCount()) {
                return@withContext McpResponse.error("行号超出范围: $line", request.id)
            }
            val start = content.getLineOffset(line)
            val end = start + content.getLine(line).length + 1
            content.delete(start, end)
            McpResponse.success(request.id, "已删除第 $line 行")
        } catch (e: Exception) {
            McpServerLog.log("DeleteLineTool error: ${e.message}")
            McpResponse.error("删除失败: ${e.message}", request.id)
        }
    }
}
