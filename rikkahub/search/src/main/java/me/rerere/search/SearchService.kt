package me.rerere.search

import androidx.compose.runtime.Composable
import java.util.UUID // 使用稳定且受支持的 java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.internal.closeQuietly
import okio.IOException
import kotlin.coroutines.resumeWithException

// 为 java.util.UUID 提供一个明确的序列化器，以保证最大兼容性
object UuidSerializer : KSerializer<UUID> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

interface SearchService<T : SearchServiceOptions> {
    val name: String

    @Composable
    fun Description()

    suspend fun search(
        query: String,
        commonOptions: SearchCommonOptions,
        serviceOptions: T
    ): Result<SearchResult>

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <T : SearchServiceOptions> getService(options: T): SearchService<T> {
            return when (options) {
                is SearchServiceOptions.TavilyOptions -> TavilySearchService
                is SearchServiceOptions.ExaOptions -> ExaSearchService
                is SearchServiceOptions.ZhipuOptions -> ZhipuSearchService
                is SearchServiceOptions.BingLocalOptions -> BingSearchService
                is SearchServiceOptions.SearXNGOptions -> SearXNGService
                is SearchServiceOptions.LinkUpOptions -> LinkUpService
                is SearchServiceOptions.BraveOptions -> BraveSearchService
                else -> throw IllegalArgumentException("Unsupported search service options: ${options::class.simpleName}")
            } as SearchService<T>
        }

        internal val httpClient by lazy {
            OkHttpClient.Builder()
                .build()
        }

        internal val json by lazy {
            Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            }
        }
    }
}

@Serializable
data class SearchCommonOptions(
    val resultSize: Int = 10
)

@Serializable
data class SearchResult(
    val answer: String? = null,
    val items: List<SearchResultItem>,
) {
    @Serializable
    data class SearchResultItem(
        val title: String,
        val url: String,
        val text: String,
    )
}

@Serializable
sealed class SearchServiceOptions {
    @Serializable(with = UuidSerializer::class)
    abstract val id: UUID

    companion object {
        val DEFAULT = BingLocalOptions()

        val TYPES = mapOf(
            BingLocalOptions::class to "Bing",
            ZhipuOptions::class to "智谱",
            TavilyOptions::class to "Tavily",
            ExaOptions::class to "Exa",
            SearXNGOptions::class to "SearXNG",
            LinkUpOptions::class to "LinkUp",
            BraveOptions::class to "Brave"
        )
    }

    @Serializable
    @SerialName("bing_local")
    class BingLocalOptions(
        // FIX: 在每个子类的 override 属性上都添加注解
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID()
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("zhipu")
    data class ZhipuOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("tavily")
    data class TavilyOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("exa")
    data class ExaOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val apiKey: String = ""
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("searxng")
    data class SearXNGOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val url: String = "",
        val engines: String = "",
        val language: String = "",
        val username: String = "",
        val password: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("linkup")
    data class LinkUpOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val apiKey: String = "",
    ) : SearchServiceOptions()

    @Serializable
    @SerialName("brave")
    data class BraveOptions(
        @Serializable(with = UuidSerializer::class)
        override val id: UUID = UUID.randomUUID(),
        val apiKey: String = "",
    ) : SearchServiceOptions()
}

internal suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) {
                    continuation.resumeWithException(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _ ->
                    response.closeQuietly()
                }
            }
        })
    }
}