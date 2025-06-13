// File: LocalMcpServer.kt
package android.zero.mcp

import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Collections
import java.util.UUID
import io.ktor.utils.io.writeStringUtf8

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
    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val json = Json { encodeDefaults = true }
    private val contextManager = ContextManager()
    private val interpreter = Interpreter(contextManager)
    private val operator = Operator(contextManager)
    private val scope = CoroutineScope(Dispatchers.IO)

    private val clients = Collections.synchronizedSet(mutableSetOf<Channel<McpResponse>>())

    fun start() {
        server = embeddedServer(Netty, port) {
            install(ContentNegotiation) {
                json()
            }
            install(CORS) {
                anyHost()
                allowHeader(HttpHeaders.ContentType)
                allowMethod(HttpMethod.Post)
                allowMethod(HttpMethod.Get)
            }
            routing {
                get("/sse") {
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream.toString())
                    val eventChannel = Channel<McpResponse>(Channel.UNLIMITED)
                    clients.add(eventChannel)

                    try {
                        call.respondBytesWriter(ContentType.Text.EventStream) {
                            for (resp in eventChannel) {
                                val payload = "event: message\n" +
                                        "data: ${json.encodeToString(resp)}\n\n"
                                writeStringUtf8(payload)
                                flush()
                            }
                        }
                    } finally {
                        clients.remove(eventChannel)
                        eventChannel.close()
                    }
                }
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
        val ctxId = req.contextId ?: contextManager.createContext()
        val updatedArgs = req.args.toMutableMap().apply {
            put("id", req.id)
            ctxId?.let { put("contextId", it) }
        }
        val cmd = interpreter.parse(req.copy(contextId = ctxId, args = updatedArgs))

        val resp: McpResponse = if (cmd == null) {
            McpResponse(req.id, "error", "Unknown command type: ${req.type}", null, ctxId)
        } else {
            try {
                // FIX: Updated the 'when' block to check for the new top-level command classes.
                val result = when (cmd) {
                    is FileCommand -> operator.execute(ExecuteCommand(req.id, ctxId, "file.${cmd.action}", cmd.args))
                    is ShellCommand -> operator.execute(ExecuteCommand(req.id, ctxId, "shell.execute", mapOf("command" to cmd.command)))
                    is GradleCommand -> operator.execute(ExecuteCommand(req.id, ctxId, "gradle.${cmd.task}", emptyMap()))
                    is TaskCommand -> operator.execute(ExecuteCommand(req.id, ctxId, "task.${cmd.action}", cmd.args))
                    is TabFileCommand -> operator.execute(ExecuteCommand(req.id, ctxId, "tabfile.${cmd.action}", cmd.args))
                    is QueryCommand -> "Query commands are not directly executable by Operator in this context."
                    // The 'else' case handles any other Command subtypes, including the generic ExecuteCommand.
                    else -> "Unsupported command type: ${cmd::class.simpleName}"
                }
                McpResponse(req.id, "response.success", result, null, ctxId)
            } catch (e: Exception) {
                McpResponse(req.id, "response.error", e.localizedMessage ?: "Unknown error during execution", null, ctxId)
            }
        }
        broadcast(resp)
    }

    private fun broadcast(resp: McpResponse) {
        clients.forEach { ch ->
            ch.trySend(resp).getOrThrow()
        }
    }

    fun stop() {
        server?.stop(1000, 2000)
        scope.cancel()
    }
}