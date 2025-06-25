package android.zero.mcp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.zero.mcp.prompt.PromptTemplateManager

@Composable
fun ToolPromptScreen() {
    val all = PromptTemplateManager.dumpAll().split("\n\n")

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("工具提示词模板总览", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(all) {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(6.dp))
            }
        }
    }
}
