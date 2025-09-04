package me.rerere.rag.extractor.impl

import me.rerere.rag.extractor.DataExtractor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * 从HTML文本中提取纯文本内容
 * 使用 Jsoup 解析 HTML
 */
class HtmlExtractor : DataExtractor<String> {
    /**
     * 从HTML文本中提取纯文本内容
     * @param data HTML文本
     * @return 提取出的纯文本内容列表
     */
    override fun extract(data: String): List<String> {
        if (data.isBlank()) {
            return emptyList()
        }

        return try {
            val document = Jsoup.parse(data)
            val result = mutableListOf<String>()

            // 提取标题
            val title = document.title()
            if (title.isNotBlank()) {
                result.add(title)
            }

            // 提取正文内容
            extractContent(document, result)

            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 提取文档中的主要内容
     */
    private fun extractContent(document: Document, result: MutableList<String>) {
        // 移除不需要的元素
        document.select("script, style, iframe, noscript, head, nav, footer").remove()

        // 提取段落文本
        val paragraphs = document.select("p")
        for (paragraph in paragraphs) {
            val text = paragraph.text().trim()
            if (text.isNotBlank()) {
                result.add(text)
            }
        }

        // 提取标题元素
        val headings = document.select("h1, h2, h3, h4, h5, h6")
        for (heading in headings) {
            val text = heading.text().trim()
            if (text.isNotBlank()) {
                result.add(text)
            }
        }

        // 提取列表项
        val listItems = document.select("li")
        for (item in listItems) {
            val text = item.text().trim()
            if (text.isNotBlank() && !result.contains(text)) {
                result.add(text)
            }
        }

        // 如果没有足够内容，提取所有文本
        if (result.isEmpty()) {
            val bodyText = document.body().text().trim()
            if (bodyText.isNotBlank()) {
                result.add(bodyText)
            }
        }
    }
}
