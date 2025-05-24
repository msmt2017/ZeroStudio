package android.zero.studio.chatai.data.network

import android.zero.studio.chatai.data.dto.anthropic.request.MessageRequest
import android.zero.studio.chatai.data.dto.anthropic.response.MessageResponseChunk
import kotlinx.coroutines.flow.Flow

interface AnthropicAPI {
    fun setToken(token: String?)
    fun setAPIUrl(url: String)
    fun streamChatMessage(messageRequest: MessageRequest): Flow<MessageResponseChunk>
}
