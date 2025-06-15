package android.zero.mcp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.zero.mcp.McpServerLog

class McpServerLogFragment : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LogScreen()
                }
            }
        }
    }
}

@Composable
fun LogScreen() {
    val logs = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        logs.addAll(McpServerLog.getLogs())
    }

    Column(modifier = Modifier.padding(12.dp)) {
        Text("🧠 MCP Server 日志输出", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs.reversed()) { log ->
                Text(text = log, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(2.dp))
            }
        }
    }
}
