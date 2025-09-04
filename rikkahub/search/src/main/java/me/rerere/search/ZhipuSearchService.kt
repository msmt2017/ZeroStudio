package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
// buildJsonObject and JsonPrimitive are no longer needed for request
// import kotlinx.serialization.json.JsonPrimitive
// import kotlinx.serialization.json.buildJsonObject
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ZhipuSearchService : SearchService<SearchServiceOptions.ZhipuOptions> {
    override val name: String = "Zhipu"

    // --- FIX: Define a serializable data class for the request body ---
    @Serializable
    private data class ZhipuRequestBody(
        @SerialName("search_query")
        val searchQuery: String,
        @SerialName("search_engine")
        val searchEngine: String = "search_std"
    )
    // --- End of FIX ---

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://bigmodel.cn/usercenter/proj-mgmt/apikeys")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override suspend fun search(
        query: String,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.ZhipuOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            // --- FIX: Create an instance of the data class ---
            val requestBody = ZhipuRequestBody(
                searchQuery = query
            )
            // --- End of FIX ---

            val request = Request.Builder()
                .url("https://open.bigmodel.cn/api/paas/v4/web_search")
                // --- FIX: Encode the data class instance directly with its serializer ---
                .post(json.encodeToString(ZhipuRequestBody.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${serviceOptions.apiKey}")
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyRaw = response.body?.string() ?: error("Failed to get response body")
                // Renamed 'response' to 'responseData' to avoid shadowing
                val responseData = runCatching {
                    json.decodeFromString<ZhipuDto>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println(bodyRaw)
                    error("Failed to decode response: $bodyRaw")
                }.getOrThrow()

                return@withContext Result.success(
                    SearchResult(
                        items = responseData.searchResult.map {
                            SearchResultItem(
                                title = it.title,
                                url = it.link,
                                text = it.content,
                            )
                        }
                    )
                )
            } else {
                val errorBody = response.body?.string()
                println("Request failed with code ${response.code}: $errorBody")
                error("response failed #${response.code} - Body: $errorBody")
            }
        }
    }

    // Response DTOs remain the same
    @Serializable
    data class ZhipuDto(
        @SerialName("search_result")
        val searchResult: List<ZhipuSearchResultDto>
    )

    @Serializable
    data class ZhipuSearchResultDto(
        @SerialName("content")
        val content: String,
        @SerialName("icon")
        val icon: String?,
        @SerialName("link")
        val link: String,
        @SerialName("media")
        val media: String?,
        @SerialName("refer")
        val refer: String?,
        @SerialName("title")
        val title: String
    )
}