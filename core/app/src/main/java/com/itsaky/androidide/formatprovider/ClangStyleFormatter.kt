package com.itsaky.androidide.formatprovider

/**
 * An advanced, rule-based formatter that mimics the behavior of tools like clang-format.
 * It's designed for C-family languages (C, C++, Java, C#) and JavaScript/TypeScript.
 * It handles indentation, spacing around operators, and brace placement.
 */
class ClangStyleFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val formatted = StringBuilder()
        var indentLevel = 0
        val indentString = " ".repeat(indentSize)
        var inBlockComment = false

        source.lines().forEach { line ->
            var trimmedLine = line.trim()

            if (trimmedLine.isEmpty()) {
                formatted.append('\n')
                return@forEach
            }
            
            if (inBlockComment) {
                if (trimmedLine.contains("*/")) {
                    inBlockComment = false
                }
                formatted.append(trimmedLine).append('\n')
                return@forEach
            }
            
            if (trimmedLine.startsWith("/*")) {
                inBlockComment = true
            }

            if (!trimmedLine.matches(Regex("for\\s*\\(.*\\)"))) {
                 trimmedLine = trimmedLine.replace(Regex("([^\\]=><!])=([^\\]=])"), "$1 = $2")
                                       .replace(Regex("(\\S)([+\\-*/%<>]=?)(\\S)"), "$1 $2 $3")
                                       .replace(Regex("&&"), " && ")
                                       .replace(Regex("\\|\\|"), " || ")
            }
            trimmedLine = trimmedLine.replace(Regex(",(\\S)"), ", $1")
                                   .replace(Regex(";(\\S)"), "; $1")
            trimmedLine = trimmedLine.replace(Regex("(if|for|while|switch|catch)(\\()"), "$1 $2")

            if (trimmedLine.startsWith("}") || trimmedLine.startsWith(")") || trimmedLine.startsWith("case ") || trimmedLine.startsWith("default:")) {
                if (indentLevel > 0) {
                    indentLevel--
                }
            }

            for (i in 0 until indentLevel) {
                formatted.append(indentString)
            }
            formatted.append(trimmedLine).append('\n')
            
            if (trimmedLine.endsWith("{") || trimmedLine.endsWith("(")) {
                 if (!trimmedLine.matches(Regex(".*\\)\\s*\\{"))) {
                    indentLevel++
                 }
            }
             if (trimmedLine.matches(Regex(".*\\)\\s*\\{"))) {
                  indentLevel++
             }
        }

        return formatted.toString().replace(Regex("(\\s*\\n\\s*){3,}"), "\n\n").trim()
    }
}