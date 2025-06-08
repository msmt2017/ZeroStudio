package me.rerere.ai.provider

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val favorite: Boolean = false,
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
)

@Serializable
enum class ModelType {
    CHAT,
    EMBEDDING
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

fun guessModalityFromModelId(modelId: String): Pair<List<Modality>, List<Modality>> {
    return when {
        GPT4O.containsMatchIn(modelId) || GPT_4_1.containsMatchIn(modelId) -> {
            listOf(Modality.TEXT, Modality.IMAGE) to listOf(Modality.TEXT)
        }

        GEMINI_20_FLASH.containsMatchIn(modelId) -> {
            listOf(Modality.TEXT, Modality.IMAGE) to listOf(Modality.TEXT)
        }

        CLAUDE_SONNET_3_5.containsMatchIn(modelId) || CLAUDE_SONNET_3_7.containsMatchIn(modelId) || CLAUDE_4.containsMatchIn(modelId) -> {
            listOf(Modality.TEXT, Modality.IMAGE) to listOf(Modality.TEXT)
        }

        else -> {
            listOf(Modality.TEXT) to listOf(Modality.TEXT)
        }
    }
}

fun guessModelAbilityFromModelId(modelId: String): List<ModelAbility> {
    return when {
        GPT4O.containsMatchIn(modelId) || GPT_4_1.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL)
        }

        GEMINI_20_FLASH.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL)
        }

        GEMINI_2_5_FLASH.containsMatchIn(modelId) || GEMINI_2_5_PRO.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL, ModelAbility.REASONING)
        }

        CLAUDE_SONNET_3_5.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL)
        }

        CLAUDE_SONNET_3_7.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL, ModelAbility.REASONING)
        }

        CLAUDE_4.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL, ModelAbility.REASONING)
        }

        QWEN_3.containsMatchIn(modelId) -> {
            listOf(ModelAbility.TOOL, ModelAbility.REASONING)
        }

        else -> {
            emptyList()
        }
    }
}

private val GPT4O = Regex("gpt-4o")
private val GPT_4_1 = Regex("gpt-4\\.1")
private val GEMINI_20_FLASH = Regex("gemini-2.0-flash")
private val GEMINI_2_5_FLASH = Regex("gemini-2.5-flash")
private val GEMINI_2_5_PRO = Regex("gemini-2.5-pro")
private val CLAUDE_SONNET_3_5 = Regex("claude-3.5-sonnet")
private val CLAUDE_SONNET_3_7 = Regex("claude-3.7-sonnet")
private val CLAUDE_4 = Regex("claude-4")
private val QWEN_3 = Regex("qwen-?3")