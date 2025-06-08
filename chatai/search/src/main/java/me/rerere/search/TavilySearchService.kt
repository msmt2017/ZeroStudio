package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "TavilySearchService"

object TavilySearchService : SearchService<SearchServiceOptions.TavilyOptions> {
    override val name: String = "Tavily"

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://app.tavily.com/home")
            }
        ) {
            Text("点击获取API Key")
        }
    }

    override suspend fun search(
        query: String,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.TavilyOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJsonObject {
                put("query", JsonPrimitive(query))
                put("max_results", JsonPrimitive(commonOptions.resultSize))
            }
            val request = Request.Builder()
                .url("https://api.tavily.com/search")
                .post(body.toString().toRequestBody())
                .addHeader("Authorization", "Bearer ${serviceOptions.apiKey}")
                .build()
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val response = response.body?.string()?.let {
                    json.decodeFromString<SearchResponse>(it)
                } ?: error("Failed to parse response")

                return@withContext Result.success(
                    SearchResult(
                        items = response.results.map {
                            SearchResultItem(
                                title = it.title,
                                url = it.url,
                                text = it.content
                            )
                        }
                    ))
            } else {
                error("response failed #${response.code}")
            }
        }
    }

    @Serializable
    data class SearchResponse(
        val query: String,
        val followUpQuestions: String? = null,
        val answer: String? = null,
        val images: List<String> = emptyList(),
        val results: List<ResultItem>,
    )

    @Serializable
    data class ResultItem(
        val title: String,
        val url: String,
        val content: String,
        val score: Double,
        val rawContent: String? = null
    )
}