// >> file: com/itsaky/androidide/formatprovider/KotlinFormatter.kt

package com.itsaky.androidide.formatprovider

import com.facebook.ktfmt.format.Formatter as KtfmtFormatter
import com.facebook.ktfmt.format.FormattingOptions as KtfmtOptions

/**
 * A powerful code formatter for Kotlin files that uses the 'ktfmt' library from Facebook.
 * complex reflowing and chained calls gracefully.
 */
class KotlinFormatter(
    private val options: KotlinFormatOptions = KotlinFormatOptions()
) : CodeFormatter {

    override fun format(source: String): String {
        return try {
            // Map our internal options to ktfmt's options for consistency
            val ktfmtOptions = KtfmtOptions(
                maxWidth = options.maxLineLength,
                blockIndent = options.indentSize,
                continuationIndent = options.indentSize * 2, // A common and readable convention
                removeUnusedImports = options.organizeImports,
                manageTrailingCommas = true // A sensible modern default
            )
            KtfmtFormatter.format(ktfmtOptions, source)
        } catch (e: Exception) {
            // ktfmt can throw errors on syntactically incorrect code, which is expected
            // during live editing. We gracefully return the original source.
            System.err.println("ktfmt formatting failed, returning original source. Reason: ${e.message}")
            // Optional: for debugging, you might want to uncomment the next line
            // e.printStackTrace()
            source
        }
    }
}