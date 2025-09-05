package com.itsaky.androidide.formatprovider

/**
 * A rule-based, simplified code formatter for Shell scripts (.sh, .bash, etc.).
 * This implementation focuses on indentation for control structures like if, for, while, and case.
 */
class ShellFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val formatted = StringBuilder()
        var indentLevel = 0
        val indentString = " ".repeat(indentSize)
        val lines = source.lines()

        val indentKeywords = setOf("if", "for", "while", "case", "then", "do")
        val dedentKeywords = setOf("fi", "done", "esac")

        lines.forEach { line ->
            var trimmedLine = line.trim()
            
            trimmedLine = trimmedLine.replace(Regex("\\s*\\|\\s*"), " | ")
                                   .replace(Regex("\\s*>\\s*"), " > ")
                                   .replace(Regex("\\s*<\\s*"), " < ")

            val firstWord = trimmedLine.split(Regex("\\s+")).firstOrNull() ?: ""
            
            if (dedentKeywords.contains(firstWord)) {
                if (indentLevel > 0) {
                    indentLevel--
                }
            } else if (trimmedLine.endsWith(";;")) {
                 if (indentLevel > 0) {
                    indentLevel--
                }
            }

            if (trimmedLine.isNotEmpty()) {
                for (i in 0 until indentLevel) {
                    formatted.append(indentString)
                }
            }
            formatted.append(trimmedLine).append('\n')

            if (indentKeywords.contains(firstWord) || trimmedLine.endsWith("do") || trimmedLine.endsWith("then")) {
                indentLevel++
            } else if (trimmedLine.endsWith(")")) {
                 val caseLine = lines.find { it.trim().startsWith("case ") }
                 if(caseLine != null && lines.indexOf(line) > lines.indexOf(caseLine)) {
                    indentLevel++
                 }
            }
        }
        
        return formatted.toString().trim()
    }
}