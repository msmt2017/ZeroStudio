// File: android/zero/mcp/protocol/McpResponse.kt
package android.zero.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Represents a response sent from the MCP Server back to an AI client.
 * This structure defines the contract for how the server communicates results, logs, and errors.
 *
 * @property id The ID of the command that this response is for, allowing client-side correlation.
 * @property event The type of event this response represents (e.g., "file.create.success", "shell.exec.stdout", "error").
 * @property data The payload of the response, which can be any [JsonElement] (e.g., a String, a JsonObject, a JsonArray).
 * @property success Optional boolean indicating the success status of the operation.
 * `null` for intermediate events (like logs), `true` for success, `false` for failure.
 * @property contextId An optional ID to link responses to a specific logical "context" or session, mirroring the command's contextId.
 */
@Serializable
data class McpResponse(
    val id: String,
    val event: String,
    val data: JsonElement,
    val success: Boolean? = null,
    val contextId: String? = null
)

