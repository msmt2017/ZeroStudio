package me.rerere.ai.provider.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.Provider
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.provider.providers.openai.ChatCompletionsAPI
import me.rerere.ai.provider.providers.openai.ResponseAPI
import me.rerere.ai.ui.MessageChunk
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.util.json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

object OpenAIProvider : Provider<ProviderSetting.OpenAI> {
  private val client = OkHttpClient.Builder()
    .connectTimeout(120, TimeUnit.SECONDS)
    .readTimeout(120, TimeUnit.SECONDS)
    .writeTimeout(120, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .addInterceptor { chain ->
      val request = chain.request().newBuilder()
        .addHeader("X-Title", "RikkaHub")
        .addHeader("HTTP-Referer", "https://rikka-ai.com")
        .build()
      chain.proceed(request)
    }
    .addInterceptor(HttpLoggingInterceptor().apply {
      level = HttpLoggingInterceptor.Level.HEADERS
    })
    .build()

  private val chatCompletionsAPI = ChatCompletionsAPI(client = client)
  private val responseAPI = ResponseAPI(client = client)

  override suspend fun listModels(providerSetting: ProviderSetting.OpenAI): List<Model> =
    withContext(Dispatchers.IO) {
      val request = Request.Builder()
        .url("${providerSetting.baseUrl}/models")
        .addHeader("Authorization", "Bearer ${providerSetting.apiKey}")
        .get()
        .build()

      val response = client.newCall(request).execute()
      if (!response.isSuccessful) {
        error("Failed to get models: ${response.code} ${response.body?.string()}")
      }

      val bodyStr = response.body?.string() ?: ""
      val bodyJson = json.parseToJsonElement(bodyStr).jsonObject
      val data = bodyJson["data"]?.jsonArray ?: return@withContext emptyList()

      data.mapNotNull { modelJson ->
        val modelObj = modelJson.jsonObject
        val id = modelObj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null

        Model(
          modelId = id,
          displayName = id,
        )
      }
    }

  override suspend fun streamText(
    providerSetting: ProviderSetting.OpenAI,
    messages: List<UIMessage>,
    params: TextGenerationParams
  ): Flow<MessageChunk> = if (providerSetting.useResponseApi) {
    responseAPI.streamText(
      providerSetting = providerSetting,
      messages = messages,
      params = params
    )
  } else {
    chatCompletionsAPI.streamText(
      providerSetting = providerSetting,
      messages = messages,
      params = params
    )
  }

  override suspend fun generateText(
    providerSetting: ProviderSetting.OpenAI,
    messages: List<UIMessage>,
    params: TextGenerationParams
  ): MessageChunk = if (providerSetting.useResponseApi) {
    responseAPI.generateText(
      providerSetting = providerSetting,
      messages = messages,
      params = params
    )
  } else {
    chatCompletionsAPI.generateText(
      providerSetting = providerSetting,
      messages = messages,
      params = params
    )
  }
}