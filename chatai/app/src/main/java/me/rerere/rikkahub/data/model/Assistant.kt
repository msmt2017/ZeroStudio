package me.rerere.rikkahub.data.model

import kotlinx.serialization.Serializable
import me.rerere.ai.provider.CustomBody
import me.rerere.ai.provider.CustomHeader
import kotlin.uuid.Uuid

@Serializable
data class Assistant(
    val id: Uuid = Uuid.random(),
    val name: String = "",
    val systemPrompt: String = "",
    val temperature: Float = 0.6f,
    val topP: Float = 1.0f,
    val contextMessageSize: Int = 32,
    val streamOutput: Boolean = true,
    val enableMemory: Boolean = false,
    val enableMessageTime: Boolean = false,
    val thinkingBudget: Int? = null,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
)

@Serializable
data class AssistantMemory(
    val id: Int,
    val content: String = "",
)