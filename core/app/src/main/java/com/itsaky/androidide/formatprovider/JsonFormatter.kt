package com.itsaky.androidide.formatprovider

import org.json.JSONArray
import org.json.JSONObject

/**
 * A code formatter for JSON files.
 * It uses Android's built-in JSON library to parse and re-serialize the object with indentation.
 */
class JsonFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val trimmedSource = source.trim()
        return try {
            if (trimmedSource.startsWith("{")) {
                val jsonObject = JSONObject(trimmedSource)
                jsonObject.toString(indentSize)
            } else if (trimmedSource.startsWith("[")) {
                val jsonArray = JSONArray(trimmedSource)
                jsonArray.toString(indentSize)
            } else {
                source // Not a valid JSON object/array start
            }
        } catch (e: Exception) {
            e.printStackTrace()
            source // Return original on parsing error
        }
    }
}