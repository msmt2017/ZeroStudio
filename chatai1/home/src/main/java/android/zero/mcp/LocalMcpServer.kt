package android.zero.mcp

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.* // Correct import for ContentNegotiation
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.serialization.kotlinx.json.* // Correct import for json()
import io.ktor.server.plugins.cors.routing.* // Correct import for CORS
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import android.zero.mcp.model.McpResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections // Correct import for Collections
import java.util.UUID

@Serializable
data class McpRequest(
    val id: String,
    val type: String,
    val contextId: String? = null,
    val args: Map<String, String>
)

@Serializable
data class McpResponse(
    val id: String,
    val event: String,
    val result: String,
    val error: String? = null,
    val contextId: String? = null
)

class LocalMcpServer(private val projectRoot: File, private val port: Int = 11583) {
    private var server: EmbeddedServer<*, *>? = null
    private val json = Json { encodeDefaults = true }
    private val contextManager = ContextManager()
    private val interpreter = Interpreter(contextManager)
    private val operator = Operator(contextManager) // This will need the fixed Operator.kt
    private val scope = CoroutineScope(Dispatchers.IO)

    // Simple example: save all eventChannels in memory to push to all clients
    private val clients = Collections.synchronizedSet(mutableSetOf<Channel<McpResponse>>())

    fun start() {
        server = embeddedServer(Netty, port) {
            install(ContentNegotiation) {
                json() // Use the kotlinx.serialization.json extension
            }
            install(CORS) {
                anyHost() // Use anyHost extension function
                allowHeader(HttpHeaders.ContentType) // Allow Content-Type header for POST requests
                allowMethod(HttpMethod.Post) // Allow POST requests
                allowMethod(HttpMethod.Get) // Allow GET requests for SSE
            }
            routing {
                // SSE endpoint
                get("/sse") {
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()) // Convert ContentType to String
                    val eventChannel = Channel<McpResponse>(Channel.UNLIMITED)
                    clients.add(eventChannel) // Add channel to clients

                    // Coroutine for each client
                    try {
                        call.respondBytesWriter(ContentType.Text.EventStream) {
                            for (resp in eventChannel) {
                                val payload = "event: message\n" +
                                        "data: ${json.encodeToString(resp)}\n\n"
                                write(payload.toByteArray())
                                flush()
                            }
                        }
                    } finally {
                        clients.remove(eventChannel) // Remove channel when client disconnects
                        eventChannel.close()
                    }
                }
                // Receive MCP requests
                post("/mcp") {
                    val req = call.receive<McpRequest>()
                    scope.launch {
                        handleRequest(req)
                    }
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }.start(wait = false)
    }

    private suspend fun handleRequest(req: McpRequest) {
        // 1. If no contextId, create one
        val ctxId = req.contextId ?: contextManager.createContext()
        // 2. Add id/contextId back to args, for Operator use
        val updatedArgs = req.args.toMutableMap().apply {
            put("id", req.id)
            ctxId?.let { put("contextId", it) }
        }
        // 3. Parse command
        val cmd = interpreter.parse(req.copy(contextId = ctxId, args = updatedArgs))

        val resp: McpResponse = if (cmd == null) {
            McpResponse(req.id, "error", "Unknown command type: ${req.type}", null, ctxId)
        } else {
            try {
                // Operator.execute expects ExecuteCommand, so cast if it's an ExecuteCommand
                val result = when (cmd) {
                    is ExecuteCommand -> operator.execute(cmd)
                    is QueryCommand -> "Query commands are not directly executable by Operator in this context." // Example handling
                    else -> "Unsupported command type"
                }
                McpResponse(req.id, "response.success", result, null, ctxId)
            } catch (e: Exception) {
                McpResponse(UUID.randomUUID().toString(), "error", e.localizedMessage ?: "Unknown error", null, ctxId)
            }
        }
        // 4. Push to all SSE clients
        broadcast(resp)
    }

    private fun broadcast(resp: McpResponse) {
        // Iterate over a copy to avoid ConcurrentModificationException
        clients.forEach { ch ->
            ch.trySend(resp).getOrThrow() // Use getOrThrow to propagate errors for debugging
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        scope.cancel()
    }
}
