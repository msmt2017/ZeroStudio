package me.rerere.rag.spliter

/**
 * 基于正则表达式的文本分割器
 * @param pattern 用于分割文本的正则表达式模式
 * @param chunkSize 每个文本块的最大大小
 * @param chunkOverlap 相邻文本块之间的重叠大小
 */
class RegexTextSplitter(
    private val pattern: String = "\\s+",
    private val chunkSize: Int = 1000,
    private val chunkOverlap: Int = 200
) : TextSplitter {

    init {
        require(chunkSize > chunkOverlap) { "Chunk size must be greater than chunk overlap" }
    }

    override fun split(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }

        val regex = Regex(pattern)
        val matches = regex.findAll(text)
        val splitPoints = matches.map { it.range.first }.toList()

        // 如果没有匹配项，使用整个文本作为一个块
        if (splitPoints.isEmpty()) {
            return listOf(text)
        }

        // 创建块
        val chunks = mutableListOf<String>()
        var currentChunkStart = 0

        while (currentChunkStart < text.length) {
            var currentChunkEnd = currentChunkStart + chunkSize

            // 确保不超出文本范围
            if (currentChunkEnd > text.length) {
                currentChunkEnd = text.length
            } else {
                // 查找最近的分割点
                val nextSplitPoint = splitPoints.firstOrNull { it > currentChunkEnd } ?: text.length
                currentChunkEnd = nextSplitPoint
            }

            // 添加块
            chunks.add(text.substring(currentChunkStart, currentChunkEnd))

            // 移动到下一个块的起点，考虑重叠
            currentChunkStart += (chunkSize - chunkOverlap).coerceAtLeast(1)

            // 确保不超出文本范围
            if (currentChunkStart >= text.length) {
                break
            }
        }

        return chunks
    }

    override fun getConfig(): Map<String, Any> {
        return mapOf(
            "pattern" to pattern,
            "chunkSize" to chunkSize,
            "chunkOverlap" to chunkOverlap
        )
    }
}
