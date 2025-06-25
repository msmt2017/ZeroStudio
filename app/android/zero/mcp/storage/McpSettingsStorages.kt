package android.zero.mcp.storage

import android.content.Context
import org.json.JSONObject

/**
 * 扩展 McpSettingsStorage 提供原始 JSON 导入导出能力
 */
fun McpSettingsStorage.getAllRaw(): String {
    val all = prefs.all
    val json = JSONObject()
    for ((key, value) in all) {
        when (value) {
            is Set<*> -> json.put(key, value.joinToString("|"))
            else -> json.put(key, value.toString())
        }
    }
    return json.toString(2)
}

fun McpSettingsStorage.importAllRaw(context: Context, jsonString: String) {
    init(context)
    val json = JSONObject(jsonString)
    val editor = prefs.edit()
    json.keys().forEach { key ->
        val raw = json.getString(key)
        if (raw.contains("|")) {
            editor.putStringSet(key, raw.split("|").toSet())
        } else {
            editor.putString(key, raw)
        }
    }
    editor.apply()
}
