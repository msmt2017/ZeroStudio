package com.itsaky.androidide.formatprovider

/**
 * An interface for language-specific code formatters.
 */
interface CodeFormatter {
    /**
     * Formats the given source code string.
     * @param source The raw source code.
     * @return The formatted source code.
     * @throws Exception if formatting fails.
     */
    fun format(source: String): String
}