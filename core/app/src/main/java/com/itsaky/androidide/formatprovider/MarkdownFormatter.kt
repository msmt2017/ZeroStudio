package com.itsaky.androidide.formatprovider

/**
 * A rule-based formatter for Markdown.
 * It standardizes list markers, heading spacing, and blank lines around code blocks.
 */
class MarkdownFormatter : CodeFormatter {

    override fun format(source: String): String {
        val formattedLines = mutableListOf<String>()
        val lines = source.lines()
        var inCodeBlock = false

        for (i in lines.indices) {
            var line = lines[i]

            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock
                if (i > 0 && formattedLines.last().isNotBlank()) {
                    formattedLines.add("")
                }
                formattedLines.add(line)
                continue
            }

            if (inCodeBlock) {
                formattedLines.add(line)
                continue
            }
            
            line = line.replace(Regex("^#+\\s*(\\S)"), "$0 $1").trimEnd()
            
            line = line.replace(Regex("^\\s*[*+]\\s+"), "  - ")
            
            if (i > 0 && lines[i-1].isNotBlank()) {
                val isNewList = line.trim().startsWith("- ") && !lines[i-1].trim().startsWith("- ")
                val isNewHeading = line.trim().startsWith("#")
                if(isNewList || isNewHeading) {
                    formattedLines.add("")
                }
            }

            formattedLines.add(line)
        }

        return formattedLines.joinToString("\n")
    }
}