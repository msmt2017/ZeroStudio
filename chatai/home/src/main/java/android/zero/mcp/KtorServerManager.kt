package com.example.ktormcpapp

import android.content.Context
import android.util.Log
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.InetAddress

/**
 * 管理 Ktor 服务器的启动和停止。
 */
class KtorServerManager {

    private var server: NettyApplicationEngine? = null
    private val TAG = "KtorServerManager"

    /**
     * 启动 Ktor 服务器。
     * @param port 监听的端口。
     * @param host IP 地址（例如 "0.0.0.0" 监听所有接口，或 "192.168.1.100" 特定 IP）。
     * @param onStarted 服务器成功启动时的回调。
     * @param onError 服务器启动失败时的回调。
     */
    fun startServer(
        port: Int,
        host: String = "0.0.0.0", // 默认监听所有可用网络接口，包括内网 IP
        onStarted: (String, Int) -> Unit, // 返回实际监听的 IP 和端口
        onError: (Throwable) -> Unit
    ) {
        if (server != null) {
            Log.w(TAG, "服务器已在运行。")
            onStarted(host, port) // 如果已经运行，直接回调成功
            return
        }

        try {
            // 初始化 MCP 服务
            McpService.initialize()

            server = embeddedServer(Netty, port = port, host = host) {
                // 配置内容协商，用于 JSON 序列化和反序列化
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    })
                }
                // 配置调用日志
                install(CallLogging)
                // 配置状态页，处理错误
                install(StatusPages) {
                    exception<Throwable> { call, cause ->
                        call.respondText(text = "500: ${cause.message}", status = HttpStatusCode.InternalServerError)
                        Log.e(TAG, "服务器错误: ${cause.message}", cause)
                    }
                }
                // 配置 SSE
                install(SSE)

                routing {
                    // MCP Host 信息端点
                    get("/mcp/host") {
                        val mcpInfo = McpService.getMcpHostInfo()
                        call.respond(mcpInfo)
                        Log.d(TAG, "/mcp/host 请求已处理")
                    }

                    // MCP 工具执行端点
                    post("/mcp/execute") {
                        try {
                            val request = call.receive<ToolExecutionRequest>()
                            Log.d(TAG, "收到 /mcp/execute 请求: $request")
                            val response = McpService.executeTool(request)
                            call.respond(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "处理 /mcp/execute 请求时出错: ${e.message}", e)
                            call.respond(HttpStatusCode.BadRequest, ToolExecutionResponse(false, "无效请求或参数: ${e.message}"))
                        }
                    }

                    // MCP 指令执行端点 (示例，可以合并到 /mcp/execute)
                    post("/mcp/command") {
                        try {
                            val request = call.receive<ToolExecutionRequest>() // 使用相同的请求结构
                            Log.d(TAG, "收到 /mcp/command 请求: $request")
                            val response = McpService.executeCommand(request.toolName, request.args)
                            call.respond(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "处理 /mcp/command 请求时出错: ${e.message}", e)
                            call.respond(HttpStatusCode.BadRequest, ToolExecutionResponse(false, "无效请求或参数: ${e.message}"))
                        }
                    }

                    // SSE (Server-Sent Events) 端点
                    // AI 可以连接此端点以获取实时更新
                    sse("/sse") {
                        // 在一个新的协程中收集 MCPService 的 SSE 事件流
                        // 确保这个协程在 SSE 连接关闭时也能被取消
                        launch {
                            McpService.sseEventsFlow.collect { eventData ->
                                Log.d(TAG, "向 SSE 客户端发送事件: $eventData")
                                // 将事件数据作为 SSE 消息发送。
                                // Ktor 的 SSE 插件会自动处理 'data' 和 'event' 字段的格式。
                                // 实际发送时，eventData 应该是类似 "event: my_event\ndata: some_data\n\n" 的格式
                                // 但我们这里直接发送 JSON 字符串，可以在客户端解析。
                                // 如果需要明确的 event 和 data 字段，需要在 eventData 中包含它们，或者像下面这样构造
                                send(SseEvent(data = eventData))
                            }
                        }
                        Log.d(TAG, "新的 SSE 客户端已连接")
                    }

                    // 根路径，用于简单的测试
                    get("/") {
                        call.respondText("Ktor Server is running! Access /mcp/host for MCP info.")
                    }
                }
            }.start(wait = false) // 以非阻塞方式启动服务器

            val actualHost = host // 启动时，host 是我们传入的 "0.0.0.0" 或特定 IP
            // Ktor 启动后，它可能会在多个接口上监听。我们可以尝试获取实际监听的地址
            // 但对于 Android 应用内的本地服务器，通常就是传入的 host 或 127.0.0.1
            Log.i(TAG, "Ktor 服务器已在 http://$actualHost:$port 启动。")
            onStarted(actualHost, port)

        } catch (e: Exception) {
            Log.e(TAG, "Ktor 服务器启动失败", e)
            onError(e)
            server = null
        }
    }

    /**
     * 停止 Ktor 服务器。
     * @param onStopped 服务器成功停止时的回调。
     */
    fun stopServer(onStopped: () -> Unit) {
        server?.apply {
            stop(1000, 5000) // 优雅地停止服务器，等待 1 秒，强制停止 5 秒
            Log.i(TAG, "Ktor 服务器已停止。")
        }
        McpService.shutdown() // 关闭 MCP 服务
        server = null
        onStopped()
    }

    /**
     * 检查服务器是否正在运行。
     */
    fun isRunning(): Boolean {
        return server != null
    }

    /**
     * 获取设备的内网 IP 地址。
     * 此方法需要 ACCESS_WIFI_STATE 和 ACCESS_NETWORK_STATE 权限。
     */
    fun getLocalIpAddress(): String {
        try {
            val networkInterfaces = java.net.NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val intf = networkInterfaces.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    // 检查是否是本地回环地址（127.0.0.1）或链接本地地址
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress) {
                        // 确保是 IPv4 地址
                        if (inetAddress is InetAddress && !inetAddress.isAnyLocalAddress && !inetAddress.isMulticastAddress) {
                            val hostAddress = inetAddress.hostAddress
                            // 过滤掉 IPv6 地址
                            if (hostAddress.indexOf(':') < 0) {
                                Log.d(TAG, "获取到内网 IP: $hostAddress")
                                return hostAddress
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            Log.e(TAG, "获取本地 IP 地址失败", ex)
        }
        return "127.0.0.1" // 默认返回回环地址
    }
}
