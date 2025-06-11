package android.zero.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType // Correct import for toMediaType
import okhttp3.RequestBody.Companion.toRequestBody // Correct import for toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID

class McpClient(
    private val baseUrl: String = "http://127.0.0.1:11583",
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }
    val coroutineScope = CoroutineScope(Dispatchers.IO) // Expose a scope for launching flows

    /** Send command and return Flow of responses for that command ID */
    fun sendCommand(type: String, args: Map<String, String>, contextId: String? = null): Flow<McpResponse> {
        val id = UUID.randomUUID().toString() // Use java.util.UUID
        val req = McpRequest(id, type, contextId, args) // Args already contains id if needed by server side

        val jsonBody = json.encodeToString(McpRequest.serializer(), req) // Use serializer explicitly
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder()
            .url("$baseUrl/mcp")
            .post(requestBody)
            .build()).enqueue(object: Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                // Handle failure, e.g., send an error response through the flow
                coroutineScope.launch {
                    val errorResp = McpResponse(id, "request.error", "", e.localizedMessage ?: "Network error", contextId)
                    // If you want to push this to the main SSE flow too, you'd need a shared channel
                    // For now, it just prints.
                    println("MCP Request Failed: ${e.localizedMessage}")
                }
            }
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    coroutineScope.launch {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        val errorResp = McpResponse(id, "response.http.error", "", "HTTP ${response.code}: $errorBody", contextId)
                        println("MCP HTTP Error: ${response.code} - $errorBody")
                    }
                }
                response.close()
            }
        })
        return listenSse().filter { it.id == id }
    }

    /** Global SSE listener */
    fun listenSse(): Flow<McpResponse> = callbackFlow { // Made public
        val request = Request.Builder().url("$baseUrl/sse").build()
        val source: EventSource = EventSources.createFactory(client)
            .newEventSource(request, object: EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val resp = json.decodeFromString<McpResponse>(data) // Decode without explicit serializer
                        trySend(resp).getOrThrow() // Use getOrThrow to propagate errors
                    } catch (e: Exception) {
                        println("Error decoding SSE event: $e")
                        // Optionally send a parsing error response
                        trySend(McpResponse(id ?: "", "sse.parse.error", "", e.localizedMessage ?: "Parsing error")).getOrThrow()
                    }
                }
                override fun onFailure(es: EventSource, t: Throwable?, resp: Response?) {
                    println("SSE connection failed: ${t?.localizedMessage ?: "Unknown"}")
                    close(t) // Close the flow with the throwable
                }

                override fun onClosed(eventSource: EventSource) {
                    println("SSE connection closed.")
                    close() // Close the flow when SSE connection is closed
                }

                override fun onOpen(eventSource: EventSource, response: Response) {
                    println("SSE connection opened.")
                }
            })
        awaitClose {
            source.cancel()
            println("SSE listener cancelled.")
        }
    }

    fun close() {
        // Cancel the client's coroutine scope to stop any ongoing flows
        coroutineScope.cancel()
        // If there are other resources to clean up, add them here
        // For OkHttpClient, it manages its own connection pool
    }
}
