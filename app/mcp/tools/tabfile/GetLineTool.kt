package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Cursor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: 获取指定行范围内容
 * 参数：getLine=5 或 getLine=5-10
 */
class GetLineTool(private val getCurrentContent: () -> Content?) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val rangeRaw = request.params?.get("getLine") ?: return@withContext McpResponse.error("Missing #getLine=range", request.id)
        val content = getCurrentContent() ?: return@withContext McpResponse.error("No active file", request.id)

        try {
            val lines = parseRange(rangeRaw)
            val output = buildString {
                for (line in lines) {
                    if (line in 0 until content.getLineCount()) {
                        append(content.getLineString(line)).append("\n")
                    }
                }
            }
            McpResponse.success(request.id, output.trimEnd())
        } catch (e: Exception) {
            McpServerLog.log("GetLineTool error: ${e.message}")
            McpResponse.error("Invalid line range", request.id)
        }
    }

    private fun parseRange(input: String): IntRange {
        return if ("-" in input) {
            val (start, end) = input.split("-").map { it.trim().toInt() }
            start..end
        } else {
            val line = input.trim().toInt()
            line..line
        }
    }
}

/**
 * Tool: 获取光标所在行内容
 */
class GetCursorLineTool(
    private val getCursor: () -> Cursor?,
    private val getCurrentContent: () -> Content?
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val cursor = getCursor() ?: return@withContext McpResponse.error("No cursor found", request.id)
        val content = getCurrentContent() ?: return@withContext McpResponse.error("No active file", request.id)

        val line = cursor.leftLine
        val text = content.getLineString(line)
        McpResponse.success(request.id, text)
    }
}

/**
 * Tool: 获取当前文件完整内容
 */
class GetFullFileTool(private val getCurrentContent: () -> Content?) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val content = getCurrentContent() ?: return@withContext McpResponse.error("No active file", request.id)
        val output = buildString {
            for (i in 0 until content.getLineCount()) {
                append(content.getLineString(i)).append("\n")
            }
        }
        McpResponse.success(request.id, output.trimEnd())
    }
}

/**
 * Tool: 搜索当前文件内容中是否包含指定关键字
 * 参数：searchTabFile=关键字
 */
class SearchTabFileTool(private val getCurrentContent: () -> Content?) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val keyword = request.params?.get("searchTabFile")?.trim()
            ?: return@withContext McpResponse.error("Missing #searchTabFile keyword", request.id)

        val content = getCurrentContent() ?: return@withContext McpResponse.error("No active file", request.id)
        val matched = mutableListOf<String>()
        for (i in 0 until content.getLineCount()) {
            val lineText = content.getLineString(i)
            if (lineText.contains(keyword, ignoreCase = true)) {
                matched += "Line ${i + 1}: $lineText"
            }
        }
        McpResponse.success(request.id, matched.joinToString("\n"))
    }
}
