package me.rerere.rag.spliter

/**
 * 文本分割器接口，用于将长文本分割成更小的片段
 */
interface TextSplitter {
    /**
     * 将文本分割成多个片段
     * @param text 要分割的文本
     * @return 分割后的文本片段列表
     */
    fun split(text: String): List<String>

    /**
     * 获取分割器的配置信息
     * @return 配置信息Map
     */
    fun getConfig(): Map<String, Any>
}
