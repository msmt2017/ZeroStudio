package android.zero.mcp.server

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.statuspages.*
import org.slf4j.event.Level

/**
 * Ktor 生命周期与日志插件：捕捉请求/异常/状态
 */
fun Application.installLifecycleLogger() {
    install(CallLogging) {
        level = Level.INFO
        format { call ->
            val uri = call.request.uri
            val method = call.request.httpMethod.value
            "📥 [$method] $uri"
        }
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("❌ 请求失败: ${call.request.uri}", cause)
            call.respondText("内部错误: ${cause.localizedMessage}")
        }
    }
}
