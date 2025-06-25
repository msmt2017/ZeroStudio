package android.zero.mcp.meta

import kotlinx.serialization.Serializable

/**
 * 每个 MCP 工具的描述元数据
 */
@Serializable
data class McpToolInfo(
    val method: String,
    val description: String,
    val params: List<String>,
    val example: String
)

object McpToolMetadata {

    val toolList = listOf(
        // TabFile 工具集
        McpToolInfo(
            method = "TabFile.getCursor",
            description = "获取光标所在行的完整内容",
            params = listOf(),
            example = "{\"method\":\"TabFile.getCursor\"}"
        ),
        McpToolInfo(
            method = "TabFile.getFile",
            description = "获取当前标签页文件的完整内容",
            params = listOf(),
            example = "{\"method\":\"TabFile.getFile\"}"
        ),
        McpToolInfo(
            method = "TabFile.getLine",
            description = "获取指定行内容，支持单行或多行范围",
            params = listOf("lineRange"),
            example = "{\"method\":\"TabFile.getLine\",\"params\":{\"lineRange\":\"5\"}}"
        ),
        McpToolInfo(
            method = "TabFile.searchTabFile",
            description = "在当前标签页文件中搜索内容",
            params = listOf("searchContent"),
            example = "{\"method\":\"TabFile.searchTabFile\",\"params\":{\"searchContent\":\"main\"}}"
        ),
        McpToolInfo(
            method = "TabFile.getFunction",
            description = "获取指定函数名的完整内容",
            params = listOf("functionName"),
            example = "{\"method\":\"TabFile.getFunction\",\"params\":{\"functionName\":\"onCreate\"}}"
        ),
        McpToolInfo(
            method = "TabFile.insertLine",
            description = "在指定行插入内容",
            params = listOf("line", "content"),
            example = "{\"method\":\"TabFile.insertLine\",\"params\":{\"line\":\"5\",\"content\":\"// 新插入的代码\"}}"
        ),
        McpToolInfo(
            method = "TabFile.replaceLine",
            description = "替换指定行的内容",
            params = listOf("line", "content"),
            example = "{\"method\":\"TabFile.replaceLine\",\"params\":{\"line\":\"5\",\"content\":\"// 替换后的代码\"}}"
        ),
        McpToolInfo(
            method = "TabFile.deleteLine",
            description = "删除指定行",
            params = listOf("line"),
            example = "{\"method\":\"TabFile.deleteLine\",\"params\":{\"line\":\"5\"}}"
        ),
        McpToolInfo(
            method = "TabFile.getCurrentFile",
            description = "获取当前标签页文件信息",
            params = listOf(),
            example = "{\"method\":\"TabFile.getCurrentFile\"}"
        ),
        
        // File 工具集
        McpToolInfo(
            method = "File.search",
            description = "搜索指定路径下所有包含关键词的文件",
            params = listOf("path", "content"),
            example = "{\"method\":\"File.search\",\"params\":{\"path\":\"src\",\"content\":\"main\"}}"
        ),
        McpToolInfo(
            method = "File.info",
            description = "查询文件/目录大小、时间、哈希等信息",
            params = listOf("path"),
            example = "{\"method\":\"File.info\",\"params\":{\"path\":\"app/build.gradle.kts\"}}"
        ),
        McpToolInfo(
            method = "File.WriteFile",
            description = "写入文件内容",
            params = listOf("path", "content"),
            example = "{\"method\":\"File.WriteFile\",\"params\":{\"path\":\"test.txt\",\"content\":\"文件内容\"}}"
        ),
        McpToolInfo(
            method = "File.Rename",
            description = "重命名文件或目录",
            params = listOf("DestinationPath", "RenameContent"),
            example = "{\"method\":\"File.Rename\",\"params\":{\"DestinationPath\":\"old.txt\",\"RenameContent\":\"new.txt\"}}"
        ),
        McpToolInfo(
            method = "File.move",
            description = "移动文件到指定目录",
            params = listOf("movePath", "DestinationPath"),
            example = "{\"method\":\"File.move\",\"params\":{\"movePath\":\"file.txt\",\"DestinationPath\":\"src/\"}}"
        ),
        McpToolInfo(
            method = "File.copy",
            description = "复制文件到指定目录",
            params = listOf("copyPath", "DestinationPath"),
            example = "{\"method\":\"File.copy\",\"params\":{\"copyPath\":\"file.txt\",\"DestinationPath\":\"backup/\"}}"
        ),
        McpToolInfo(
            method = "File.delete",
            description = "删除文件或目录",
            params = listOf("path"),
            example = "{\"method\":\"File.delete\",\"params\":{\"path\":\"temp.txt\"}}"
        ),
        McpToolInfo(
            method = "File.create",
            description = "创建文件或目录",
            params = listOf("path", "folder", "files"),
            example = "{\"method\":\"File.create\",\"params\":{\"path\":\"./\",\"folder\":\"newdir\",\"files\":\"newfile.txt\"}}"
        ),
        
        // Gradle 工具集
        McpToolInfo(
            method = "gradle.run-project",
            description = "运行当前项目",
            params = listOf(),
            example = "{\"method\":\"gradle.run-project\"}"
        ),
        McpToolInfo(
            method = "gradle.Refresh-project",
            description = "刷新项目",
            params = listOf(),
            example = "{\"method\":\"gradle.Refresh-project\"}"
        ),
        
        // Task 工具集
        McpToolInfo(
            method = "task.runTask",
            description = "运行指定 Gradle 任务",
            params = listOf("runTask"),
            example = "{\"method\":\"task.runTask\",\"params\":{\"runTask\":\":build\"}}"
        ),
        McpToolInfo(
            method = "task.taskList",
            description = "列出所有可用的 Gradle 任务",
            params = listOf(),
            example = "{\"method\":\"task.taskList\"}"
        ),
        McpToolInfo(
            method = "task.searchTask",
            description = "搜索 Gradle 任务",
            params = listOf("searchTask"),
            example = "{\"method\":\"task.searchTask\",\"params\":{\"searchTask\":\"build\"}}"
        ),
        
        // Shell 工具集
        McpToolInfo(
            method = "shell.execute",
            description = "执行终端命令",
            params = listOf("execute"),
            example = "{\"method\":\"shell.execute\",\"params\":{\"execute\":\"ls /data\"}}"
        ),
        
        // Workspace 工具集
        McpToolInfo(
            method = "File.workspace.getmoduleInfo",
            description = "获取工作区模块信息",
            params = listOf(),
            example = "{\"method\":\"File.workspace.getmoduleInfo\"}"
        ),
        McpToolInfo(
            method = "File.workspace.getGradleWrapperInfo",
            description = "获取 Gradle Wrapper 信息",
            params = listOf(),
            example = "{\"method\":\"File.workspace.getGradleWrapperInfo\"}"
        ),
        McpToolInfo(
            method = "File.workspace.getinstallApk",
            description = "安装 APK 文件",
            params = listOf("variant"),
            example = "{\"method\":\"File.workspace.getinstallApk\",\"params\":{\"variant\":\"debug\"}}"
        ),
        McpToolInfo(
            method = "File.workspace.ModifyGradleVersion",
            description = "修改 Gradle 版本",
            params = listOf("version", "type"),
            example = "{\"method\":\"File.workspace.ModifyGradleVersion\",\"params\":{\"version\":\"8.5\",\"type\":\"all\"}}"
        ),
        McpToolInfo(
            method = "File.workspace.GetModuleSrcFileList",
            description = "获取模块源码文件列表",
            params = listOf("module"),
            example = "{\"method\":\"File.workspace.GetModuleSrcFileList\",\"params\":{\"module\":\"app\"}}"
        )
    )

    fun describeAll(): String = toolList.joinToString("\n\n") { info ->
        "🔧 ${info.method}\n${info.description}\n参数: ${info.params}\n示例: ${info.example}"
    }

    fun getToolHelp(method: String): McpToolInfo? =
        toolList.find { it.method.equals(method, ignoreCase = true) }
}
