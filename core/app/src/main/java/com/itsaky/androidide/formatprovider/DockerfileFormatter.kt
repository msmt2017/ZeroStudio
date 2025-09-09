package com.itsaky.androidide.formatprovider

/**
 * A simple, rule-based formatter for Dockerfiles.
 * It aligns the arguments of multi-line RUN commands.
 */
class DockerfileFormatter(private val indentSize: Int = 4) : CodeFormatter {
    override fun format(source: String): String {
        val result = StringBuilder()
        val lines = source.lines()
        var inRunBlock = false

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("RUN")) {
                inRunBlock = true
            }

            if (inRunBlock && (trimmed.startsWith("&&") || trimmed.startsWith("||"))) {
                result.append(" ".repeat(indentSize)).append(trimmed).append('\n')
            } else {
                result.append(line).append('\n')
                if (!trimmed.endsWith("\\")) {
                    inRunBlock = false
                }
            }
        }
        return result.toString().trim()
    }
}