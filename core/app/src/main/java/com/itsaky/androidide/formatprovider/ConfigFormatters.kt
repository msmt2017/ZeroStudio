package com.itsaky.androidide.formatprovider

// A very basic formatter for YAML
class YamlFormatter(private val indentSize: Int = 2) : CodeFormatter {
    override fun format(source: String): String {
        // YAML formatting is complex. This is a simplified indentation fixer.
        val lines = source.lines()
        val result = StringBuilder()
        var currentIndent = 0
        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                result.append('\n')
                return@forEach
            }
            val indent = line.takeWhile { it.isWhitespace() }.length
            if (indent > currentIndent) {
                currentIndent = indent
            } else if (indent < currentIndent) {
                currentIndent = indent
            }
            result.append(" ".repeat(currentIndent)).append(trimmed).append('\n')
        }
        return result.toString().trim()
    }
}

// A simple formatter for TOML and INI files (keeps structure)
class TomlFormatter : CodeFormatter {
    override fun format(source: String): String {
        return source.lines().joinToString("\n") { it.trim() }.replace(Regex("\n\n+"), "\n\n")
    }
}

class IniFormatter : CodeFormatter {
     override fun format(source: String): String {
        return source.lines().joinToString("\n") { 
            val parts = it.split('=', limit=2)
            if (parts.size == 2) {
                "${parts[0].trim()} = ${parts[1].trim()}"
            } else {
                it.trim()
            }
        }
    }
}