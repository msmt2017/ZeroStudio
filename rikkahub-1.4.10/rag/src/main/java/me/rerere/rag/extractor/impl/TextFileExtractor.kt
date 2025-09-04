package me.rerere.rag.extractor.impl

import me.rerere.rag.extractor.DataExtractor
import java.io.File

/**
 * 从文本文件中提取文本内容
 * 支持常见的文本文件格式
 */
class TextFileExtractor : DataExtractor<File> {
    /**
     * 从文件中提取文本内容
     * @param data 文本文件
     * @return 提取出的文本内容
     */
    override fun extract(data: File): List<String> {
        if (!data.exists() || !data.isFile) {
            return emptyList()
        }

        return try {
            val text = data.readText()
            listOf(text)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
