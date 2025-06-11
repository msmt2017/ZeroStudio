// 文件路径：app/src/main/java/android/zero/mcp/ExecutionEngine.kt
package android.zero.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 执行端：提供底层执行能力，如 Shell、文件、Gradle、网络等。
 */
object ExecutionEngine {

    /** 执行 shell 命令并返回完整输出 */
    suspend fun executeShell(command: String): String = withContext(Dispatchers.IO) {
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val output = reader.readText()
            reader.close()
            proc.waitFor()
            output
        } catch (e: Exception) {
            "Shell execution failed: ${e.message}"
        }
    }

    /** 执行文件操作：示例新建文件 */
    fun createFile(path: String, isDirectory: Boolean): Boolean {
        val f = java.io.File(path)
        return try {
            if (isDirectory) {
                f.mkdirs()
            } else {
                f.parentFile?.mkdirs()
                f.createNewFile()
            }
        } catch (_: Exception) {
            false
        }
    }

    /** 写入文件内容 */
    fun writeFile(path: String, content: String): Boolean {
        return try {
            val f = java.io.File(path)
            if (!f.exists()) {
                f.parentFile?.mkdirs()
                f.createNewFile()
            }
            f.writeText(content)
            true
        } catch (_: Exception) {
            false
        }
    }

    // 更多执行方法可按需添加
}
