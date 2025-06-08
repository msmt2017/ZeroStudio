package me.gulya.gradle.mcp.server.tool

import io.modelcontextprotocol.kotlin.sdk.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.Tool
import kotlinx.serialization.json.Json
import me.gulya.gradle.mcp.gradle.GradleService

/**
 * Base interface or class for Gradle-based tools.
 */
interface GradleTool {
    val name: String
    val description: String
    val inputSchema: Tool.Input

    /**
     * Executes the tool's logic.
     * @param request The tool call request.
     * @param gradleService A service to interact with Gradle.
     * @param debug Flag indicating if debug logging is enabled.
     * @return The result of the tool execution.
     */
    suspend fun execute(
        request: CallToolRequest,
        gradleService: GradleService,
        debug: Boolean
    ): CallToolResult

    // Helper JSON instance
    val json: Json
        get() = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true; classDiscriminator = "#class" }

}
