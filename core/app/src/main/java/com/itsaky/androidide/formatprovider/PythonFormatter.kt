package com.itsaky.androidide.formatprovider

/**
 * A rule-based, simplified formatter for Python.
 * It focuses on fixing mixed indentation and ensuring proper spacing around functions and classes.
 */
class PythonFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val formatted = StringBuilder()
        val indentString = " ".repeat(indentSize)

        val lines = source.lines()
        for (i in lines.indices) {
            val line = lines[i]
            
            var leadingWhitespace = line.takeWhile { it.isWhitespace() }
            val tabCount = leadingWhitespace.count { it == '\t' }
            val spaceCount = leadingWhitespace.count { it == ' ' }
            val totalIndent = (tabCount * indentSize) + spaceCount
            
            val indentLevel = totalIndent / indentSize
            val correctedIndent = indentString.repeat(indentLevel)
            
            var formattedLine = correctedIndent + line.trimStart()

            if (i > 1 && (formattedLine.trim().startsWith("def ") || formattedLine.trim().startsWith("class "))) {
                if (lines[i-1].isNotBlank() && lines[i-2].isNotBlank()) {
                    if(!lines[i-1].trim().startsWith("@")) {
                        formatted.insert(formatted.length - lines[i-1].length -1, "\n")
                    }
                }
            }

            formatted.append(formattedLine).append('\n')
        }

        return formatted.toString().trim()
    }
}