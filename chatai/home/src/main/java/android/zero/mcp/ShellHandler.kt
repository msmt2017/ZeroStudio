package android.zero.mcp

import com.termux.app.TermuxService
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import android.zero.mcp.McpResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ShellHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val json = Json { encodeDefaults = true }
    private val sessionCache = mutableMapOf<String, TerminalSession>()

    fun getSession(contextId: String): TerminalSession? = sessionCache[contextId]

    fun closeSession(contextId: String) {
        sessionCache.remove(contextId)?.finishIfRunning()
    }

    suspend fun handle(
        args: Map<String, String>,
        out: SendChannel<McpResponse>
    ) {
        val cmd = args["command"]?.trim().orEmpty()
        val contextId = args["contextId"]?.trim()
        if (cmd.isEmpty()) {
            out.send(resp("shell.error", "No command provided")); return
        }
        if (contextId.isNullOrEmpty()) {
            out.send(resp("shell.error", "Missing contextId for session management")); return
        }

        // Access TermuxService instance (assuming a singleton getter exists)
        val service = TermuxService.getInstance() // Adjust based on actual TermuxService API
        if (service == null) {
            out.send(resp("shell.error", "TermuxService not available")); return
        }

        var session = getSession(contextId)
        val closed = AtomicBoolean(false)

        if (session == null) {
            // Create a new TerminalSession with proper parameters
            session = TerminalSession(
                "/system/bin/sh", // executablePath
                null,             // workingDirectory
                arrayOf(),        // arguments
                arrayOf(),        // environment
                object : TerminalSessionClient {} // Minimal client implementation
            ).apply {
                sessionCache[contextId] = this
            }
        }

        session?.write("$cmd\n")

        session?.updateTerminalSessionClient(object : TermuxTerminalSessionClientBase() {

            private var lastOutput = ""
            private val watcher = scope.launch {
                while (isActive && !closed.get()) {
                    // Replace toString() with actual output retrieval method if available
                    val snap = session.toString() // Placeholder: use session.getOutput() or similar
                    if (snap != lastOutput) {
                        snap.split("\n")
                            .filter { it.isNotBlank() && it != lastOutput }
                            .forEach { out.trySend(resp("shell.log", it)) }
                        lastOutput = snap
                    }
                    delay(100)
                }
            }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                if (closed.getAndSet(true)) return
                watcher.cancel()
                sessionCache.remove(contextId)
                val code = finishedSession.exitStatus ?: -1
                val payload = json.encodeToString(mapOf("exitCode" to code))
                out.trySend(resp("shell.complete", payload))
            }

            override fun logError(tag: String?, message: String?) {
                if (closed.getAndSet(true)) return
                watcher.cancel()
                sessionCache.remove(contextId)
                out.trySend(resp("shell.error", message ?: "unknown"))
            }
        })
    }

    private fun resp(event: String, data: String) =
        McpResponse(UUID.randomUUID().toString(), event, data)
}

// Placeholder interface to satisfy TerminalSession constructor
interface TerminalSessionClient : TerminalSession.SessionClient