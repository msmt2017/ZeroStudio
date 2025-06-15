package android.zero.mcp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun McpAiControlPanel() {
    var maxTokens by remember { mutableStateOf("2048") }
    var temperature by remember { mutableStateOf("0.7") }
    var model by remember { mutableStateOf("gpt-4") }
    var promptPreview by remember { mutableStateOf("你想要做什么？") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("AI 控制面板", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))

        TextField(
            value = model,
            onValueChange = { model = it },
            label = { Text("模型名称 (如 gpt-4/claude) ") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = maxTokens,
            onValueChange = { maxTokens = it },
            label = { Text("最大 Token 限制") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = temperature,
            onValueChange = { temperature = it },
            label = { Text("Temperature (0~1)") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = promptPreview,
            onValueChange = { promptPreview = it },
            label = { Text("预设 Prompt 模板") },
            modifier = Modifier.fillMaxWidth().height(100.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            // 可绑定实际 AI 参数注入逻辑
        }) {
            Text("应用设置")
        }
    }
}
