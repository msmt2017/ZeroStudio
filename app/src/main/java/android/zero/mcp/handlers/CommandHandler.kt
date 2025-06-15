// File: android/zero/mcp/handlers/CommandHandler.kt
package android.zero.mcp.handlers

import android.zero.mcp.protocol.McpCommand
import kotlinx.coroutines.channels.SendChannel

/**
 * Defines the contract for all MCP command handlers.
 * Each handler is responsible for processing a specific type of [McpCommand]
 * and sending [McpResponse] objects back through the provided [SendChannel].
 */
interface CommandHandler {

    /**
     * Handles the given MCP command.
     *
     * @param command The [McpCommand] to process.
     * @param responseChannel The [SendChannel] to send [McpResponse] objects back to the client.
     */
    suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>)
}
