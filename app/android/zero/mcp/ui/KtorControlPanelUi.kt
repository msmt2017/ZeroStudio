package android.zero.mcp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.ktor.server.engine.*

/**
 * 可视化控制面板（Compose）：控制 Ktor 启动/停止 + 显示状态
 */
@Composable
fun KtorControlPanelUi(server: ApplicationEngine?) {
    var running by remember { mutableStateOf(server?.environment?.monitor?.isRunning ?: false) }
    var statusText by remember { mutableStateOf("Ktor 状态未知") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Ktor 控制中心", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Text("当前状态: ${if (running) "🟢 运行中" else "🔴 未启动"}", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Button(onClick = {
                if (!running) {
                    server?.start()
                    running = true
                    statusText = "✅ 启动成功"
                }
            }) {
                Text("启动服务器")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (running) {
                    server?.stop(1000, 2000)
                    running = false
                    statusText = "🛑 已关闭服务器"
                }
            }) {
                Text("停止服务器")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(statusText, style = MaterialTheme.typography.bodySmall)
    }
}
