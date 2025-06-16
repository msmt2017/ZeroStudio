package android.zero.mcp.ui

import android.zero.mcp.sse.SseSessionMonitor
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 可视化显示当前 SSE 会话连接状态
 */
@Composable
fun SseMonitorPanelUi() {
    val sessions = remember { mutableStateListOf<Map<String, Any>>() }
    val count = remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            sessions.clear()
            sessions.addAll(SseSessionMonitor.getSnapshot())
            count.value = SseSessionMonitor.activeSessionCount()
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔌 SSE 会话监控", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        Text("活跃连接数: ${count.value}", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(12.dp))
        sessions.forEach { s ->
            Text("会话 ${s["sessionId"]}，事件数: ${s["events"]}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
