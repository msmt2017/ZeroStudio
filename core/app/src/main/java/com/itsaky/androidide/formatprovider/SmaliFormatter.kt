package com.itsaky.androidide.formatprovider

/**
 * A rule-based formatter for Smali/Baksmali assembly files.
 * This implementation focuses on standardizing indentation for directives, labels, and opcodes.
 */
class SmaliFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        val formatted = StringBuilder()
        val indentString = " ".repeat(indentSize)
        var inMethod = false

        source.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                formatted.append('\n')
                return@forEach
            }

            if (trimmedLine.startsWith(".")) {
                val directive = trimmedLine.split(Regex("\\s+")).first()
                when (directive) {
                    ".method" -> {
                        formatted.append(trimmedLine).append('\n')
                        inMethod = true
                    }
                    ".end" -> {
                        if (trimmedLine.startsWith(".end method")) {
                            inMethod = false
                        }
                        formatted.append(trimmedLine).append('\n')
                    }
                    ".locals", ".param", ".line", ".prologue", ".registers" -> {
                        formatted.append(indentString).append(trimmedLine).append('\n')
                    }
                    else -> {
                        formatted.append(trimmedLine).append('\n')
                    }
                }
            }
            else if (trimmedLine.startsWith(":")) {
                formatted.append(trimmedLine).append('\n')
            }
            else if (inMethod) {
                val parts = trimmedLine.split('#', limit = 2)
                val instruction = parts[0].trim()
                
                formatted.append(indentString).append(instruction)
                
                if (parts.size > 1) {
                    val instructionLength = indentString.length + instruction.length
                    val paddingSize = (32 - instructionLength).coerceAtLeast(4)
                    val padding = " ".repeat(paddingSize)
                    formatted.append(padding).append('#').append(parts[1])
                }
                formatted.append('\n')
            }
            else {
                formatted.append(trimmedLine).append('\n')
            }
        }

        return formatted.toString().trim()
    }
}