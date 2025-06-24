package android.zero.mcp.tools.workspace

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tool: @File:workspace:getinstallApk
 * 功能：安装指定变体的APK文件
 * 参数：variant=变体名称(debug/release)
 * 示例：@File:workspace:getinstallApk=debug
 */
class InstallApkTool(
    private val getApkFile: (String) -> File?,
    private val launchInstaller: (Uri) -> Unit
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val variant = request.params?.get("variant") 
                ?: return@withContext McpResponse.error("缺少 variant 参数", request.id)
            
            if (variant !in setOf("debug", "release")) {
                return@withContext McpResponse.error("无效的变体: $variant (应为 debug 或 release)", request.id)
            }
            
            val apkFile = getApkFile(variant)
            if (apkFile == null || !apkFile.exists()) {
                return@withContext McpResponse.error("APK文件不存在: $variant 变体", request.id)
            }
            
            val result = installApk(apkFile, variant)
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("InstallApkTool error: ${e.message}")
            McpResponse.error("安装APK失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 安装APK文件
     */
    private fun installApk(apkFile: File, variant: String): String {
        return try {
            // 检查APK文件是否已签名
            if (!isApkSigned(apkFile)) {
                return buildString {
                    appendLine("❌ APK未签名，无法安装")
                    appendLine("文件: ${apkFile.absolutePath}")
                    appendLine("变体: $variant")
                    appendLine("大小: ${formatFileSize(apkFile.length())}")
                    appendLine()
                    appendLine("💡 提示: 请先构建已签名的APK")
                }
            }
            
            // 创建安装Intent
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.fromFile(apkFile)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/vnd.android.package-archive"
            }
            
            // 启动安装器
            launchInstaller(Uri.fromFile(apkFile))
            
            buildString {
                appendLine("✅ APK安装请求已发送")
                appendLine("文件: ${apkFile.absolutePath}")
                appendLine("变体: $variant")
                appendLine("大小: ${formatFileSize(apkFile.length())}")
                appendLine("签名状态: 已签名")
                appendLine()
                appendLine("📱 请在设备上确认安装")
            }
            
        } catch (e: Exception) {
            "❌ 启动安装器失败: ${e.message}"
        }
    }
    
    /**
     * 检查APK是否已签名
     */
    private fun isApkSigned(apkFile: File): Boolean {
        return try {
            // 简单的签名检查：尝试读取APK的META-INF目录
            val zipFile = java.util.zip.ZipFile(apkFile)
            val hasSignature = zipFile.entries().asSequence().any { entry ->
                entry.name.startsWith("META-INF/") && 
                (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC"))
            }
            zipFile.close()
            hasSignature
        } catch (e: Exception) {
            McpServerLog.log("检查APK签名失败: ${e.message}")
            false
        }
    }
    
    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
} 