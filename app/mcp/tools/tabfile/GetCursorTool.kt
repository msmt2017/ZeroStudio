package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @TabFile:#getCursor
 * 功能：获取光标所在行的完整内容返回给 AI
 */
class GetCursorTool(
    private val getCursor: () -> Cursor?,
    private val getContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val cursor = getCursor() ?: return@withContext McpResponse.error("未找到光标位置", request.id)
        val content = getContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)

        val line = cursor.leftLine
        if (line !in 0 until content.getLineCount()) {
            return@withContext McpResponse.error("光标行越界: $line", request.id)
        }

        val lineText = content.getLineString(line)
        McpResponse.success(request.id, lineText)
    }
} 
