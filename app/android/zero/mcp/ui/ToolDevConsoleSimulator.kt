package android.zero.mcp.ui

import android.zero.mcp.McpRequest
import android.zero.mcp.client.McpClientImpl
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 开发者控制台模拟器：发送 JSON-RPC 请求到 MCP Server
 */
@Composable
fun ToolDevConsoleSimulator() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember { McpClientImpl("http://127.0.0.1:11583") }

    var method by remember { mutableStateOf("") }
    var paramJson by remember { mutableStateOf("{") }
    var responseText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🧪 AI 工具请求模拟器", style = MaterialTheme.typography.titleLarge)

        Spacer(modifier = Modifier.height(12.dp))
        TextField(value = method, onValueChange = { method = it }, label = { Text("工具 method 名") })
        Spacer(modifier = Modifier.height(8.dp))

        Text("参数 JSON:", style = MaterialTheme.typography.labelLarge)
        BasicTextField(
            value = paramJson,
            onValueChange = { paramJson = it },
            modifier = Modifier.fillMaxWidth().height(100.dp).padding(4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            scope.launch {
                val req = McpRequest(
                    id = "dev-${System.currentTimeMillis()}",
                    method = method,
                    params = runCatching { JSONObject(paramJson).toMap().mapValues { it.value.toString() }.toMutableMap() }.getOrElse { mutableMapOf() }
                )
                val result = client.send(req)
                responseText = result
            }
        }) {
            Text("发送请求")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("响应：")
        Text(responseText, style = MaterialTheme.typography.bodySmall)
    }
}
