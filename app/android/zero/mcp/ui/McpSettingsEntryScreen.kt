package android.zero.mcp.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun McpSettingsEntryScreen() {
    val ctx = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("MCP 设置面板", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            ctx.startActivity(Intent(ctx, McpServerLogFragment::class.java))
        }) { Text("查看日志输出") }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            ctx.startActivity(Intent(ctx, Class.forName("android.zero.mcp.ui.ToolPermissionScreen")))
        }) { Text("权限管理") }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            ctx.startActivity(Intent(ctx, Class.forName("android.zero.mcp.ui.ToolCategoryScreen")))
        }) { Text("分类总览") }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            ctx.startActivity(Intent(ctx, Class.forName("android.zero.mcp.ui.ToolPromptScreen")))
        }) { Text("提示词模板") }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = {
            ctx.startActivity(Intent(ctx, Class.forName("android.zero.mcp.ui.ToolPolicyScreen")))
        }) { Text("执行策略") }
    }
}
