package com.itsaky.androidide.formatprovider

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * A code formatter for HTML files.
 * It uses the Jsoup library to parse and pretty-print the document.
 */
class HtmlFormatter(private val indentSize: Int = 4) : CodeFormatter {

    override fun format(source: String): String {
        return try {
            val doc: Document = Jsoup.parse(source)
            doc.outputSettings()
                .indentAmount(indentSize)
                .prettyPrint(true)
            
            if (!source.trim().lowercase().startsWith("<!doctype") && !source.trim().lowercase().startsWith("<html")) {
                doc.body().html()
            } else {
                doc.outerHtml()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }
}