// android/zero/mcp/ShellHandler.kt
@file:Suppress("EXPERIMENTAL_API_USAGE")

package android.zero.mcp

import com.termux.app.TermuxService
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class ShellHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val json = Json { encodeDefaults = true }

    suspend fun handle(
        args: Map<String, String>,
        out: SendChannel<McpResponse>
    ) {
        val cmd = args["command"]?.trim().orEmpty()
        if (cmd.isEmpty()) {
            out.send(resp("shell.error", "No command provided")); return
        }

        val service = TermuxService.sharedInstance
        if (service == null) {
            out.send(resp("shell.error", "TermuxService not available")); return
        }

        /* ---- 1. 启 PTY ---- */
        val sessionWrapper = service.createTermuxSession(
            argv            = arrayOf("/system/bin/sh", "-c", cmd),
            env             = null,
            niceName        = null,
            cwd             = null,
            isFailSafe      = false,
            sessionName     = "MCP-$cmd"
        ) ?: run {
            out.send(resp("shell.error", "createTermuxSession() failed")); return
        }

        val session = sessionWrapper.terminalSession
        val closed  = AtomicBoolean(false)

        /* ---- 2. 绑定监听 ---- */
        session.updateTerminalSessionClient(object : TermuxTerminalSessionClientBase() {

            private var lastLen = 0
            private val watcher = scope.launch {
                while (isActive && !closed.get()) {
                    val snap = session.transcriptText ?: ""
                    if (snap.length > lastLen) {
                        snap.substring(lastLen)
                            .lineSequence()
                            .filter { it.isNotBlank() }
                            .forEach { out.trySend(resp("shell.log", it)) }
                        lastLen = snap.length
                    }
                    delay(100)
                }
            }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                if (closed.getAndSet(true)) return
                watcher.cancel()
                val code = finishedSession.exitStatus ?: -1
                val payload = json.encodeToString(mapOf("exitCode" to code))
                out.trySend(resp("shell.complete", payload))
                out.close()
            }

            override fun logError(tag: String?, message: String?) {
                if (closed.getAndSet(true)) return
                watcher.cancel()
                out.trySend(resp("shell.error", message ?: "unknown"))
                out.close()
            }
        })
    }

    /* util */
    private fun resp(event: String, data: String) =
        McpResponse(UUID.randomUUID().toString(), event, data)
}
