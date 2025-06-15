// File: android/zero/mcp/protocol/JsonAdapters.kt
package android.zero.mcp.protocol

import kotlinx.serialization.json.Json

/**
 * Provides a pre-configured [Json] instance for serialization and deserialization
 * within the MCP protocol.
 */
object JsonAdapters {
    val defaultJson = Json {
        prettyPrint = true // Format JSON nicely for readability
        ignoreUnknownKeys = true // Allow for future expansion without breaking older clients/servers
        isLenient = true // Be lenient with JSON parsing (e.g., tolerate unquoted keys if necessary, though generally not recommended for strict JSON)
    }
}
