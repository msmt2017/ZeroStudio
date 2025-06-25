package android.zero.mcp.monitor

import java.util.concurrent.ConcurrentHashMap

/**
 * 工具调用统计器：记录每个工具调用次数和错误数
 */
object ToolStatsTracker {

    private val invokeCounts = ConcurrentHashMap<String, Int>()
    private val errorCounts = ConcurrentHashMap<String, Int>()

    fun recordInvocation(method: String) {
        invokeCounts[method] = invokeCounts.getOrDefault(method, 0) + 1
    }

    fun recordError(method: String) {
        errorCounts[method] = errorCounts.getOrDefault(method, 0) + 1
    }

    fun getStats(): List<Map<String, Any>> {
        val all = (invokeCounts.keys + errorCounts.keys).distinct()
        return all.map { method ->
            mapOf(
                "method" to method,
                "calls" to (invokeCounts[method] ?: 0),
                "errors" to (errorCounts[method] ?: 0)
            )
        }.sortedByDescending { it["calls"] as Int }
    }
}
