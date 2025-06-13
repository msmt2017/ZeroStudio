package android.zero.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID

class McpClient(
    private val baseUrl: String = "http://127.0.0.1:11583",
    private val client: OkHttpClient = OkHttpClient()
) {
    private val json = Json { ignoreUnknownKeys = true }
    val coroutineScope = CoroutineScope(Dispatchers.IO)

    fun sendCommand(type: String, args: Map<String, String>, contextId: String? = null): Flow<McpResponse> {
        val id = UUID.randomUUID().toString()
        val req = McpRequest(id, type, contextId, args)

        val jsonBody = json.encodeToString(req)
        val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

        client.newCall(Request.Builder()
            .url("$baseUrl/mcp")
            .post(requestBody)
            .build()).enqueue(object: Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                coroutineScope.launch {
                    val errorResp = McpResponse(id, "request.error", "", e.localizedMessage ?: "Network error", contextId)
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

    fun listenSse(): Flow<McpResponse> = callbackFlow {
        val request = Request.Builder().url("$baseUrl/sse").build()
        val source: EventSource = EventSources.createFactory(client)
            .newEventSource(request, object: EventSourceListener() {
                override fun onEvent(es: EventSource, id: String?, type: String?, data: String) {
                    try {
                        val resp = json.decodeFromString<McpResponse>(data)
                        trySend(resp).getOrThrow()
                    } catch (e: Exception) {
                        println("Error decoding SSE event: $e")
                        trySend(McpResponse(id ?: "", "sse.parse.error", "", e.localizedMessage ?: "Parsing error")).getOrThrow()
                    }
                }
                override fun onFailure(es: EventSource, t: Throwable?, resp: Response?) {
                    println("SSE connection failed: ${t?.localizedMessage ?: "Unknown"}")
                    channel.close(t)
                }

                override fun onClosed(eventSource: EventSource) {
                    println("SSE connection closed.")
                    channel.close()
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
        client.dispatcher.executorService.shutdown()
    }
}