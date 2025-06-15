// File: android/zero/mcp/LogManager.kt
package android.zero.mcp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * [LogManager] manages all logging for the MCP Server and Ktor.
 * It provides a centralized way to add log entries and exposes them via a [MutableStateFlow]
 * for observation by a Compose UI (e.g., [McpServerLogFragment]).
 *
 * Logs include server status, command processing, network communication, errors, and more.
 *
 * @author Android Zero
 */
object LogManager {

    // Internal mutable state flow to hold the list of log entries.
    // Using a ConcurrentLinkedQueue to allow thread-safe additions.
    private val _logEntries = MutableStateFlow(ConcurrentLinkedQueue<LogEntry>())

    /**
     * Exposed [StateFlow] for observing log entries.
     * UI components can collect from this flow to update their display.
     */
    val logEntries = _logEntries.asStateFlow()

    // Date formatter for consistent timestamping.
    private val dateFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Adds a new log entry to the log manager.
     *
     * @param message The main log message.
     * @param level The log level (e.g., "INFO", "WARN", "ERROR", "DEBUG").
     * @param tag An optional tag to categorize the log entry (e.g., "KtorServer", "FileHandler").
     */
    fun addLog(message: String, level: String = "INFO", tag: String? = null) {
        val timestamp = dateFormatter.format(Date())
        val formattedMessage = if (tag.isNullOrBlank()) {
            "[$timestamp] [$level] $message"
        } else {
            "[$timestamp] [$level] [$tag] $message"
        }
        _logEntries.update { currentQueue ->
            currentQueue.apply {
                add(LogEntry(formattedMessage, System.currentTimeMillis()))
                // Optional: Limit the number of log entries to prevent excessive memory usage
                // while(size > MAX_LOG_ENTRIES) { poll() }
            }
        }
        // For console/Logcat output during development
        println(formattedMessage)
    }

    /**
     * Clears all existing log entries.
     */
    fun clearLogs() {
        _logEntries.update { ConcurrentLinkedQueue() }
        println("LogManager: All logs cleared.")
    }
}

/**
 * Data class representing a single log entry.
 *
 * @property message The formatted log message string.
 * @property timestampMillis The timestamp when the log was created, in milliseconds.
 */
data class LogEntry(val message: String, val timestampMillis: Long)
