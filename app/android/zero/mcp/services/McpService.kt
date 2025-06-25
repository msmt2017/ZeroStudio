package android.zero.mcp

import android.content.Context
import android.zero.mcp.http.*
import android.zero.mcp.prompt.PromptTemplateManager
import android.zero.mcp.registry.McpToolRegistry
import android.zero.mcp.server.installLifecycleLogger
import io.ktor.server.engine.*
import io.ktor.server.netty.*

/**
 * MCP 主服务入口：统一初始化 Ktor + MCP 注册 + HTTP 路由 + 日志插件
 */
object McpService {

    private var engine: ApplicationEngine? = null

    fun start(context: Context, port: Int = 11583): ApplicationEngine {
        if (engine != null) return engine!!

        engine = embeddedServer(Netty, port = port) {
            installLifecycleLogger()

            // 安装所有 HTTP 路由
            mcpServerCore()
            mcpToolListRoute()
            mcpToolsMarkdownRoute()
            mcpHealthPingRoute()
            toolStatsRoute()
            toolReloadRoute()
            toolErrorLogRoute()
            toolTestRoute()
            sseWithMonitoring()
            sseSessionMonitorRoute()
            ktorEnvInspectorRoute()
        }.start(wait = false)

        // 初始化配置、注册所有工具
        McpInitManager.initialize(context, this)

        return engine!!
    }

    fun stop() {
        engine?.stop(2000, 5000)
        engine = null
    }

    fun getInstance(): ApplicationEngine? = engine
}
