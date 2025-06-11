// 文件路径：chatai/home/src/main/java/me/rerere/rikkahub/mcp/GradleHandler.kt

package android.zero.mcp

import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * GradleHandler：负责处理所有与 Gradle 任务相关的 MCP 请求，
 * 包括列出任务、执行任务和执行任意 Gradle 命令。
 */
class GradleHandler2 {
    private val json = Json { encodeDefaults = true }

    /**
     * 列出当前项目所有 Gradle 任务（包括子模块）。
     *
     * 参数 args:
     *   - "projectRoot": 项目根目录（绝对路径）
     *
     * 响应事件:
     *   - "task.list.result"，data 为 Json 字符串列表，每项是一个任务名称（包含描述文本）。
     */
    suspend fun handleListTasks(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val projectRoot = File(args["projectRoot"] ?: ".")
        // 根据操作系统选择 Gradle Wrapper
        val gradlew = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        try {
            // 通过 "./gradlew tasks --all" 获取所有任务列表
            val process = ProcessBuilder(gradlew, "tasks", "--all")
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            val tasks = mutableListOf<String>()
            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    if (line.trim().isNotEmpty()) {
                        tasks.add(line.trim())
                    }
                }
            }
            process.waitFor()

            val data = json.encodeToString(tasks)
            sendChannel.send(McpResponse("task.list.result", data))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to list tasks"
            sendChannel.send(McpResponse("task.list.error", errMsg))
        }
    }

    /**
     * 执行指定的 Gradle 任务（例如 assembleDebug、clean 等）。
     *
     * 参数 args:
     *   - "projectRoot": 项目根目录（绝对路径）
     *   - "task": 要执行的任务名（String）
     *
     * SSE 推送过程:
     *   - 每行日志推送为 event="task.execute.log", data="<日志内容>"
     *   - 完成后推送 event="task.execute.complete", data="{\"exitCode\":<退出码>}"
     */
    suspend fun handleExecuteTask(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val projectRoot = File(args["projectRoot"] ?: ".")
        val taskName = args["task"] ?: return
        val gradlew = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }
        try {
            // 启动进程执行命令
            val process = ProcessBuilder(gradlew, taskName)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    // 每接收到一行日志，就实时通过 SSE 推送
                    sendChannel.send(McpResponse("task.execute.log", line))
                }
            }
            process.waitFor()

            // 推送完成事件，包括退出码
            sendChannel.send(McpResponse("task.execute.complete", "{\"exitCode\":${process.exitValue()}}"))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to execute task $taskName"
            sendChannel.send(McpResponse("task.execute.error", errMsg))
        }
    }

    /**
     * 直接执行任意 Gradle 命令（例如 build、assembleDebug 等，可以带参数）。
     *
     * 参数 args:
     *   - "projectRoot": 项目根目录（绝对路径）
     *   - "command": 要传递给 gradlew 的命令及其参数（以空格分隔）
     *
     * SSE 推送过程:
     *   - 每行日志推送为 event="gradle.execute.log", data="<日志内容>"
     *   - 完成后推送 event="gradle.execute.complete", data="{\"exitCode\":<退出码>}"
     */
    suspend fun handleExecuteCommand(args: Map<String, String>, sendChannel: SendChannel<McpResponse>) {
        val projectRoot = File(args["projectRoot"] ?: ".")
        val command = args["command"] ?: return
        val gradlew = if (System.getProperty("os.name").lowercase().contains("windows")) {
            "gradlew.bat"
        } else {
            "./gradlew"
        }

        // 将 command 拆成参数列表，例如 "build --stacktrace"
        val parts = command.split(" ").filter { it.isNotBlank() }
        val cmdList = mutableListOf<String>()
        cmdList.add(gradlew)
        cmdList.addAll(parts)

        try {
            val process = ProcessBuilder(cmdList)
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.lineSequence().forEach { line ->
                    sendChannel.send(McpResponse("gradle.execute.log", line))
                }
            }
            process.waitFor()

            sendChannel.send(McpResponse("gradle.execute.complete", "{\"exitCode\":${process.exitValue()}}"))
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Failed to execute Gradle command"
            sendChannel.send(McpResponse("gradle.execute.error", errMsg))
        }
    }
}
