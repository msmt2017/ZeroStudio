package me.gulya.gradle.mcp.config

import org.slf4j.Logger
import org.slf4j.LoggerFactory

enum class ServerMode {
    STDIO, SSE
}

data class CliArgs(
    val mode: ServerMode = ServerMode.STDIO,
    val port: Int = DEFAULT_PORT,
    val debug: Boolean = false
)

const val DEFAULT_PORT = 3001
const val DEFAULT_TEST_LOG_LINES = 100

// Simple logger delegate
inline fun <reified T> logger(): Logger = LoggerFactory.getLogger(T::class.java)

object AppConfig {
    fun parse(args: Array<String>): CliArgs {
        val log = LoggerFactory.getLogger(AppConfig::class.java)
        val debug = args.contains("--debug")
        val filteredArgs = args.filter { it != "--debug" }.toList()

        var mode = ServerMode.STDIO
        var port = DEFAULT_PORT

        when (filteredArgs.firstOrNull()) {
            null, "--stdio" -> {
                mode = ServerMode.STDIO
                log.info("Configured for Standard I/O mode.")
            }
            "--sse" -> {
                mode = ServerMode.SSE
                port = filteredArgs.getOrNull(1)?.toIntOrNull() ?: DEFAULT_PORT
                log.info("Configured for SSE mode on port $port.")
            }
            else -> {
                System.err.println("Unknown command: ${filteredArgs.first()}. Defaulting to Standard I/O.")
                log.warn("Unknown command: ${filteredArgs.first()}. Defaulting to Standard I/O.")
                mode = ServerMode.STDIO
            }
        }

        if (debug) {
            log.info("Debug mode enabled.")
        }

        return CliArgs(mode, port, debug)
    }
}
