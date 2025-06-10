// 文件路径：app/src/main/java/android/zero/mcp/LocalMcpServer.kt
package android.zero.mcp

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.serialization.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * MCP 请求数据模型
 */
@Serializable
data class McpRequest(val type: String, val args: Map<String, String>)

/**
 * MCP 响应数据模型，用于通过 SSE 推送事件
 */
@Serializable
data class McpResponse(val event: String, val data: String)

/**
 * 根据请求类型，分发给对应 Handler
 */
object HandlerRouter {
    private val fileHandler = FileHandler()
    private val gradleHandler = GradleHandler()
    private val shellHandler = ShellHandler()

    suspend fun handle(request: McpRequest, channel: SendChannel<McpResponse>) {
        when (request.type) {
            "file.searchName"         -> fileHandler.handleSearchByName(request.args, channel)
            "file.searchContent"      -> fileHandler.handleSearchByContent(request.args, channel)
            "file.listModuleIncludes" -> fileHandler.handleListIncludes(request.args, channel)
            "file.listModuleFiles"    -> fileHandler.handleListModuleFiles(request.args, channel)
            "file.create"             -> fileHandler.handleCreate(request.args, channel)
            "file.write"              -> fileHandler.handleWrite(request.args, channel)

            "task.list"               -> gradleHandler.handleListTasks(request.args, channel)
            "task.execute"            -> gradleHandler.handleExecuteTask(request.args, channel)
            "gradle.execute"          -> gradleHandler.handleExecuteCommand(request.args, channel)

            "shell.execute"           -> shellHandler.handleExecuteCommand(request.args, channel)

            else -> channel.send(McpResponse("error", "Unknown command: ${request.type}"))
        }
        channel.close()
    }
}

/**
 * 本地 Ktor SSE 服务端，用以接收 MCPRequest 并通过 /sse SSE 推送 McpResponse
 */
class LocalMcpServer(
    private val projectRoot: File,
    private val port: Int = 11583
) {
    private var server: NettyApplicationEngine? = null

    fun start() {
        server = embeddedServer(Netty, port) {
            install(ContentNegotiation) { json() }
            install(CORS) { anyHost() }
            routing {
                // SSE 推送接口
                get("/sse") {
                    call.response.header(HttpHeaders.CacheControl, "no-cache")
                    call.response.header(HttpHeaders.ContentType, ContentType.Text.EventStream)
                    val channel = Channel<McpResponse>(Channel.UNLIMITED)
                    val job = launch {
                        for (resp in channel) {
                            val payload = "event: ${resp.event}\n" +
                                          "data: ${resp.data}\n\n"
                            call.response.writeText(payload, flush = true)
                        }
                    }
                    job.invokeOnCompletion { channel.close() }
                    job.join()
                }
                // 接收 MCP 请求
                post("/mcp") {
                    val req = call.receive<McpRequest>()
                    val eventChannel = Channel<McpResponse>(Channel.UNLIMITED)
                    launch(Dispatchers.IO) { HandlerRouter.handle(req, eventChannel) }
                    call.respond(HttpStatusCode.OK, mapOf("status" to "accepted"))
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
    }
}
