package me.rerere.rag.extractor

interface DataExtractor<T> {
    fun extract(data: T): List<String>
}
