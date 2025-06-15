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
import android.zero.mcp.policy.ToolExecutionPolicy

@Composable
fun ToolPolicyScreen() {
    val tools = ToolExecutionPolicy.dumpPolicies().split("\n\n")

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("工具执行策略", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(tools) { info ->
                Text(text = info, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
            }
        }
    }
}
