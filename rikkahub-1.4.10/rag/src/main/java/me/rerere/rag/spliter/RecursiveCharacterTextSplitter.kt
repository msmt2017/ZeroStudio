package me.rerere.rag.spliter

/**
 * 递归字符文本分割器
 * 支持多级分割，先尝试按段落分割，如果块太大，再尝试按句子分割，以此类推
 * @param chunkSize 每个文本块的最大大小
 * @param chunkOverlap 相邻文本块之间的重叠大小
 * @param separators 按优先级排序的分隔符列表
 */
class RecursiveCharacterTextSplitter(
    private val chunkSize: Int = 1000,
    private val chunkOverlap: Int = 200,
    private val separators: List<String> = listOf("\n\n", "\n", ". ", ", ", " ", "")
) : TextSplitter {

    init {
        require(chunkSize > chunkOverlap) { "Chunk size must be greater than chunk overlap" }
        require(separators.isNotEmpty()) { "Separators list cannot be empty" }
        // 确保最后一个分隔符能够分割任何字符
        require(separators.last().isEmpty()) { "Last separator must be empty string" }
    }

    override fun split(text: String): List<String> {
        return splitText(text, 0)
    }

    private fun splitText(text: String, level: Int): List<String> {
        if (text.length <= chunkSize) {
            return listOf(text)
        }

        // 如果已经到达最后一个分隔符，直接按字符分割
        if (level >= separators.size - 1) {
            return splitByCharacters(text)
        }

        val separator = separators[level]
        // 空分隔符表示按字符分割
        if (separator.isEmpty()) {
            return splitByCharacters(text)
        }

        val chunks = mutableListOf<String>()
        // 按当前级别的分隔符分割
        val segments = if (separator == "\n") {
            text.split("\n")
        } else {
            text.split(separator)
        }

        var currentChunk = StringBuilder()

        for (segment in segments) {
            val segmentWithSeparator = if (segment.isEmpty()) "" else segment + separator

            // 如果添加这个段会导致超出块大小
            if (currentChunk.length + segmentWithSeparator.length > chunkSize) {
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                }

                // 如果单个段太长，递归分割
                if (segmentWithSeparator.length > chunkSize) {
                    chunks.addAll(splitText(segmentWithSeparator, level + 1))
                } else {
                    currentChunk = StringBuilder(segmentWithSeparator)
                }
            } else {
                currentChunk.append(segmentWithSeparator)
            }
        }

        // 添加最后一个块
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        // 应用重叠
        return mergeWithOverlap(chunks)
    }

    private fun splitByCharacters(text: String): List<String> {
        val chunks = mutableListOf<String>()
        for (i in 0 until text.length step (chunkSize - chunkOverlap)) {
            val chunkEnd = minOf(i + chunkSize, text.length)
            chunks.add(text.substring(i, chunkEnd))

            if (chunkEnd == text.length) {
                break
            }
        }
        return chunks
    }

    private fun mergeWithOverlap(chunks: List<String>): List<String> {
        if (chunks.size <= 1 || chunkOverlap <= 0) {
            return chunks
        }

        val result = mutableListOf<String>()
        result.add(chunks.first())

        for (i in 1 until chunks.size) {
            val prevChunk = result.last()
            val currentChunk = chunks[i]

            // 计算重叠部分
            val overlapStart = maxOf(0, prevChunk.length - chunkOverlap)
            val prevChunkEnd = prevChunk.substring(overlapStart)

            // 查找重叠
            val overlapIndex = currentChunk.indexOf(prevChunkEnd)

            if (overlapIndex != -1) {
                // 有重叠部分，合并
                result[result.size - 1] =
                    prevChunk + currentChunk.substring(prevChunkEnd.length + overlapIndex)
            } else {
                // 没有找到精确重叠，直接添加
                result.add(currentChunk)
            }
        }

        return result
    }

    override fun getConfig(): Map<String, Any> {
        return mapOf(
            "chunkSize" to chunkSize,
            "chunkOverlap" to chunkOverlap,
            "separators" to separators
        )
    }
}
