package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.server.*
import android.zero.mcp.utils.Logger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
 import com.termux.terminal.TerminalSession
 import com.termux.shared.termux.TermuxConstants
 import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase

/**
 * 管理 Termux 终端会话以执行 shell 命令。
 * 这是一个占位符实现，需要与真实的 Termux API 集成。
 */
object ShellHandler : McpHandler {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sessionCache = ConcurrentHashMap<String, TerminalSession>()
    
    // 占位符上下文
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    override suspend fun handle(request: McpRequest): Any {
        if (!this::appContext.isInitialized) {
            return McpErrorResponse(request.id, McpError.internalError("ShellHandler not initialized."))
        }

        val operation = request.method.removePrefix("shell:")
        val params = request.params
        if (params == null) {
            return McpErrorResponse(request.id, McpError.invalidParams("Parameters are required for shell operations."))
        }

        return when (operation) {
            "execute" -> execute(request.id, params)
            else -> McpErrorResponse(request.id, McpError.methodNotFound("Unknown shell operation: $operation"))
        }
    }

    private suspend fun execute(id: String, params: JsonElement): Any {
        val command = getStringParam(params, "execute")
        val contextId = getStringParam(params, "contextId") ?: UUID.randomUUID().toString()

        if (command.isNullOrBlank()) {
            return McpErrorResponse(id, McpError.invalidParams("Missing 'execute' parameter."))
        }

        Logger.log("Shell execute: context='$contextId', command='$command'")

        // 占位符: 在这里与 Termux TerminalSession 交互
        val session = sessionCache.getOrPut(contextId) { createTermuxSession(contextId) }
        session.write(command + "\n")

        // 模拟异步输出
        scope.launch {
            delay(500)
            val output = "Output for '$command' in context '$contextId'"
            KtorMcpServer.sseChannel.trySend(McpNotification(
                method = "shell.log",
                params = buildJsonObject {
                    put("contextId", contextId)
                    put("data", output)
                }
            ))
            delay(1000)
            KtorMcpServer.sseChannel.trySend(McpNotification(
                method = "shell.complete",
                params = buildJsonObject {
                    put("contextId", contextId)
                    put("exitCode", 0)
                }
            ))
        }

        return McpResponse(id, buildJsonObject {
            put("status", "Command execution started")
            put("contextId", contextId)
        })
    }

    
    // 创建一个真实的 Termux 会话的示例函数
    private fun createTermuxSession(contextId: String): TerminalSession {
        val executablePath = TermuxConstants.TERMUX_PREFIX_DIR_PATH + "/bin/login"
        val cwd = TermuxConstants.TERMUX_HOME_DIR_PATH
        val args = arrayOf<String>()
        val env = arrayOf(
            "TERM=xterm-256color",
            "HOME=${TermuxConstants.TERMUX_HOME_DIR_PATH}",
            "PREFIX=${TermuxConstants.TERMUX_PREFIX_DIR_PATH}"
        )

        val client = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) {
                scope.launch {
                    val transcript = changedSession.emulator.screen.transcriptText
                    KtorMcpServer.sseChannel.trySend(McpNotification(
                        method = "shell.log",
                        params = buildJsonObject {
                            put("contextId", contextId)
                            put("data", transcript)
                        }
                    ))
                }
            }

            override fun onSessionFinished(finishedSession: TerminalSession) {
                 scope.launch {
                     KtorMcpServer.sseChannel.trySend(McpNotification(
                        method = "shell.complete",
                        params = buildJsonObject {
                            put("contextId", contextId)
                            put("exitCode", finishedSession.exitStatus)
                        }
                    ))
                    sessionCache.remove(contextId)
                }
            }
            
            override fun logError(tag: String?, message: String?) {
                scope.launch {
                    KtorMcpServer.sseChannel.trySend(McpNotification(
                        method = "shell.error",
                        params = buildJsonObject {
                            put("contextId", contextId)
                            put("message", message ?: "Unknown error")
                        }
                    ))
                }
            }
        }
        
        return TerminalSession(executablePath, cwd, args, env, 80, 24, client)
    }
    
}
