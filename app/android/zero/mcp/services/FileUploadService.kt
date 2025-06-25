package android.zero.mcp.services

import android.content.Context
import android.content.Intent
import android.zero.mcp.utils.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 文件上传服务
 * 用于将文件内容发送到AI窗口
 */
object FileUploadService {
    
    private val _uploadEvents = MutableSharedFlow<FileUploadEvent>(replay = 10)
    val uploadEvents: SharedFlow<FileUploadEvent> = _uploadEvents.asSharedFlow()
    
    companion object {
        private const val ACTION_UPLOAD_FILE = "android.zero.mcp.UPLOAD_FILE_TO_AI"
        private const val EXTRA_FILE_INFO = "file_info"
        private const val EXTRA_UPLOAD_ID = "upload_id"
    }
    
    /**
     * 上传文件到AI
     */
    suspend fun uploadToAI(fileInfo: Map<String, Any>): UploadResult {
        return try {
            val uploadId = UUID.randomUUID().toString()
            
            // 创建上传事件
            val uploadEvent = FileUploadEvent(
                uploadId = uploadId,
                fileInfo = fileInfo,
                status = UploadStatus.PENDING,
                timestamp = System.currentTimeMillis()
            )
            
            // 发送事件
            _uploadEvents.emit(uploadEvent)
            
            // 构建发送到AI的内容
            val aiContent = buildAIContent(fileInfo)
            
            // 发送到AI窗口
            val success = sendToAIWindow(aiContent, fileInfo["autoSend"] as? Boolean ?: true)
            
            val result = if (success) {
                UploadResult(
                    success = true,
                    message = "文件已成功发送到AI窗口",
                    uploadId = uploadId
                )
            } else {
                UploadResult(
                    success = false,
                    message = "发送到AI窗口失败",
                    uploadId = uploadId
                )
            }
            
            // 更新事件状态
            val finalEvent = uploadEvent.copy(
                status = if (success) UploadStatus.SUCCESS else UploadStatus.FAILED,
                result = result
            )
            _uploadEvents.emit(finalEvent)
            
            Logger.log("文件上传完成: $uploadId, 成功: $success")
            result
            
        } catch (e: Exception) {
            Logger.log("文件上传失败: ${e.message}")
            UploadResult(
                success = false,
                message = "上传失败: ${e.message}",
                uploadId = null
            )
        }
    }
    
    /**
     * 构建发送到AI的内容
     */
    private fun buildAIContent(fileInfo: Map<String, Any>): String {
        val fileName = fileInfo["fileName"] as? String ?: "未知文件"
        val filePath = fileInfo["filePath"] as? String ?: ""
        val fileContent = fileInfo["fileContent"] as? String ?: ""
        val lineCount = fileInfo["lineCount"] as? Int ?: 0
        
        return buildString {
            appendLine("📁 文件信息:")
            appendLine("文件名: $fileName")
            appendLine("路径: $filePath")
            appendLine("行数: $lineCount")
            appendLine()
            appendLine("📄 文件内容:")
            appendLine("```${getFileExtension(fileName)}")
            append(fileContent)
            appendLine("```")
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "").lowercase()
    }
    
    /**
     * 发送到AI窗口
     */
    private fun sendToAIWindow(content: String, autoSend: Boolean): Boolean {
        return try {
            // 使用RikkahubAIIntegrationService发送内容
            RikkahubAIIntegrationService.addTextContent(content, autoSend)
            
            Logger.log("内容已发送到AI窗口，自动发送: $autoSend")
            true
            
        } catch (e: Exception) {
            Logger.log("发送到AI窗口失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取上传历史
     */
    suspend fun getUploadHistory(): List<FileUploadEvent> {
        // 这里可以实现获取上传历史的逻辑
        return emptyList()
    }
    
    /**
     * 清除上传历史
     */
    suspend fun clearUploadHistory() {
        // 清除上传历史
        Logger.log("上传历史已清除")
    }
}

/**
 * 上传结果
 */
@Serializable
data class UploadResult(
    val success: Boolean,
    val message: String,
    val uploadId: String?
)

/**
 * 文件上传事件
 */
@Serializable
data class FileUploadEvent(
    val uploadId: String,
    val fileInfo: Map<String, Any>,
    val status: UploadStatus,
    val timestamp: Long,
    val result: UploadResult? = null
)

/**
 * 上传状态
 */
@Serializable
enum class UploadStatus {
    PENDING,
    UPLOADING,
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * AI窗口事件总线
 * 用于与rikkahub的AI窗口进行通信
 */
object AIWindowEventBus {
    
    private val _fileContentEvents = MutableSharedFlow<FileContentEvent>(replay = 10)
    val fileContentEvents = _fileContentEvents.asSharedFlow()
    
    /**
     * 发送文件内容到AI窗口
     */
    suspend fun sendFileContent(content: String, autoSend: Boolean) {
        val event = FileContentEvent(
            id = UUID.randomUUID().toString(),
            content = content,
            autoSend = autoSend,
            timestamp = System.currentTimeMillis()
        )
        _fileContentEvents.emit(event)
    }
    
    /**
     * 发送图片到AI窗口
     */
    suspend fun sendImage(imagePath: String, autoSend: Boolean = true) {
        val event = FileContentEvent(
            id = UUID.randomUUID().toString(),
            content = "![图片]($imagePath)",
            autoSend = autoSend,
            timestamp = System.currentTimeMillis(),
            type = ContentType.IMAGE
        )
        _fileContentEvents.emit(event)
    }
}

/**
 * 文件内容事件
 */
@Serializable
data class FileContentEvent(
    val id: String,
    val content: String,
    val autoSend: Boolean,
    val timestamp: Long,
    val type: ContentType = ContentType.TEXT
)

/**
 * 内容类型
 */
@Serializable
enum class ContentType {
    TEXT,
    IMAGE,
    FILE
} 