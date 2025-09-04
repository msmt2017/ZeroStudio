package me.rerere.rag.document

data class Document(
    val id: String,
    val content: String,
    val metadata: Map<String, Any>,
)
