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
import me.rerere.search.SearchResult.SearchResultItem
import me.rerere.search.SearchService.Companion.httpClient
import me.rerere.search.SearchService.Companion.json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

object ExaSearchService : SearchService<SearchServiceOptions.ExaOptions> {
    override val name: String = "Exa"

    // --- FIX: Define a serializable data class for the request body ---
    @Serializable
    private data class ExaRequestBody(
        val query: String,
        val numResults: Int,
        val contents: RequestContents
    )

    @Serializable
    private data class RequestContents(
        val text: Boolean = true
    )
    // --- End of FIX ---

    @Composable
    override fun Description() {
        val urlHandler = LocalUriHandler.current
        TextButton(
            onClick = {
                urlHandler.openUri("https://dashboard.exa.ai/api-keys")
            }
        ) {
            Text(stringResource(R.string.click_to_get_api_key))
        }
    }

    override suspend fun search(
        query: String,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.ExaOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            // --- FIX: Create an instance of the data class instead of building JsonObject manually ---
            val requestBody = ExaRequestBody(
                query = query,
                numResults = commonOptions.resultSize,
                contents = RequestContents(text = true)
            )
            // --- End of FIX ---

            val request = Request.Builder()
                .url("https://api.exa.ai/search")
                // --- FIX: Encode the data class instance directly ---
                .post(json.encodeToString(ExaRequestBody.serializer(), requestBody).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${serviceOptions.apiKey}")
                // Add required headers for Exa API
                .addHeader("x-api-key", serviceOptions.apiKey)
                .addHeader("Content-Type", "application/json")
                .build()

            // The rest of the function remains the same
            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyRaw = response.body?.string() ?: error("Failed to get response body")
                val responseData = runCatching { // Renamed 'response' to 'responseData' to avoid shadowing
                    json.decodeFromString<ExaData>(bodyRaw)
                }.onFailure {
                    it.printStackTrace()
                    println(bodyRaw)
                    error("Failed to decode response: $bodyRaw")
                }.getOrThrow()

                return@withContext Result.success(
                    SearchResult(
                        items = responseData.results.map {
                            SearchResultItem(
                                title = it.title.ifBlank { "No Title" }, // Add a fallback for blank titles
                                url = it.url,
                                text = it.text
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
    
    // The response data classes remain unchanged
    @Serializable
    data class ExaData(
        val results: List<ExaResult>,
        // Other fields can be marked as nullable or have default values if not always present
        val requestId: String? = null,
        val autopromptString: String? = null,
        val resolvedSearchType: String? = null,
        val costDollars: ExaCostDollars? = null
    )

    @Serializable
    data class ExaResult(
        val id: String,
        val title: String,
        val url: String,
        val text: String,
        val publishedDate: String? = null,
        val author: String? = null,
    )

    @Serializable
    data class ExaCostDollars(
        val total: Double,
        val search: ExaSearchCost,
        val contents: ExaContentsCost
    )

    @Serializable
    data class ExaSearchCost(
        val neural: Double
    )

    @Serializable
    data class ExaContentsCost(
        val text: Double
    )
}