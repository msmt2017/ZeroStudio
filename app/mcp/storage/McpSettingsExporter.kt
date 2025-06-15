package android.zero.mcp.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MCP 配置导出器：将所有设置导出为 JSON 文件
 */
object McpSettingsExporter {

    suspend fun exportToFile(context: Context, fileName: String = "mcp_settings_backup.json"): Result<File> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allPrefs = McpSettingsStorage.getAllRaw()
            val output = File(context.filesDir, fileName)
            output.writeText(allPrefs)
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
