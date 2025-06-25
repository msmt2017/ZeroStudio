package android.zero.mcp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.zero.mcp.security.McpToolPermissionManager

@Composable
fun ToolPermissionScreen() {
    val allowed = remember { mutableStateListOf<String>() }
    val denied = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        allowed.clear()
        denied.clear()
        allowed.addAll(McpToolPermissionManager.listAllowed())
        denied.addAll(McpToolPermissionManager.listDenied())
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("工具权限管理", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(12.dp))
        LazyColumn {
            val allTools = (allowed + denied).distinct().sorted()
            items(allTools) { tool ->
                val isAllowed = remember { mutableStateOf(tool in allowed) }
                Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                    Checkbox(
                        checked = isAllowed.value,
                        onCheckedChange = {
                            isAllowed.value = it
                            if (it) McpToolPermissionManager.allow(tool) else McpToolPermissionManager.deny(tool)
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tool)
                }
            }
        }
    }
}
