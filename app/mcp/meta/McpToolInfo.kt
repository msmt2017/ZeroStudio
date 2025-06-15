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
        McpToolInfo(
            method = "TabFile.getCursor",
            description = "获取光标所在行内容",
            params = listOf(),
            example = "{\"method\":\"TabFile.getCursor\"}"
        ),
        McpToolInfo(
            method = "TabFile.getFile",
            description = "获取当前文件所有内容",
            params = listOf(),
            example = "{\"method\":\"TabFile.getFile\"}"
        ),
        McpToolInfo(
            method = "File.search",
            description = "搜索指定路径下所有包含关键词的文件",
            params = listOf("path", "content"),
            example = "{\"method\":\"File.search\",\"params\":{\"path\":\"src\",\"content\":\"main\"}}"
        ),
        McpToolInfo(
            method = "task.runTask",
            description = "运行指定 Gradle 任务",
            params = listOf("runTask"),
            example = "{\"method\":\"task.runTask\",\"params\":{\"runTask\":\":build\"}}"
        ),
        McpToolInfo(
            method = "File.info",
            description = "查询文件/目录大小、时间、哈希等信息",
            params = listOf("path"),
            example = "{\"method\":\"File.info\",\"params\":{\"path\":\"app/build.gradle.kts\"}}"
        ),
        McpToolInfo(
            method = "shell.execute",
            description = "执行终端命令",
            params = listOf("execute"),
            example = "{\"method\":\"shell.execute\",\"params\":{\"execute\":\"ls /data\"}}"
        )
        // ✅ 可继续补充所有已注册工具说明
    )

    fun describeAll(): String = toolList.joinToString("\n\n") { info ->
        "🔧 ${info.method}\n${info.description}\n参数: ${info.params}\n示例: ${info.example}"
    }

    fun getToolHelp(method: String): McpToolInfo? =
        toolList.find { it.method.equals(method, ignoreCase = true) }
}
