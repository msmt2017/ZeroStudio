package app.mcp.http

import app.mcp.core.CommandParser
import app.mcp.core.CommandDispatcher
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

fun Application.mcpCommandRoute() {
    routing {
        post("/command") {
            val raw = call.receiveText()
            val command = CommandParser.parse(raw)
            val result = CommandDispatcher.dispatch(command)
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to (if (result.isSuccess) "success" else "error"),
                "data" to (result.getOrNull() ?: result.exceptionOrNull()?.message)
            ))
        }
    }
} 