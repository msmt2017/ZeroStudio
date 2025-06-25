package android.zero.mcp.ui

import android.zero.mcp.monitor.ToolErrorLogManager
import android.zero.mcp.monitor.ToolStatsTracker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * MCP 工具使用统计 + 错误日志显示
 */
@Composable
fun McpStatsLogPanelUi() {
    val stats = remember { mutableStateListOf<Map<String, Any>>() }
    val errors = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        while (true) {
            stats.clear()
            stats.addAll(ToolStatsTracker.getStats())
            errors.clear()
            errors.addAll(ToolErrorLogManager.getAll())
            kotlinx.coroutines.delay(3000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("📊 工具调用统计", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(stats) { row ->
                Text("🔧 ${row["method"]} → 次数: ${row["calls"]}, 错误: ${row["errors"]}", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("❌ 最近错误日志", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(errors) { line ->
                Text(line, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
