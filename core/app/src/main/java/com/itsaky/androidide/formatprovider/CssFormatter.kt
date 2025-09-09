package com.itsaky.androidide.formatprovider

/**
 * A rule-based, simplified code formatter for CSS, SCSS, and LESS.
 * This implementation handles basic indentation and line breaks.
 */
class CssFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val formatted = StringBuilder()
        var indentLevel = 0
        val indentString = " ".repeat(indentSize)

        val processedSource = source
            .replace(Regex("\\s*\\{\\s*"), " {\n")
            .replace(Regex("\\s*}\\s*"), "\n}\n")
            .replace(Regex("\\s*;\\s*"), ";\n")
            .replace(Regex(",\\s*"), ", ")

        processedSource.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                return@forEach
            }

            if (trimmedLine.contains("}")) {
                if (indentLevel > 0) {
                    indentLevel--
                }
            }

            for (i in 0 until indentLevel) {
                formatted.append(indentString)
            }
            formatted.append(trimmedLine).append('\n')

            if (trimmedLine.contains("{")) {
                indentLevel++
            }
        }

        return formatted.toString().replace(Regex("\n\n+"), "\n").trim()
    }
}