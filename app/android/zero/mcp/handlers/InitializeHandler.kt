package android.zero.mcp.handlers

import android.zero.mcp.server.*
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * 处理 'initialize' 请求，用于建立会话和能力协商。
 */
object InitializeHandler : McpHandler {
    override suspend fun handle(request: McpRequest): Any {
        Logger.log("Handling initialize request.")
        // 在实际应用中，您会在这里检查 request.params.protocolVersion
        // 并根据客户端能力调整服务器的响应。

        val serverCapabilities = buildJsonObject {
            putJsonObject("prompts") { put("listChanged", true) }
            putJsonObject("resources") {
                put("subscribe", true)
                put("listChanged", true)
            }
            putJsonObject("tools") { put("listChanged", true) }
            put("logging", buildJsonObject {})
            put("experimental", buildJsonObject {})
        }

        val serverInfo = buildJsonObject {
            put("name", "android.zero.mcpServer")
            put("version", "1.0.0")
        }

        val result = buildJsonObject {
            put("protocolVersion", "2024-11-05") // 回应我们支持的协议版本
            put("capabilities", serverCapabilities)
            put("serverInfo", serverInfo)
        }

        // 协商完成后，发送一个 initialized 通知
        KtorMcpServer.sseChannel.send(McpNotification("initialized"))

        return McpResponse(request.id, result)
    }
}
