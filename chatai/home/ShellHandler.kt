// File: ShellHandler.kt
package android.zero.mcp

import android.content.Context
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.app.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ShellHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val context: Context
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

        var session = getSession(contextId)
        val closed = AtomicBoolean(false)

        if (session == null) {
            session = TerminalSession(
                "/system/bin/sh",
                null,
                arrayOf(),
                arrayOf(),
                // FIX: Replaced the problematic anonymous object with a valid TermuxTerminalSessionClientBase instance.
                object : TermuxTerminalSessionClientBase() {}
            ).apply {
                sessionCache[contextId] = this
            }
        }

        session.write("$cmd\n")

        session.updateTerminalSessionClient(object : TermuxTerminalSessionClientBase() {
            private var lastOutput = ""
            private val watcher = scope.launch {
                while (isActive && !closed.get()) {
                    val snap = session.toString()
                    if (snap != lastOutput) {
                        snap.split("\n")
                            .filter { it.isNotBlank() && it != lastOutput }
                            .forEach { out.trySend(resp("shell.log", it)) }
                        lastOutput = snap
                    }
                    delay(100)
                }
            }

            override fun onSessionFinished(session: TerminalSession, exitStatus: Int) {
                if (closed.getAndSet(true)) return
                watcher.cancel()
                sessionCache.remove(contextId)
                val payload = json.encodeToString(mapOf("exitCode" to exitStatus))
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