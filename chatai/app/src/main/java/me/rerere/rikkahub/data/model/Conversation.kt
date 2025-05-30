package me.rerere.rikkahub.data.model

import android.net.Uri
import androidx.core.net.toUri
import kotlinx.serialization.Serializable
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.util.InstantSerializer
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANT_ID
import java.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Conversation(
    val id: Uuid = Uuid.Companion.random(),
    val assistantId: Uuid,
    val title: String = "",
    val messages: List<UIMessage>,
    val tokenUsage: TokenUsage? = null,
    @Serializable(with = InstantSerializer::class)
    val createAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val updateAt: Instant = Instant.now(),
) {
    val files: List<Uri>
        get() = messages
            .flatMap { it.parts }
            .filterIsInstance<UIMessagePart.Image>()
            .mapNotNull {
                it.url.takeIf { it.startsWith("file://") }?.toUri()
            }

    companion object {
        fun ofId(id: Uuid, assistantId: Uuid = DEFAULT_ASSISTANT_ID, messages: List<UIMessage> = emptyList()) = Conversation(
            id = id,
            messages = messages,
            assistantId = assistantId
        )
    }
}