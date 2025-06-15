package android.zero.mcp.tools.workspace

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tool: @File:workspace:#ModifyGradleVersion=8.5,all
 * 功能：修改 gradle-wrapper.properties 中的 distributionUrl 字段为指定版本
 */
class ModifyGradleVersionTool(
    private val wrapperFile: File
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val version = request.params?.get("ModifyGradleVersion")?.trim()
            ?: return@withContext McpResponse.error("缺少 ModifyGradleVersion 参数，例如 8.5,all", request.id)

        val (gradleVer, type) = version.split(',').map { it.trim() }.let {
            if (it.size == 2) it[0] to it[1] else return@withContext McpResponse.error("格式错误，应为: 版本,类型（如 8.5,all）", request.id)
        }

        return@withContext try {
            val original = wrapperFile.readText()
            val regex = Regex("distributionUrl=.*?gradle-.*?-(bin|all)\\.zip")
            val replacement = "distributionUrl=https://services.gradle.org/distributions/gradle-$gradleVer-$type.zip"
            val modified = original.replace(regex, replacement)
            wrapperFile.writeText(modified)
            McpResponse.success(request.id, "gradle-wrapper.properties 已更新为 gradle-$gradleVer-$type")
        } catch (e: Exception) {
            McpServerLog.log("ModifyGradleVersionTool error: ${e.message}")
            McpResponse.error("修改 gradle-wrapper 失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:workspace:#GetModuleSrcFileList=app
 * 功能：列出指定模块 /src 下所有文件名（排除非 src 内容）
 */
class GetModuleSrcFileListTool(
    private val workspaceRoot: File
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val module = request.params?.get("GetModuleSrcFileList")?.trim()
            ?: return@withContext McpResponse.error("缺少模块名参数", request.id)

        val srcDir = File(workspaceRoot, "$module/src")
        if (!srcDir.exists() || !srcDir.isDirectory) {
            return@withContext McpResponse.error("模块目录不存在或不是有效目录: ${srcDir.absolutePath}", request.id)
        }

        val result = srcDir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(workspaceRoot).path }
            .joinToString("\n")

        McpResponse.success(request.id, result)
    }
}
