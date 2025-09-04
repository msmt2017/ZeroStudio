package me.rerere.ai.provider

import java.util.UUID // FIX 1: Import a stable UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Note: The UuidSerializer is now defined in ProviderSetting.kt

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    // FIX 5: Use UUID, apply the serializer, and use the correct random function
    @Serializable(with = UuidSerializer::class)
    val id: UUID = UUID.randomUUID(),
    val type: ModelType = ModelType.CHAT,
    // Assuming these data classes exist in other files
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    // ProviderSetting type is now resolved from the other file
    val providerOverwrite: ProviderSetting? = null,
)

// The definitions for these might be needed if they are not in other files
// For now, let's add them to make this file self-contained for compilation check
@Serializable
data class CustomHeader(val key: String, val value: String)

@Serializable
data class CustomBody(val key: String, val value: String)

@Serializable
enum class ModelType {
    CHAT,
    EMBEDDING,
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

@Serializable
sealed class BuiltInTools {
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()
}