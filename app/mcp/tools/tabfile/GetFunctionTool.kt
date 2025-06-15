package android.zero.mcp.tools.tabfile

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @TabFile:#getFunction=functionName
 * 功能：获取当前文件中指定函数名的定义体，支持 Java/Kotlin
 */
class GetFunctionTool(
    private val getContent: () -> Content?
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.Default) {
        val functionName = request.params?.get("getFunction")?.trim()
            ?: return@withContext McpResponse.error("缺少 getFunction 参数", request.id)

        val content = getContent() ?: return@withContext McpResponse.error("未打开任何文件", request.id)

        try {
            val totalLines = content.getLineCount()
            var matchLine = -1

            // 先找到函数声明行
            for (i in 0 until totalLines) {
                val line = content.getLineString(i)
                if (line.contains(functionName) &&
                    (line.contains("fun ") || line.contains("void ") || line.contains("public ") || line.contains("private "))) {
                    matchLine = i
                    break
                }
            }

            if (matchLine == -1) {
                return@withContext McpResponse.error("未找到函数声明：$functionName", request.id)
            }

            // 向后读取直到括号闭合大致匹配
            val result = StringBuilder()
            var braceDepth = 0
            var started = false
            for (i in matchLine until totalLines) {
                val line = content.getLineString(i)
                result.append(line).append("\n")
                braceDepth += line.count { it == '{' }
                braceDepth -= line.count { it == '}' }
                if (!started && line.contains("{")) started = true
                if (started && braceDepth <= 0) break
            }

            McpResponse.success(request.id, result.toString().trimEnd())
        } catch (e: Exception) {
            McpServerLog.log("GetFunctionTool error: ${e.message}")
            McpResponse.error("函数提取出错：${e.message}", request.id)
        }
    }
}
