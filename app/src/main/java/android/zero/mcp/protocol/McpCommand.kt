// File: android/zero/mcp/protocol/McpCommand.kt
package android.zero.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents a command sent from an AI client to the MCP Server.
 * This structure defines the contract for how AI requests are formatted.
 *
 * @property id A unique identifier for this command, used for correlating responses.
 * @property instruction The main instruction or command string (e.g., "@File:create").
 * @property args Optional arguments for the command, represented as a [JsonObject]
 * to allow flexible key-value pairs.
 * @property contextId An optional ID to link commands to a specific logical "context" or session on the client side.
 */
@Serializable
data class McpCommand(
    val id: String,
    val instruction: String,
    val args: JsonObject = JsonObject(emptyMap()),
    val contextId: String? = null
)

