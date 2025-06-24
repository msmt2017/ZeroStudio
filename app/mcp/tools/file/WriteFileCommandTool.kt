package app.mcp.tools.file

import app.mcp.core.*
import java.io.File

class WriteFileCommandTool(private val workspaceRoot: File) : McpCommandTool {
    override val meta = CommandMeta(
        name = "File:WriteFile",
        description = "写入内容到指定文件",
        params = listOf(
            CommandParam("path", "文件路径", true),
            CommandParam("content", "写入内容", true),
            CommandParam("writeLine", "写入行号", false)
        )
    )

    override fun execute(params: Map<String, String>): Result<Any> {
        val path = params["path"] ?: return Result.failure(Exception("Missing path"))
        val content = params["content"] ?: return Result.failure(Exception("Missing content"))
        val writeLine = params["writeLine"]?.toIntOrNull()
        return try {
            val file = if (path.startsWith("/")) File(path) else File(workspaceRoot, path)
            file.parentFile?.mkdirs()
            if (!file.exists()) file.createNewFile()
            if (writeLine != null) {
                val lines = file.readLines().toMutableList()
                while (lines.size < writeLine) lines.add("")
                lines.add(writeLine - 1, content)
                file.writeText(lines.joinToString("\n"))
            } else {
                file.appendText(content)
            }
            Result.success("写入成功: ${file.absolutePath}")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 