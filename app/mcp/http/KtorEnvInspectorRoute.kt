package android.zero.mcp.http

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * 输出当前 Ktor 环境状态 / 插件情况
 */
fun Application.ktorEnvInspectorRoute() {
    routing {
        get("/debug/env") {
            val env = environment
            val plugins = pluginRegistry.all().map { it.key.simpleName }
            call.respond(
                mapOf(
                    "environment" to mapOf(
                        "devMode" to env.developmentMode,
                        "log" to env.log.name,
                        "rootPath" to env.rootPath
                    ),
                    "plugins" to plugins
                )
            )
        }
    }
}
