package me.rerere.rag.spliter

/**
 * 基于字符的文本分割器
 * @param chunkSize 每个文本块的大小
 * @param chunkOverlap 相邻文本块之间的重叠大小
 * @param separator 分割文本的分隔符
 */
class CharacterTextSplitter(
    private val chunkSize: Int = 1000,
    private val chunkOverlap: Int = 200,
    private val separator: String = "\n"
) : TextSplitter {

    init {
        require(chunkSize > chunkOverlap) { "Chunk size must be greater than chunk overlap" }
    }

    override fun split(text: String): List<String> {
        if (text.isEmpty()) {
            return emptyList()
        }

        // 按分隔符拆分文本
        val splits = text.split(separator)
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()

        for (split in splits) {
            // 如果单个分割部分超过块大小，进一步分割
            if (split.length > chunkSize) {
                // 如果当前块不为空，添加到结果并重置
                if (currentChunk.isNotEmpty()) {
                    chunks.add(currentChunk.toString())
                    currentChunk = StringBuilder()
                }

                // 分割长文本
                var i = 0
                while (i < split.length) {
                    val end = minOf(i + chunkSize, split.length)
                    chunks.add(split.substring(i, end))
                    i += chunkSize - chunkOverlap
                }
            } else if (currentChunk.length + split.length + separator.length > chunkSize) {
                // 如果添加分割部分会超过块大小，保存当前块并开始新块
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder(split)
            } else {
                // 添加到当前块
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append(separator)
                }
                currentChunk.append(split)
            }
        }

        // 添加最后一个块（如果不为空）
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }

        return chunks
    }

    override fun getConfig(): Map<String, Any> {
        return mapOf(
            "chunkSize" to chunkSize,
            "chunkOverlap" to chunkOverlap,
            "separator" to separator
        )
    }
}
