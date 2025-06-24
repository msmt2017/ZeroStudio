package android.zero.mcp.tools.workspace

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.project.ProjectManager
import com.itsaky.androidide.utils.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

/**
 * Tool: @File:workspace:getmoduleInfo
 * 功能：获取工作区的所有模块信息
 * 示例：@File:workspace:getmoduleInfo
 */
class GetModuleInfoTool(private val projectManager: ProjectManager) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val workspaceRoot = projectManager.getWorkspaceRootDir()
            if (workspaceRoot == null || !workspaceRoot.exists()) {
                return@withContext McpResponse.error("工作区不存在或未初始化", request.id)
            }
            
            val modules = getModulesFromWorkspace(workspaceRoot)
            
            if (modules.isEmpty()) {
                return@withContext McpResponse.success(request.id, "⚠️ 未找到任何模块")
            }
            
            val result = buildString {
                appendLine("📦 工作区模块信息")
                appendLine("工作区路径: ${workspaceRoot.absolutePath}")
                appendLine("模块数量: ${modules.size}")
                appendLine()
                
                modules.forEachIndexed { index, module ->
                    appendLine("📁 模块 ${index + 1}: ${module.name}")
                    appendLine("模块名: ${module.name}")
                    appendLine("绝对路径: ${module.absolutePath}")
                    appendLine("相对路径: ${module.relativePath}")
                    appendLine("类型: ${module.type}")
                    appendLine("构建文件: ${module.buildFile}")
                    appendLine()
                }
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetModuleInfoTool error: ${e.message}")
            McpResponse.error("获取模块信息失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 从工作区获取模块信息
     */
    private fun getModulesFromWorkspace(workspaceRoot: File): List<ModuleInfo> {
        val modules = mutableListOf<ModuleInfo>()
        
        // 检查settings.gradle.kts或settings.gradle
        val settingsGradleKts = File(workspaceRoot, "settings.gradle.kts")
        val settingsGradle = File(workspaceRoot, "settings.gradle")
        
        val settingsFile = when {
            settingsGradleKts.exists() -> settingsGradleKts
            settingsGradle.exists() -> settingsGradle
            else -> null
        }
        
        if (settingsFile != null) {
            try {
                val content = settingsFile.readText(StandardCharsets.UTF_8)
                val includePattern = Pattern.compile("include\\(['\"]([^'\"]+)['\"]\\)")
                val matcher = includePattern.matcher(content)
                
                while (matcher.find()) {
                    val moduleName = matcher.group(1)
                    val moduleDir = File(workspaceRoot, moduleName)
                    if (moduleDir.exists()) {
                        modules.add(createModuleInfo(moduleName, moduleDir, workspaceRoot))
                    }
                }
            } catch (e: Exception) {
                McpServerLog.log("解析settings.gradle失败: ${e.message}")
            }
        }
        
        // 如果没有找到模块，尝试自动检测
        if (modules.isEmpty()) {
            workspaceRoot.listFiles()?.forEach { file ->
                if (file.isDirectory && hasBuildFile(file)) {
                    modules.add(createModuleInfo(file.name, file, workspaceRoot))
                }
            }
        }
        
        return modules.sortedBy { it.name }
    }
    
    /**
     * 检查目录是否有构建文件
     */
    private fun hasBuildFile(dir: File): Boolean {
        return File(dir, "build.gradle").exists() || 
               File(dir, "build.gradle.kts").exists() ||
               File(dir, "pom.xml").exists()
    }
    
    /**
     * 创建模块信息
     */
    private fun createModuleInfo(name: String, moduleDir: File, workspaceRoot: File): ModuleInfo {
        val buildFile = when {
            File(moduleDir, "build.gradle.kts").exists() -> "build.gradle.kts"
            File(moduleDir, "build.gradle").exists() -> "build.gradle"
            File(moduleDir, "pom.xml").exists() -> "pom.xml"
            else -> "未知"
        }
        
        val type = when {
            name == "app" -> "Android Application"
            name.contains("library") || name.contains("lib") -> "Android Library"
            name.contains("test") -> "Test Module"
            else -> "Unknown"
        }
        
        return ModuleInfo(
            name = name,
            absolutePath = moduleDir.absolutePath,
            relativePath = moduleDir.relativeTo(workspaceRoot).path,
            type = type,
            buildFile = buildFile
        )
    }
}

/**
 * Tool: @File:workspace:getGradleWrapperInfo
 * 功能：获取gradle-wrapper.properties文件信息
 * 示例：@File:workspace:getGradleWrapperInfo
 */
class GetGradleWrapperInfoTool(private val gradleWrapperFile: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            if (!gradleWrapperFile.exists()) {
                return@withContext McpResponse.error("gradle-wrapper.properties文件不存在", request.id)
            }
            
            val content = gradleWrapperFile.readText(StandardCharsets.UTF_8)
            
            val result = buildString {
                appendLine("📦 Gradle Wrapper 信息")
                appendLine("文件路径: ${gradleWrapperFile.absolutePath}")
                appendLine("文件大小: ${formatFileSize(gradleWrapperFile.length())}")
                appendLine()
                appendLine("📄 文件内容:")
                append(content)
                appendLine()
                
                // 解析关键信息
                val distributionUrlMatch = Pattern.compile("distributionUrl=([^\\s]+)").matcher(content)
                if (distributionUrlMatch.find()) {
                    val distributionUrl = distributionUrlMatch.group(1)
                    appendLine("🔗 分发URL: $distributionUrl")
                    
                    // 解析版本信息
                    val versionMatch = Pattern.compile("gradle-([\\d.]+)-([\\w]+)\\.zip").matcher(distributionUrl)
                    if (versionMatch.find()) {
                        val version = versionMatch.group(1)
                        val type = versionMatch.group(2)
                        appendLine("📋 Gradle版本: $version")
                        appendLine("📋 分发类型: $type")
                    }
                }
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetGradleWrapperInfoTool error: ${e.message}")
            McpResponse.error("获取Gradle Wrapper信息失败: ${e.message}", request.id)
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

/**
 * Tool: @File:workspace:ModifyGradleVersion
 * 功能：修改gradle-wrapper.properties中的Gradle版本
 * 参数：version=版本号,type=分发类型(all/bin)
 * 示例：@File:workspace:ModifyGradleVersion=8.5,all
 */
class ModifyGradleVersionTool(private val gradleWrapperFile: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val versionParam = request.params?.get("version")
            if (versionParam == null) {
                return@withContext McpResponse.error("缺少 version 参数", request.id)
            }
            
            if (!gradleWrapperFile.exists()) {
                return@withContext McpResponse.error("gradle-wrapper.properties文件不存在", request.id)
            }
            
            val parts = versionParam.split(",")
            val version = parts[0].trim()
            val type = if (parts.size > 1) parts[1].trim() else "all"
            
            if (!isValidVersion(version)) {
                return@withContext McpResponse.error("无效的版本号: $version", request.id)
            }
            
            if (type !in setOf("all", "bin")) {
                return@withContext McpResponse.error("无效的分发类型: $type (应为 all 或 bin)", request.id)
            }
            
            val result = modifyGradleVersion(version, type)
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("ModifyGradleVersionTool error: ${e.message}")
            McpResponse.error("修改Gradle版本失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 修改Gradle版本
     */
    private fun modifyGradleVersion(version: String, type: String): String {
        val content = gradleWrapperFile.readText(StandardCharsets.UTF_8)
        
        // 备份原文件
        val backupFile = File(gradleWrapperFile.parentFile, "gradle-wrapper.properties.backup")
        gradleWrapperFile.copyTo(backupFile, overwrite = true)
        
        // 构建新的分发URL
        val newDistributionUrl = "https\\://services.gradle.org/distributions/gradle-$version-$type.zip"
        
        // 替换分发URL
        val newContent = content.replace(
            Regex("distributionUrl=([^\\s]+)"),
            "distributionUrl=$newDistributionUrl"
        )
        
        // 写入文件
        gradleWrapperFile.writeText(newContent, StandardCharsets.UTF_8)
        
        return buildString {
            appendLine("✅ Gradle版本修改成功")
            appendLine("文件: ${gradleWrapperFile.absolutePath}")
            appendLine("新版本: $version")
            appendLine("分发类型: $type")
            appendLine("新URL: $newDistributionUrl")
            appendLine("备份文件: ${backupFile.absolutePath}")
        }
    }
    
    /**
     * 验证版本号格式
     */
    private fun isValidVersion(version: String): Boolean {
        return Pattern.compile("^\\d+\\.\\d+(\\.\\d+)?$").matcher(version).matches()
    }
}

/**
 * Tool: @File:workspace:GetModuleSrcFileList
 * 功能：获取指定模块src目录下的所有文件和文件夹列表
 * 参数：moduleName=模块名称
 * 示例：@File:workspace:GetModuleSrcFileList=app
 */
class GetModuleSrcFileListTool(private val workspaceRoot: File) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            val moduleName = request.params?.get("moduleName")
                ?: return@withContext McpResponse.error("缺少 moduleName 参数", request.id)
            
            val moduleDir = File(workspaceRoot, moduleName)
            if (!moduleDir.exists()) {
                return@withContext McpResponse.error("模块不存在: $moduleName", request.id)
            }
            
            val srcDir = File(moduleDir, "src")
            if (!srcDir.exists()) {
                return@withContext McpResponse.error("模块src目录不存在: ${srcDir.absolutePath}", request.id)
            }
            
            val fileList = getSrcFileList(srcDir, moduleName)
            
            val result = buildString {
                appendLine("📁 模块源码文件列表")
                appendLine("模块: $moduleName")
                appendLine("src路径: ${srcDir.absolutePath}")
                appendLine("文件总数: ${fileList.size}")
                appendLine()
                appendLine("📋 文件列表:")
                
                fileList.forEach { fileInfo ->
                    val prefix = "  ".repeat(fileInfo.depth)
                    val icon = if (fileInfo.isDirectory) "📁" else "📄"
                    appendLine("$prefix$icon ${fileInfo.name}")
                }
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetModuleSrcFileListTool error: ${e.message}")
            McpResponse.error("获取模块源码文件列表失败: ${e.message}", request.id)
        }
    }
    
    /**
     * 获取src目录下的文件列表
     */
    private fun getSrcFileList(srcDir: File, moduleName: String): List<SrcFileInfo> {
        val fileList = mutableListOf<SrcFileInfo>()
        
        fun scanDirectory(dir: File, depth: Int) {
            dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))?.forEach { file ->
                // 跳过隐藏文件和构建目录
                if (!file.name.startsWith(".") && !file.name.startsWith("build")) {
                    fileList.add(SrcFileInfo(
                        name = file.name,
                        path = file.relativeTo(File(workspaceRoot, moduleName)).path,
                        isDirectory = file.isDirectory,
                        depth = depth
                    ))
                    
                    if (file.isDirectory) {
                        scanDirectory(file, depth + 1)
                    }
                }
            }
        }
        
        scanDirectory(srcDir, 0)
        return fileList
    }
}

/**
 * 模块信息
 */
data class ModuleInfo(
    val name: String,
    val absolutePath: String,
    val relativePath: String,
    val type: String,
    val buildFile: String
)

/**
 * 源码文件信息
 */
data class SrcFileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val depth: Int
) 