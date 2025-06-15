package android.zero.mcp.monitor

import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 记录 MCP 工具执行异常详情
 */
object ToolErrorLogManager {

    private val logs = ConcurrentLinkedQueue<String>()
    private const val MAX_LOGS = 200

    fun record(errorMsg: String) {
        logs += "[${System.currentTimeMillis()}] $errorMsg"
        if (logs.size > MAX_LOGS) logs.poll()
    }

    fun getAll(): List<String> = logs.toList().reversed()

    fun clear() = logs.clear()
}
