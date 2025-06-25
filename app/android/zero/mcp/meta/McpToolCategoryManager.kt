package android.zero.mcp.meta

import android.zero.mcp.McpServerLog

/**
 * 工具分类系统：用于按模块对 MCP 工具进行分组管理
 */
object McpToolCategoryManager {

    private val categoryMap = mutableMapOf<String, MutableList<String>>()

    fun register(tool: String, category: String) {
        val list = categoryMap.getOrPut(category) { mutableListOf() }
        if (!list.contains(tool)) {
            list += tool
            McpServerLog.log("已归类工具 [$tool] -> 分类 [$category]")
        }
    }

    fun getCategory(tool: String): String? {
        return categoryMap.entries.find { it.value.contains(tool) }?.key
    }

    fun getToolsByCategory(category: String): List<String> =
        categoryMap[category]?.toList()?.sorted() ?: emptyList()

    fun getAllCategories(): Set<String> = categoryMap.keys

    fun dumpCategoryMap(): String =
        categoryMap.entries.joinToString("\n\n") { (cat, tools) ->
            "📂 $cat\n${tools.joinToString("\n") { "- $it" }}"
        }
}
