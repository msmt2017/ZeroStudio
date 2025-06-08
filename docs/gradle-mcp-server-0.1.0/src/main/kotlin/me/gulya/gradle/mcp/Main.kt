package me.gulya.gradle.mcp

import me.gulya.gradle.mcp.config.AppConfig
import me.gulya.gradle.mcp.server.McpServer

fun main(args: Array<String>) {
    val config = AppConfig.parse(args)

    val server = McpServer(config)
    server.start()

    println("Gradle MCP Server finished.")
}
