package android.zero.mcp.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.zero.mcp.storage.McpSettingsExporter
import android.zero.mcp.storage.McpSettingsImporter
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun McpSettingsImportExportScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var resultText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            val inputStream = ctx.contentResolver.openInputStream(it)
            val tmp = File(ctx.cacheDir, "imported_mcp.json")
            inputStream?.use { ins -> tmp.writeBytes(ins.readBytes()) }
            scope.launch {
                val result = McpSettingsImporter.importFromFile(ctx, tmp)
                resultText = result.fold(
                    onSuccess = { "✅ 导入成功" },
                    onFailure = { "❌ 导入失败: ${it.message}" }
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("设置导入导出", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            scope.launch {
                val result = McpSettingsExporter.exportToFile(ctx)
                resultText = result.fold(
                    onSuccess = { "✅ 已导出至: ${it.absolutePath}" },
                    onFailure = { "❌ 导出失败: ${it.message}" }
                )
            }
        }) {
            Text("导出配置为 JSON")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(onClick = {
            filePicker.launch("application/json")
        }) {
            Text("从文件导入配置")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(resultText, style = MaterialTheme.typography.bodyMedium)
    }
}
