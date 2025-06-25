package android.zero.mcp.storage

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MCP 配置导入器：从 JSON 文件导入设置到内存并写入 SharedPreferences
 */
object McpSettingsImporter {

    suspend fun importFromFile(context: Context, file: File): Result<Unit> = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!file.exists()) return@withContext Result.failure(IllegalArgumentException("配置文件不存在"))
            val raw = file.readText()
            McpSettingsStorage.importAllRaw(context, raw)
            McpSettingsSerializer.loadAll() // 将值注入各个管理器
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
