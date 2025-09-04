package me.rerere.ai.provider

import androidx.compose.runtime.Composable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.util.UUID // FIX 1: 使用 java.util.UUID

// FIX 2: 提供一个自定义序列化器，这是兼容性所必需的
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

@Serializable
sealed class ProviderProxy {
    @Serializable
    @SerialName("none")
    object None : ProviderProxy()

    @Serializable
    @SerialName("http")
    data class Http(
        val address: String,
        val port: Int,
        val username: String? = null,
        val password: String? = null,
    ) : ProviderProxy()
}

@Serializable
sealed class ProviderSetting {
    // FIX 1 & 2: 更改类型并应用序列化器
    @Serializable(with = UuidSerializer::class)
    abstract val id: UUID
    abstract val enabled: Boolean
    abstract val name: String
    abstract val models: List<Model>
    abstract val proxy: ProviderProxy

    @Transient // 您原来的代码中没有 @Transient，但对于非序列化字段这是正确的做法
    abstract val builtIn: Boolean
    @Transient // 对于 Composable lambda 也是如此
    abstract val description: @Composable() () -> Unit

    abstract fun addModel(model: Model): ProviderSetting
    abstract fun editModel(model: Model): ProviderSetting
    abstract fun delModel(model: Model): ProviderSetting
    abstract fun moveMove(from: Int, to: Int): ProviderSetting
    abstract fun copyProvider(
        id: UUID = this.id, // FIX 1: 更改类型
        enabled: Boolean = this.enabled,
        name: String = this.name,
        models: List<Model> = this.models,
        proxy: ProviderProxy = this.proxy,
        builtIn: Boolean = this.builtIn,
        description: @Composable (() -> Unit) = this.description,
    ): ProviderSetting

    @Serializable
    @SerialName("openai")
    data class OpenAI(
        // FIX 3: 必须使用 override val，而不是 var。这是 Kotlin 语法规则。
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        override val enabled: Boolean = true,
        override val name: String = "OpenAI",
        override val models: List<Model> = emptyList(),
        override val proxy: ProviderProxy = ProviderProxy.None,
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        val apiKey: String = "",
        val baseUrl: String = "https://api.openai.com/v1",
        val chatCompletionsPath: String = "/chat/completions",
        val useResponseApi: Boolean = false,
    ) : ProviderSetting() {
        // ... 方法实现保持不变，但为了简洁，可以写成表达式形式
        override fun addModel(model: Model): ProviderSetting = copy(models = models + model)
        override fun editModel(model: Model): ProviderSetting = copy(models = models.map { if (it.id == model.id) model.copy() else it })
        override fun delModel(model: Model): ProviderSetting = copy(models = models.filter { it.id != model.id })
        override fun moveMove(from: Int, to: Int): ProviderSetting = copy(models = models.toMutableList().apply { add(to, removeAt(from)) })
        override fun copyProvider(id: UUID, enabled: Boolean, name: String, models: List<Model>, proxy: ProviderProxy, builtIn: Boolean, description: @Composable (() -> Unit)): ProviderSetting {
            return this.copy(id = id, enabled = enabled, name = name, models = models, builtIn = builtIn, description = description, proxy = proxy)
        }
    }

    @Serializable
    @SerialName("google")
    data class Google(
        // FIX 3: 必须使用 override val
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        override val enabled: Boolean = true,
        override val name: String = "Google",
        override val models: List<Model> = emptyList(),
        override val proxy: ProviderProxy = ProviderProxy.None,
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        val apiKey: String = "",
        val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
        val vertexAI: Boolean = false,
        val location: String = "us-central1",
        val projectId: String = "",
    ) : ProviderSetting() {
        // ... 方法实现保持不变
        override fun addModel(model: Model): ProviderSetting = copy(models = models + model)
        override fun editModel(model: Model): ProviderSetting = copy(models = models.map { if (it.id == model.id) model else it })
        override fun delModel(model: Model): ProviderSetting = copy(models = models.filter { it.id != model.id })
        override fun moveMove(from: Int, to: Int): ProviderSetting = copy(models = models.toMutableList().apply { add(to, removeAt(from)) })
        override fun copyProvider(id: UUID, enabled: Boolean, name: String, models: List<Model>, proxy: ProviderProxy, builtIn: Boolean, description: @Composable (() -> Unit)): ProviderSetting {
            return this.copy(id = id, enabled = enabled, name = name, models = models, builtIn = builtIn, description = description, proxy = proxy)
        }
    }

    @Serializable
    @SerialName("claude")
    data class Claude(
        // FIX 3: 必须使用 override val
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        override val enabled: Boolean = true,
        override val name: String = "Claude",
        override val models: List<Model> = emptyList(),
        override val proxy: ProviderProxy = ProviderProxy.None,
        @Transient override val builtIn: Boolean = false,
        @Transient override val description: @Composable (() -> Unit) = {},
        val apiKey: String = "",
        val baseUrl: String = "https://api.anthropic.com/v1",
    ) : ProviderSetting() {
        // ... 方法实现保持不变
        override fun addModel(model: Model): ProviderSetting = copy(models = models + model)
        override fun editModel(model: Model): ProviderSetting = copy(models = models.map { if (it.id == model.id) model.copy() else it })
        override fun delModel(model: Model): ProviderSetting = copy(models = models.filter { it.id != model.id })
        override fun moveMove(from: Int, to: Int): ProviderSetting = copy(models = models.toMutableList().apply { add(to, removeAt(from)) })
        override fun copyProvider(id: UUID, enabled: Boolean, name: String, models: List<Model>, proxy: ProviderProxy, builtIn: Boolean, description: @Composable (() -> Unit)): ProviderSetting {
            return this.copy(id = id, enabled = enabled, name = name, models = models, builtIn = builtIn, description = description, proxy = proxy)
        }
    }

    // 警告: 此 companion object 之前导致了编译器插件冲突。如果 "Plugin generated companion object" 错误重现，请将其移出此类。
    companion object {
        val Types by lazy {
            listOf(
                OpenAI::class,
                Google::class,
                Claude::class,
            )
        }
    }
}