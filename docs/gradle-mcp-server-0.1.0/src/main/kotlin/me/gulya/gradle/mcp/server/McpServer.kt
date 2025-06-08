package me.gulya.gradle.mcp.server

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.modelcontextprotocol.kotlin.sdk.Implementation
import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import me.gulya.gradle.mcp.config.CliArgs
import me.gulya.gradle.mcp.config.ServerMode
import me.gulya.gradle.mcp.config.logger
import me.gulya.gradle.mcp.gradle.GradleService
import me.gulya.gradle.mcp.server.tool.ExecuteTaskTool
import me.gulya.gradle.mcp.server.tool.GetProjectInfoTool
import me.gulya.gradle.mcp.server.tool.RunTestsTool

class McpServer(private val config: CliArgs) {
    private val log = logger<McpServer>()
    private val gradleService = GradleService()

    fun start() {
        log.info("Starting MCP Server...")
        val server = configureMcpServer()

        when (config.mode) {
            ServerMode.STDIO -> runStdio(server)
            ServerMode.SSE -> runSse(server)
        }
    }

    private fun configureMcpServer(): Server {
        val server = Server(
            Implementation(
                name = "Gradle MCP Server",
                version = "0.1.0" // Consider moving to a const or config file
            ),
            ServerOptions(
                capabilities = ServerCapabilities(
                    prompts = ServerCapabilities.Prompts(listChanged = false),
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )

        log.info("Registering tools...")

        // Instantiate and register tools
        val tools = listOf(
            GetProjectInfoTool(),
            ExecuteTaskTool(),
            RunTestsTool()
        )

        tools.forEach { tool ->
            server.addTool(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema
            ) { request ->
                tool.execute(request, gradleService, config.debug)
            }
            log.debug("Registered tool: ${tool.name}")
        }

        log.info("MCP Server configured with ${tools.size} tools.")
        return server
    }

    private fun runStdio(server: Server) {
        log.info("Running in Standard I/O mode.")
        val transport = StdioServerTransport(
            inputStream = System.`in`.asSource().buffered(),
            outputStream = System.out.asSink().buffered()
        )

        runBlocking {
            try {
                server.connect(transport)
                val done = Job()
                server.onClose {
                    log.info("Server connection closed.")
                    done.complete()
                }
                log.info("MCP Server connected via Stdio. Waiting for close signal...")
                done.join() // Wait until the server signals closure
            } catch (e: Exception) {
                log.error("Error during Stdio server execution", e)
            } finally {
                 log.info("Stdio server loop finished.")
            }
        }
    }

    private fun runSse(server: Server) {
        log.info("Running in SSE mode on port ${config.port}.")
        println("Starting SSE server on port ${config.port}") // User-facing message
        println("Use inspector to connect to http://localhost:${config.port}/sse") // User-facing message

        try {
             embeddedServer(CIO, host = "0.0.0.0", port = config.port) {
                mcp { server } // Pass the configured server instance to the Ktor plugin
            }.start(wait = true)
        } catch (e: Exception) {
            log.error("Failed to start or run SSE server on port ${config.port}", e)
            println("Error: Could not start SSE server. Check logs for details.")
        }
    }
}
