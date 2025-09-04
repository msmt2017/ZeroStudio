package me.rerere.rag

import android.content.Context
import me.rerere.rag.db.VectorDatabase
import me.rerere.rag.spliter.TextSplitter

private const val TAG = "RAG"

/**
 * RAG（检索增强生成）系统核心类
 * 用于管理向量数据库和文本处理流程
 */
class RAG private constructor(
    private val context: Context,
    private val textSplitter: TextSplitter,
    private val db: VectorDatabase,
    private val embeddingProvider: EmbeddingProvider,
) {

    /**
     * 向量嵌入提供者接口
     * 负责将文本转换为向量嵌入
     */
    interface EmbeddingProvider {
        /**
         * 将文本转换为向量嵌入
         * @param text 要嵌入的文本
         * @return 向量嵌入（浮点数数组）
         */
        fun embed(text: String): FloatArray
    }

    /**
     * 关闭RAG系统，释放资源
     */
    fun close() {
        db.close()
    }

    /**
     * RAG系统构建器类
     */
    class Builder(private val context: Context) {
        private var textSplitter: TextSplitter? = null
        private var db: VectorDatabase? = null
        private var embeddingProvider: EmbeddingProvider? = null

        /**
         * 设置文本分割器
         */
        fun setTextSplitter(splitter: TextSplitter): Builder {
            this.textSplitter = splitter
            return this
        }

        /**
         * 设置向量数据库
         */
        fun setVectorDatabase(database: VectorDatabase): Builder {
            this.db = database
            return this
        }

        /**
         * 设置向量嵌入提供者
         */
        fun setEmbeddingProvider(provider: EmbeddingProvider): Builder {
            this.embeddingProvider = provider
            return this
        }

        /**
         * 构建RAG实例
         * @throws IllegalStateException 如果缺少必要组件
         */
        fun build(): RAG {
            val splitter = textSplitter ?: throw IllegalStateException("TextSplitter must be provided")
            val database = db ?: throw IllegalStateException("VectorDatabase must be provided")
            val provider =
                embeddingProvider ?: throw IllegalStateException("EmbeddingProvider must be provided")

            return RAG(context, splitter, database, provider)
        }
    }

    companion object {
        /**
         * 创建新的RAG构建器
         */
        fun builder(context: Context): Builder {
            return Builder(context)
        }
    }
}
