package android.zero.mcp.storage

import android.content.Context
import android.content.SharedPreferences

/**
 * MCP 设置持久化模块：用于保存权限、策略、Prompt、分类等配置项
 */
object McpSettingsStorage {

    private const val PREF_NAME = "mcp_settings"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, def: String = ""): String {
        return prefs.getString(key, def) ?: def
    }

    fun putStringSet(key: String, values: Set<String>) {
        prefs.edit().putStringSet(key, values).apply()
    }

    fun getStringSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet()) ?: emptySet()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
