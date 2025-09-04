package me.rerere.rag.extractor.impl

import me.rerere.rag.extractor.DataExtractor

class TextExtractor : DataExtractor<String> {
    override fun extract(data: String): List<String> {
        return listOf(data)
    }
}
