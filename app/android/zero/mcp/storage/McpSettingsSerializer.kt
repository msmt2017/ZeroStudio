package android.zero.mcp.storage

import android.zero.mcp.meta.McpToolCategoryManager
import android.zero.mcp.policy.ToolExecutionPolicy
import android.zero.mcp.prompt.PromptTemplateManager
import android.zero.mcp.security.McpToolPermissionManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * MCP 配置项序列化与反序列化工具
 */
object McpSettingsSerializer {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun saveAll() {
        McpSettingsStorage.putStringSet(McpSettingsKeys.ALLOWED_TOOLS, McpToolPermissionManager.listAllowed().toSet())
        McpSettingsStorage.putStringSet(McpSettingsKeys.DENIED_TOOLS, McpToolPermissionManager.listDenied().toSet())

        val policyJson = json.encodeToString(ToolExecutionPolicy.getAllPolicies())
        McpSettingsStorage.putString(McpSettingsKeys.TOOL_POLICIES, policyJson)

        val promptJson = json.encodeToString(PromptTemplateManager.dumpMap())
        McpSettingsStorage.putString(McpSettingsKeys.PROMPT_TEMPLATES, promptJson)

        val categoryJson = json.encodeToString(McpToolCategoryManager.getCategoryMap())
        McpSettingsStorage.putString(McpSettingsKeys.TOOL_CATEGORIES, categoryJson)
    }

    fun loadAll() {
        McpToolPermissionManager.allowOnly(*McpSettingsStorage.getStringSet(McpSettingsKeys.ALLOWED_TOOLS).toTypedArray())
        McpSettingsStorage.getStringSet(McpSettingsKeys.DENIED_TOOLS).forEach {
            McpToolPermissionManager.deny(it)
        }

        runCatching {
            val policies = json.decodeFromString<Map<String, ToolExecutionPolicy.Policy>>(
                McpSettingsStorage.getString(McpSettingsKeys.TOOL_POLICIES)
            )
            policies.forEach { ToolExecutionPolicy.setPolicy(it.key, it.value) }
        }

        runCatching {
            val promptMap = json.decodeFromString<Map<String, String>>(
                McpSettingsStorage.getString(McpSettingsKeys.PROMPT_TEMPLATES)
            )
            promptMap.forEach { PromptTemplateManager.registerTemplate(it.key, it.value) }
        }

        runCatching {
            val categoryMap = json.decodeFromString<Map<String, List<String>>>(
                McpSettingsStorage.getString(McpSettingsKeys.TOOL_CATEGORIES)
            )
            McpToolCategoryManager.loadFrom(categoryMap)
        }
    }
}
