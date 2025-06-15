package android.zero.mcp.tools.shell

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.termux.shell.TermuxShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @shell:#execute=command
 * 功能：执行终端命令，通过 TermuxShellExecutor 执行 termux 当前会话
 */
class ExecuteShellTool(
    private val termuxShellExecutor: TermuxShellExecutor
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val cmd = request.params?.get("execute")?.trim()
            ?: return@withContext McpResponse.error("缺少 execute 参数（命令行）", request.id)

        return@withContext try {
            val output = termuxShellExecutor.executeShellCommand(cmd)
            McpResponse.success(request.id, output.trim())
        } catch (e: Exception) {
            McpServerLog.log("ExecuteShellTool error: ${e.message}")
            McpResponse.error("终端命令执行失败: ${e.message}", request.id)
        }
    }
}
