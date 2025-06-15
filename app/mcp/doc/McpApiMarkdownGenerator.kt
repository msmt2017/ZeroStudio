package android.zero.mcp.doc

import android.zero.mcp.meta.McpToolMetadata
import java.io.File

/**
 * 工具 API Markdown 文档生成器
 */
object McpApiMarkdownGenerator {

    fun generateTo(file: File): Boolean {
        return try {
            val content = buildString {
                append("# MCP 工具 API 文档\n\n")
                McpToolMetadata.toolList.forEach { tool ->
                    append("## ${tool.method}\n")
                    append("- 描述: ${tool.description}\n")
                    append("- 参数: ${tool.params.joinToString(", ")}\n")
                    append("- 示例: \n\n```
${tool.example}
```\n\n")
                }
            }
            file.writeText(content)
            true
        } catch (e: Exception) {
            false
        }
    }
}