package android.zero.mcp.tools.shell

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.termux.shell.TermuxShellExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool: @shell:execute
 * 功能：通过获取代理或者创建当前会话然后在当前会话执行运行终端命令行（executeshell）
 * 参数：execute=终端命令
 * 示例：@shell:execute=ls -la
 * 示例：@shell:execute=pwd
 */
class ExecuteShellTool(
    private val termuxShellExecutor: TermuxShellExecutor
) : McpTool {

    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val command = request.params?.get("execute")?.trim()
                ?: return@withContext McpResponse.error("缺少 execute 参数（命令行）", request.id)
            
            if (command.isBlank()) {
                return@withContext McpResponse.error("命令不能为空", request.id)
            }
            
            McpServerLog.log("执行终端命令: $command")
            
            // 通过TermuxShellExecutor执行命令
            val output = termuxShellExecutor.executeShellCommand(command)
            
            val result = buildString {
                appendLine("✅ 终端命令执行成功")
                appendLine("命令: $command")
                appendLine("执行状态: 完成")
                appendLine()
                appendLine("📋 命令输出:")
                if (output.isNotBlank()) {
                    appendLine(output.trim())
                } else {
                    appendLine("(命令执行完成，无输出)")
                }
                appendLine()
                appendLine("💡 提示:")
                appendLine("- 命令在Termux会话中执行")
                appendLine("- 支持所有标准Linux命令")
                appendLine("- 可以在Termux终端查看完整输出")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("ExecuteShellTool error: ${e.message}")
            McpResponse.error("终端命令执行失败: ${e.message}", request.id)
        }
    }
}
