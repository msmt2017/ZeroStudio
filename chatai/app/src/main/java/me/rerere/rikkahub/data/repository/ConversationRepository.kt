package me.rerere.rikkahub.data.repository

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.entity.ConversationEntity
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.convertBase64ImagePartToLocalFile
import me.rerere.rikkahub.utils.deleteAllChatFiles
import me.rerere.rikkahub.utils.deleteChatFiles
import java.time.Instant
import kotlin.uuid.Uuid

class ConversationRepository(
    private val context: Context,
    private val conversationDAO: ConversationDAO,
) {
    fun getAllConversations(): Flow<List<Conversation>> = conversationDAO
        .getAll()
        .map { flow ->
            flow.map { entity ->
                conversationEntityToConversation(entity)
            }
        }

    fun getConversationsOfAssistant(assistantId: Uuid): Flow<List<Conversation>> {
        return conversationDAO
            .getConversationsOfAssistant(assistantId.toString())
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    fun searchConversations(titleKeyword: String): Flow<List<Conversation>> {
        return conversationDAO
            .searchConversations(titleKeyword)
            .map { flow ->
                flow.map { entity ->
                    conversationEntityToConversation(entity)
                }
            }
    }

    suspend fun getConversationById(uuid: Uuid): Conversation? {
        val entity = conversationDAO.getConversationById(uuid.toString())
        return if (entity != null) {
            conversationEntityToConversation(entity)
        } else null
    }

    suspend fun upsertConversation(conversation: Conversation) {
        if(getConversationById(conversation.id) !=  null) {
            updateConversation(conversation)
        } else {
            insertConversation(conversation)
        }
    }

    suspend fun insertConversation(conversation: Conversation) {
        conversationDAO.insert(
            conversationToConversationEntity(conversation)
        )
    }

    suspend fun updateConversation(conversation: Conversation) {
        conversationDAO.update(
            conversationToConversationEntity(conversation)
        )
    }

    suspend fun deleteConversation(conversation: Conversation) {
        conversationDAO.delete(
            conversationToConversationEntity(conversation)
        )
        context.deleteChatFiles(conversation.files)
    }

    suspend fun deleteConversationOfAssistant(assistantId: Uuid) {
        getConversationsOfAssistant(assistantId).first().forEach { conversation ->
            deleteConversation(conversation)
        }
    }

    fun conversationToConversationEntity(conversation: Conversation): ConversationEntity {
        return ConversationEntity(
            id = conversation.id.toString(),
            title = conversation.title,
            messages = JsonInstant.encodeToString(conversation.messages),
            createAt = conversation.createAt.toEpochMilli(),
            updateAt = conversation.updateAt.toEpochMilli(),
            tokenUsage = conversation.tokenUsage,
            assistantId = conversation.assistantId.toString()
        )
    }

    fun conversationEntityToConversation(conversationEntity: ConversationEntity): Conversation {
        return Conversation(
            id = Uuid.parse(conversationEntity.id),
            title = conversationEntity.title,
            messages = JsonInstant.decodeFromString<List<UIMessage>>(conversationEntity.messages),
            tokenUsage = conversationEntity.tokenUsage,
            createAt = Instant.ofEpochMilli(conversationEntity.createAt),
            updateAt = Instant.ofEpochMilli(conversationEntity.updateAt),
            assistantId = Uuid.parse(conversationEntity.assistantId)
        )
    }

    suspend fun deleteAllConversations() {
        conversationDAO.deleteAll()
        context.deleteAllChatFiles()
    }
}