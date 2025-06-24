package android.zero.mcp.services

import android.content.Context
import android.zero.mcp.utils.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * RikkaHub AI集成服务
 * 用于将文件内容集成到AI窗口中
 */
object RikkahubAIIntegrationService {
    
    private val _pendingContent = MutableStateFlow<List<AIContentItem>>(emptyList())
    val pendingContent: StateFlow<List<AIContentItem>> = _pendingContent.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    companion object {
        private const val MAX_PENDING_CONTENT = 50
    }
    
    /**
     * 初始化服务
     */
    fun initialize(context: Context) {
        Logger.log("初始化 RikkaHub AI 集成服务")
        _isConnected.value = true
    }
    
    /**
     * 添加文件内容到AI窗口
     */
    fun addFileContent(
        fileName: String,
        filePath: String,
        content: String,
        autoSend: Boolean = true
    ): String {
        val contentId = UUID.randomUUID().toString()
        
        val contentItem = AIContentItem(
            id = contentId,
            type = ContentType.FILE,
            fileName = fileName,
            filePath = filePath,
            content = content,
            autoSend = autoSend,
            timestamp = System.currentTimeMillis()
        )
        
        addContentItem(contentItem)
        
        Logger.log("添加文件内容到AI窗口: $fileName")
        return contentId
    }
    
    /**
     * 添加文本内容到AI窗口
     */
    fun addTextContent(
        text: String,
        autoSend: Boolean = true
    ): String {
        val contentId = UUID.randomUUID().toString()
        
        val contentItem = AIContentItem(
            id = contentId,
            type = ContentType.TEXT,
            content = text,
            autoSend = autoSend,
            timestamp = System.currentTimeMillis()
        )
        
        addContentItem(contentItem)
        
        Logger.log("添加文本内容到AI窗口")
        return contentId
    }
    
    /**
     * 添加图片内容到AI窗口
     */
    fun addImageContent(
        imagePath: String,
        imageName: String? = null,
        autoSend: Boolean = true
    ): String {
        val contentId = UUID.randomUUID().toString()
        
        val contentItem = AIContentItem(
            id = contentId,
            type = ContentType.IMAGE,
            fileName = imageName ?: "图片",
            filePath = imagePath,
            content = "![$imageName]($imagePath)",
            autoSend = autoSend,
            timestamp = System.currentTimeMillis()
        )
        
        addContentItem(contentItem)
        
        Logger.log("添加图片内容到AI窗口: $imagePath")
        return contentId
    }
    
    /**
     * 添加内容项
     */
    private fun addContentItem(item: AIContentItem) {
        val currentList = _pendingContent.value.toMutableList()
        
        // 限制待处理内容数量
        if (currentList.size >= MAX_PENDING_CONTENT) {
            currentList.removeAt(0)
        }
        
        currentList.add(item)
        _pendingContent.value = currentList
    }
    
    /**
     * 获取下一个待处理内容
     */
    fun getNextContent(): AIContentItem? {
        val currentList = _pendingContent.value.toMutableList()
        return if (currentList.isNotEmpty()) {
            val item = currentList.removeAt(0)
            _pendingContent.value = currentList
            item
        } else {
            null
        }
    }
    
    /**
     * 清除所有待处理内容
     */
    fun clearAllContent() {
        _pendingContent.value = emptyList()
        Logger.log("清除所有待处理内容")
    }
    
    /**
     * 移除指定内容
     */
    fun removeContent(contentId: String): Boolean {
        val currentList = _pendingContent.value.toMutableList()
        val removed = currentList.removeAll { it.id == contentId }
        if (removed) {
            _pendingContent.value = currentList
            Logger.log("移除内容: $contentId")
        }
        return removed
    }
    
    /**
     * 获取内容统计
     */
    fun getContentStats(): ContentStats {
        val content = _pendingContent.value
        return ContentStats(
            totalCount = content.size,
            fileCount = content.count { it.type == ContentType.FILE },
            textCount = content.count { it.type == ContentType.TEXT },
            imageCount = content.count { it.type == ContentType.IMAGE },
            autoSendCount = content.count { it.autoSend }
        )
    }
    
    /**
     * 检查是否有待处理内容
     */
    fun hasPendingContent(): Boolean {
        return _pendingContent.value.isNotEmpty()
    }
    
    /**
     * 关闭服务
     */
    fun shutdown() {
        Logger.log("关闭 RikkaHub AI 集成服务")
        _isConnected.value = false
        clearAllContent()
    }
}

/**
 * AI内容项
 */
@Serializable
data class AIContentItem(
    val id: String,
    val type: ContentType,
    val fileName: String? = null,
    val filePath: String? = null,
    val content: String,
    val autoSend: Boolean,
    val timestamp: Long
) {
    fun getDisplayName(): String {
        return fileName ?: when (type) {
            ContentType.FILE -> "文件"
            ContentType.TEXT -> "文本"
            ContentType.IMAGE -> "图片"
        }
    }
    
    fun getFormattedContent(): String {
        return when (type) {
            ContentType.FILE -> {
                buildString {
                    appendLine("📁 文件: ${fileName ?: "未知文件"}")
                    if (!filePath.isNullOrEmpty()) {
                        appendLine("路径: $filePath")
                    }
                    appendLine()
                    appendLine("📄 内容:")
                    appendLine("```${getFileExtension(fileName)}")
                    append(content)
                    appendLine("```")
                }
            }
            ContentType.TEXT -> content
            ContentType.IMAGE -> content
        }
    }
    
    private fun getFileExtension(fileName: String?): String {
        return fileName?.substringAfterLast('.', "")?.lowercase() ?: ""
    }
}

/**
 * 内容类型
 */
@Serializable
enum class ContentType {
    FILE,
    TEXT,
    IMAGE
}

/**
 * 内容统计
 */
@Serializable
data class ContentStats(
    val totalCount: Int,
    val fileCount: Int,
    val textCount: Int,
    val imageCount: Int,
    val autoSendCount: Int
) 