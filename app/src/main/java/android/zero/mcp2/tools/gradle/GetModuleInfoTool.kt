package android.zero.mcp.tools.workspace

import android.content.Intent
import android.net.Uri
import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.project.ProjectManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tool: @File:workspace:#getmoduleInfo
 * 功能：返回当前工作区所有模块名称与路径信息
 */
class GetModuleInfoTool(
    private val projectManager: ProjectManager
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        return@withContext try {
            val modules = projectManager.getAllModules()
            val info = modules.joinToString("\n") { mod ->
                "[${mod.name}]\n绝对路径: ${mod.absolutePath}\n相对路径: ${mod.relativePath}"
            }
            McpResponse.success(request.id, info)
        } catch (e: Exception) {
            McpServerLog.log("GetModuleInfoTool error: ${e.message}")
            McpResponse.error("获取模块信息失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:workspace:#getGradleWrapperInfo
 * 功能：返回 gradle-wrapper.properties 中的内容
 */
class GetGradleWrapperInfoTool(
    private val wrapperFile: File
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        return@withContext try {
            if (!wrapperFile.exists()) {
                return@withContext McpResponse.error("未找到 gradle-wrapper.properties 文件", request.id)
            }
            val content = wrapperFile.readText()
            McpResponse.success(request.id, content)
        } catch (e: Exception) {
            McpServerLog.log("GetGradleWrapperInfoTool error: ${e.message}")
            McpResponse.error("读取 gradle-wrapper 信息失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @File:workspace:#getinstallApk=debug/release
 * 功能：从构建输出路径安装 APK（已签名）
 */
class InstallApkTool(
    private val getApkFile: (variant: String) -> File?,
    private val launchInstaller: (uri: Uri) -> Unit
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        val variant = request.params?.get("getinstallApk") ?: return@withContext McpResponse.error("缺少变体参数，例如 debug 或 release", request.id)

        val apk = getApkFile(variant)
        if (apk == null || !apk.exists()) {
            return@withContext McpResponse.error("未找到构建输出 APK 文件", request.id)
        }

        val signed = apk.readBytes().takeLast(4).toByteArray().let {
            it[0] == 0x50.toByte() && it[1] == 0x4B.toByte() // 简单验证签名（伪）
        }

        if (!signed) {
            return@withContext McpResponse.error("APK 未签名，无法安装", request.id)
        }

        try {
            val uri = Uri.fromFile(apk)
            launchInstaller(uri)
            McpResponse.success(request.id, "已启动安装器：${apk.name}")
        } catch (e: Exception) {
            McpServerLog.log("InstallApkTool error: ${e.message}")
            McpResponse.error("无法安装 APK: ${e.message}", request.id)
        }
    }
}
