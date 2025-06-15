package android.zero.mcp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.zero.mcp.meta.McpToolCategoryManager

@Composable
fun ToolCategoryScreen() {
    val allCategories = McpToolCategoryManager.getAllCategories().sorted()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Text("工具分类总览", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(allCategories) { category ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text("📂 $category", style = MaterialTheme.typography.titleMedium)
                    val tools = McpToolCategoryManager.getToolsByCategory(category)
                    tools.forEach {
                        Text("- $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        }
    }
}
