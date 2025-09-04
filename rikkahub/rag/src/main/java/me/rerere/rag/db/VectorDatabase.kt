package me.rerere.rag.db

import me.rerere.rag.document.Document

/**
 * 向量数据库接口，用于存储和检索向量数据
 */
interface VectorDatabase {
    /**
     * 添加文档到向量数据库
     *
     * @param table 表名
     * @param document 文档列表
     * @param vectors 向量列表
     */
    fun addDocuments(table: String, document: List<Document>, vectors: List<FloatArray>)

    /**
     * 根据向量相似度搜索最相似的文档
     *
     * @param table 数据表名
     * @param embedding 查询向量
     * @param limit 返回结果的最大数量
     * @return 相似文档列表
     */
    fun search(table: String, embedding: FloatArray, limit: Int = 5): List<Document>

    /**
     * 根据ID删除文档
     *
     * @param table 数据表名
     * @param id 文档ID
     */
    fun deleteDocument(table: String, id: String): Boolean

    /**
     * 获取所有文档
     *
     * @param table 数据表名
     * @return 所有文档列表
     */
    fun getAllDocuments(table: String): List<Document>

    /**
     * 清空数据表
     *
     * @param table 数据表名
     */
    fun clear(table: String)

    /**
     * 关闭数据库连接
     */
    fun close()
}
