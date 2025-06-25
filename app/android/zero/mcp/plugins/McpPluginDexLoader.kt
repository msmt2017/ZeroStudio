package android.zero.mcp.plugins

import android.content.Context
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import dalvik.system.DexClassLoader
import java.io.File

/**
 * MCP 插件 Dex 加载器：支持从外部 .jar/.dex 文件动态加载 Tool
 */
object McpPluginDexLoader {

    fun loadPluginTool(context: Context, dexPath: String, className: String): McpTool? {
        return try {
            val optimizedDir = File(context.cacheDir, "dex")
            optimizedDir.mkdirs()

            val loader = DexClassLoader(
                dexPath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )

            val clazz = loader.loadClass(className)
            val instance = clazz.getDeclaredConstructor().newInstance()
            if (instance is McpTool) {
                McpServerLog.log("已成功从插件加载工具: $className")
                instance
            } else {
                McpServerLog.log("插件类 $className 不是 McpTool 实现")
                null
            }
        } catch (e: Exception) {
            McpServerLog.log("插件加载失败: ${e.message}")
            null
        }
    }
}