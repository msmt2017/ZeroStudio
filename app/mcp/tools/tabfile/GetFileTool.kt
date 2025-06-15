package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @TabFile:#getFile
 * 功能：返回当前打开文件的完整内容
 */
class GetFileTool(
    private val getContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val content = getContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)
        val result = buildString {
            for (i in 0 until content.getLineCount()) {
                append(content.getLineString(i)).append("\n")
            }
        }
        McpResponse.success(request.id, result.trimEnd())
    }
}
