// android/zero/mcp/ShellHandler.kt
@file:Suppress("EXPERIMENTAL_API_USAGE")

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
import kotlinx.coroutines.CancellationException

class ShellHandler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    private val json = Json { encodeDefaults = true }
    private val sessionCache = mutableMapOf<String, TerminalSession>()

    private fun resp(event: String, result: String): McpResponse {
        return McpResponse(UUID.randomUUID().toString(), event, result)
    }

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

        // 获取TermuxService实例的正确方式
        val service = TermuxService.Companion.get()
        if (service == null) {
            out.send(resp("shell.error", "TermuxService not available")); return
        }

        /* ---- 1. 会话复用逻辑 ---- */
        var session = getSession(contextId)
        val closed  = AtomicBoolean(false)

        if (session == null) {
            /* ---- 2. 创建新会话 ---- */
            val sessionWrapper = service.createTerminalSession(
                command = arrayOf("/system/bin/sh"), // 修正参数名 commandLine -> command
                environment = null,
                initialWorkingDirectory = null,
                sessionName = "MCP-$contextId"
            ) ?: run {
                out.send(resp("shell.error", "createTerminalSession() failed")); return
            }
            session = sessionWrapper.terminalSession ?: run {
                out.send(resp("shell.error", "TerminalSession is null")); return
            }
            sessionCache[contextId] = session
        }

        /* ---- 3. 发送命令到会话 ---- */
        // 添加命令白名单验证
        fun sanitizeCommand(cmd: String): String {
            val allowedCommands = listOf("ls", "cd", "cat", "grep", "echo")
            val cmdParts = cmd.split(" ")
            return if (cmdParts.isNotEmpty() && allowedCommands.contains(cmdParts[0])) {
                cmd
            } else {
                "echo Error: Unauthorized command"
            }
        }

        // 验证命令安全性，防止命令注入
        val sanitizedCmd = sanitizeCommand(cmd)
        session.write("$sanitizedCmd\n")

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

    /* util */
    private fun resp(event: String, data: String) =
        McpResponse(UUID.randomUUID().toString(), event, data)
}
