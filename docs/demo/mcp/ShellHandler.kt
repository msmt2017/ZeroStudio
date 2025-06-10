// 文件路径：chatai/home/src/main/java/me/rerere/rikkahub/mcp/ShellHandler.kt

package android.zero.mcp

import com.termux.app.TermuxService
import com.termux.app.TermuxService.TermuxSessionCallback
import me.rerere.rikkahub.mcp.McpResponse
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ShellHandler：调用 ZeroStudio 中的 TermuxService 在 Termux 会话里执行命令，将结果通过 SSE 推送给前端。
 *
 * 假定 TermuxService 在 com.termux.app 包下，且其 executeCommand() 方法签名如下：
 *   void executeCommand(@NonNull String[] argv, @NonNull TermuxSessionCallback callback);
 *
 * TermuxSessionCallback 回调签名：
 *   void onOutput(@NonNull String line);
 *   void onExit(int exitCode);
 *   void onError(@NonNull String errorMsg);
 *
 * 如果你项目中 TermuxService 或 TermuxSessionCallback 的包路径或方法签名不同，请按需修改 import 和调用处。
 */
class ShellHandler {
    private val json = Json { encodeDefaults = true }

    /**
     * 处理 shell.execute 请求，启动一个 Termux 会话并执行命令。
     *
     * args 参数：
     *   - "command": 要执行的完整 Shell 命令字符串，例如 "ls /sdcard/ZeroStudioProject"
     *
     * SSE 推送流程：
     *   • 如果 command 为空，立即推送 "shell.error"，并结束通道。
     *   • 如果无法获取到 TermuxService 实例，推送 "shell.error"，并结束通道。
     *   • 否则：
     *     1. 将 command 按空格拆分成 argv（String 数组），
     *        并调用 termuxService.executeCommand(argv, callback)。
     *     2. 在 callback.onOutput() 回调中，每次都推送 event="shell.log"，data=当行输出；
     *     3. 在 callback.onExit() 回调中，推送 event="shell.complete"，data={"exitCode":<int>}，
     *        然后关闭通道；
     *     4. 在 callback.onError() 回调中，推送 event="shell.error"，data=错误消息，并关闭通道。
     */
    suspend fun handleExecuteCommand(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val command = args["command"] ?: ""
        if (command.isBlank()) {
            // 无命令时，立即返回错误
            sendChannel.send(McpResponse("shell.error", "No command provided"))
            sendChannel.close()
            return
        }

        // 从 TermuxService 工具栏中获取 Service 实例
        // 这里直接通过 Lookup 或者 Android Context 获取都行，示例用静态获取：
        val termuxService = TermuxService.sharedInstance
        if (termuxService == null) {
            sendChannel.send(McpResponse("shell.error", "TermuxService not available"))
            sendChannel.close()
            return
        }

        // 标记是否已经关闭通道，避免重复 close()
        val closed = AtomicBoolean(false)

        // 将完整命令拆分为 argv 数组
        // 注意：如果命令包含重定向字符等特殊符号，需要自行处理或封装为一个 /system/bin/sh -c <cmd> 的数组
        val argv: Array<String> = command.trim().split(" ").toTypedArray()

        try {
            termuxService.executeCommand(argv, object : TermuxSessionCallback {
                override fun onOutput(line: String) {
                    // 每行输出都通过 SSE 推送给前端
                    if (!closed.get()) {
                        sendChannel.trySend(McpResponse("shell.log", line)).getOrThrow()
                    }
                }

                override fun onExit(exitCode: Int) {
                    if (!closed.getAndSet(true)) {
                        // 执行完成，推送退出码
                        val resultJson = json.encodeToString(mapOf("exitCode" to exitCode))
                        sendChannel.trySend(McpResponse("shell.complete", resultJson)).getOrThrow()
                        sendChannel.close()
                    }
                }

                override fun onError(errorMsg: String) {
                    if (!closed.getAndSet(true)) {
                        // 出现错误时立刻推送错误并结束通道
                        sendChannel.trySend(McpResponse("shell.error", errorMsg)).getOrThrow()
                        sendChannel.close()
                    }
                }
            })
        } catch (e: Exception) {
            // 如果调用 executeCommand 本身抛出异常，推送一次错误，然后关闭通道
            val errMsg = e.localizedMessage ?: "Shell execution failed"
            sendChannel.send(McpResponse("shell.error", errMsg))
            if (!closed.getAndSet(true)) {
                sendChannel.close()
            }
        }
    }
}
