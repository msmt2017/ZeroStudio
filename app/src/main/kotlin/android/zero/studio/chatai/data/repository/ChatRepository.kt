package android.zero.studio.chatai.data.repository

import android.zero.studio.chatai.data.database.entity.ChatRoom
import android.zero.studio.chatai.data.database.entity.Message
import android.zero.studio.chatai.data.dto.ApiState
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    suspend fun completeOpenAIChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeAnthropicChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeGoogleChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeGroqChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun completeOllamaChat(question: Message, history: List<Message>): Flow<ApiState>
    suspend fun fetchChatList(): List<ChatRoom>
    suspend fun fetchMessages(chatId: Int): List<Message>
    fun generateDefaultChatTitle(messages: List<Message>): String?
    suspend fun updateChatTitle(chatRoom: ChatRoom, title: String)
    suspend fun saveChat(chatRoom: ChatRoom, messages: List<Message>): ChatRoom
    suspend fun deleteChats(chatRooms: List<ChatRoom>)
}
