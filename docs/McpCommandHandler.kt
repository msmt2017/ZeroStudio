package com.itsaky.androidide.mcp.handler

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import com.blankj.utilcode.util.AppUtils
import com.blankj.utilcode.util.FileIOUtils
import com.blankj.utilcode.util.FileUtils
import com.itsaky.androidide.activities.MainActivity
import com.itsaky.androidide.app.IDEApplication
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.mcp.models.*
import com.itsaky.androidide.models.LogLine
import com.itsaky.androidide.projects.IProjectManager
import com.itsaky.androidide.projects.api.AndroidModule
import com.itsaky.androidide.services.IBuildService
import com.itsaky.androidide.shell.executeProcessAsync
import com.itsaky.androidide.tooling.impl.util.StopWatch
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.ILogger.Level
import com.itsaky.androidide.utils.RecursiveFileSearcher
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

/**
 * MCP 命令处理器。负责将 MCP 命令映射到 AndroidIDE 的具体 API 调用。
 *
 * @param context Android 应用上下文。
 * @param logSender 用于将处理过程中的日志发送回 MCP Service 的回调函数。
 * @param currentEditorProvider 用于动态获取当前活跃的 IDEEditor 实例的函数。
 * @author Akash Yadav (原始作者)
 * @author Gemini (在此基础上扩展和完善)
 */
class McpCommandHandler(
    private val context: Context,
    private val logSender: (level: Level, tag: String, message: String) -> Unit,
    private val currentEditorProvider: () -> IDEEditor?
) {
    private val log = ILogger.newInstance("MCP_CmdHandler")
    private val uiHandler = Handler(Looper.getMainLooper())

    /**
     * 处理接收到的 MCP 命令。
     *
     * @param command 待处理的 MCP 命令。
     * @return 命令处理结果的响应对象。
     */
    suspend fun handleCommand(command: McpCommand): Any {
        log.debug("开始处理命令: ${command.commandType}:${command.subCommand}, 参数: ${command.parameters}")

        return when (command.commandType) {
            "File" -> handleFileCommand(command)
            "gradle" -> handleGradleCommand(command)
            "task" -> handleTaskCommand(command)
            "TabFile" -> handleTabFileCommand(command)
            "shell" -> handleShellCommand(command)
            else -> {
                val errorMessage = "未知命令类型: ${command.commandType}"
                log.error(errorMessage)
                McpFileResponse(false, errorMessage) // 返回通用失败响应
            }
        }
    }

    /**
     * 处理所有 @File 相关的命令。
     */
    private suspend fun handleFileCommand(command: McpCommand): McpFileResponse {
        return withContext(Dispatchers.IO) { // 文件操作通常在 IO 线程进行
            try {
                when (command.subCommand) {
                    "create" -> {
                        val path = command.parameters["path"] ?: return@withContext McpFileResponse(false, "缺少 'path' 参数。")
                        val folderName = command.parameters["folder"]
                        val fileName = command.parameters["files"]
                        handleFileCreate(File(path), folderName, fileName)
                    }
                    "WriteFile" -> {
                        val path = command.parameters["path"] ?: return@withContext McpFileResponse(false, "缺少 'path' 参数。")
                        val content = command.parameters["content"] ?: return@withContext McpFileResponse(false, "缺少 'content' 参数。")
                        val writeLine = command.parameters["writeLine"]?.toIntOrNull()
                        handleFileWrite(File(path), content, writeLine)
                    }
                    "info" -> {
                        val path = command.parameters["path"] ?: return@withContext McpFileResponse(false, "缺少 'path' 参数。")
                        handleFileInfo(File(path))
                    }
                    "Rename" -> {
                        val destPath = command.parameters["DestinationPath"] ?: return@withContext McpFileResponse(false, "缺少 'DestinationPath' 参数。")
                        val renameContent = command.parameters["RenameContent"] ?: return@withContext McpFileResponse(false, "缺少 'RenameContent' 参数。")
                        handleFileRename(File(destPath), renameContent)
                    }
                    "copy" -> {
                        val copyPath = command.parameters["copyPath"] ?: return@withContext McpFileResponse(false, "缺少 'copyPath' 参数。")
                        val destPath = command.parameters["DestinationPath"] ?: return@withContext McpFileResponse(false, "缺少 'DestinationPath' 参数。")
                        handleFileCopy(File(copyPath), File(destPath))
                    }
                    "move" -> {
                        val movePath = command.parameters["movePath"] ?: return@withContext McpFileResponse(false, "缺少 'movePath' 参数。")
                        val destPath = command.parameters["DestinationPath"] ?: return@withContext McpFileResponse(false, "缺少 'DestinationPath' 参数。")
                        handleFileMove(File(movePath), File(destPath))
                    }
                    "search" -> {
                        val path = command.parameters["path"] ?: return@withContext McpFileResponse(false, "缺少 'path' 参数。")
                        val content = command.parameters["content"] ?: return@withContext McpFileResponse(false, "缺少 'content' 参数。")
                        handleFileSearch(File(path), content)
                    }
                    "Upload" -> {
                        val filesPath = command.parameters["Files"]
                        val getFolder = command.parameters.containsKey("getFolder") // 检查是否存在 getFolder
                        handleFileUpload(filesPath?.let { File(it) }, getFolder)
                    }
                    "workspace" -> handleFileWorkspaceCommand(command)
                    else -> McpFileResponse(false, "未知的文件子命令: ${command.subCommand}")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理文件命令 ${command.subCommand} 时发生错误: ${e.message}\n$exceptionAsString", e)
                McpFileResponse(false, "处理文件命令失败: ${e.message}")
            }
        }
    }

    /**
     * 处理 @File:workspace 相关的子命令。
     */
    private suspend fun handleFileWorkspaceCommand(command: McpCommand): McpFileResponse {
        return withContext(Dispatchers.IO) {
            try {
                when (command.parameters["subCommandKey"]) { // 使用一个内部键来区分 workspace 子命令
                    "getinstallApk" -> {
                        val variant = command.parameters["getinstallApk"] ?: "debug" // 默认为 debug
                        handleWorkspaceInstallApk(variant)
                    }
                    "getmoduleInfo" -> handleWorkspaceGetModuleInfo()
                    "getGradleWrapperInfo" -> handleWorkspaceGetGradleWrapperInfo()
                    "ModifyGradleVersion" -> {
                        val versionAndType = command.parameters["ModifyGradleVersion"] ?: return@withContext McpFileResponse(false, "缺少版本信息。")
                        val parts = versionAndType.split(",")
                        if (parts.size != 2) return@withContext McpFileResponse(false, "版本和类型格式不正确，应为 '版本,类型'。")
                        handleWorkspaceModifyGradleVersion(parts[0], parts[1])
                    }
                    "GetModuleSrcFileList" -> {
                        val moduleName = command.parameters["GetModuleSrcFileList"] ?: return@withContext McpFileResponse(false, "缺少模块名称。")
                        handleWorkspaceGetModuleSrcFileList(moduleName)
                    }
                    else -> McpFileResponse(false, "未知的工作区子命令。")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理工作区命令时发生错误: ${e.message}\n$exceptionAsString", e)
                McpFileResponse(false, "处理工作区命令失败: ${e.message}")
            }
        }
    }

    /**
     * 处理 @gradle 相关的命令。
     */
    private suspend fun handleGradleCommand(command: McpCommand): McpGradleTaskResponse {
        return withContext(Dispatchers.Main) { // 构建操作可能触发 UI 更新
            try {
                when (command.subCommand) {
                    "run" -> handleGradleRun()
                    else -> McpGradleTaskResponse(false, "未知的 Gradle 子命令: ${command.subCommand}")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理 Gradle 命令时发生错误: ${e.message}\n$exceptionAsString", e)
                McpGradleTaskResponse(false, "处理 Gradle 命令失败: ${e.message}")
            }
        }
    }

    /**
     * 处理 @task 相关的命令。
     */
    private suspend fun handleTaskCommand(command: McpCommand): McpGradleTaskResponse {
        return withContext(Dispatchers.IO) {
            try {
                when (command.subCommand) {
                    "runTask" -> {
                        val taskName = command.parameters["runTask"] ?: return@withContext McpGradleTaskResponse(false, "缺少 'runTask' 参数。")
                        handleTaskRun(taskName)
                    }
                    "taskList" -> handleTaskList()
                    "searchTask" -> {
                        val query = command.parameters["searchTask"] ?: return@withContext McpGradleTaskResponse(false, "缺少 'searchTask' 参数。")
                        handleTaskSearch(query)
                    }
                    else -> McpGradleTaskResponse(false, "未知的 Task 子命令: ${command.subCommand}")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理 Task 命令时发生错误: ${e.message}\n$exceptionAsString", e)
                McpGradleTaskResponse(false, "处理 Task 命令失败: ${e.message}")
            }
        }
    }

    /**
     * 处理 @TabFile 相关的命令。
     */
    private suspend fun handleTabFileCommand(command: McpCommand): McpTabFileResponse {
        return withContext(Dispatchers.Main) { // UI 相关操作在主线程进行
            val editor = currentEditorProvider()
            if (editor == null) {
                val msg = "IDE 编辑器未打开或无法获取实例。"
                log.warn(msg)
                return@withContext McpTabFileResponse(false, msg)
            }
            try {
                when (command.subCommand) {
                    "getFunction" -> {
                        val functionName = command.parameters["getFunction"] ?: return@withContext McpTabFileResponse(false, "缺少函数名称。")
                        handleTabFileGetFunction(editor, functionName)
                    }
                    "getCursor" -> handleTabFileGetCursor(editor)
                    "getLine" -> {
                        val lineRange = command.parameters["getLine"] ?: return@withContext McpTabFileResponse(false, "缺少行范围。")
                        handleTabFileGetLine(editor, lineRange)
                    }
                    "getFile" -> handleTabFileGetFile(editor)
                    "searchTabFile" -> {
                        val content = command.parameters["searchTabFile"] ?: return@withContext McpTabFileResponse(false, "缺少搜索内容。")
                        handleTabFileSearch(editor, content)
                    }
                    else -> McpTabFileResponse(false, "未知的 TabFile 子命令: ${command.subCommand}")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理 TabFile 命令时发生错误: ${e.message}\n$exceptionAsString", e)
                McpTabFileResponse(false, "处理 TabFile 命令失败: ${e.message}")
            }
        }
    }

    /**
     * 处理 @shell 相关的命令。
     */
    private suspend fun handleShellCommand(command: McpCommand): McpShellResponse {
        return withContext(Dispatchers.IO) { // Shell 命令在 IO 线程执行
            try {
                when (command.subCommand) {
                    "execute" -> {
                        val cmd = command.parameters["execute"] ?: return@withContext McpShellResponse(false, "缺少 'execute' 命令。")
                        handleShellExecute(cmd)
                    }
                    else -> McpShellResponse(false, "未知的 Shell 子命令: ${command.subCommand}")
                }
            } catch (e: Exception) {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val exceptionAsString = sw.toString()
                log.error("处理 Shell 命令时发生错误: ${e.message}\n$exceptionAsString", e)
                McpShellResponse(false, msg = "处理 Shell 命令失败: ${e.message}", output = e.message)
            }
        }
    }

    /**
     * 将 UI 操作封装在 runOnUiThread 中。
     * @param action 要在 UI 线程执行的操作。
     */
    private suspend fun runOnUiThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            withContext(Dispatchers.Main) {
                action()
            }
        }
    }

    // =========================================================
    // 以下是具体命令的实现，根据提供的 AndroidIDE 源码片段进行填充
    // =========================================================

    // region: @File 命令的具体实现
    private fun handleFileCreate(parentDir: File, folderName: String?, fileName: String?): McpFileResponse {
        val targetFile: File
        val operation: String
        if (!parentDir.exists() || !parentDir.isDirectory) {
            val msg = "父目录不存在或不是目录: ${parentDir.absolutePath}"
            log.error(msg)
            return McpFileResponse(false, msg)
        }
        if (folderName != null) {
            targetFile = File(parentDir, folderName)
            operation = "创建文件夹"
            if (targetFile.exists()) {
                val msg = "文件夹已存在: ${targetFile.absolutePath}"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }
            if (!targetFile.mkdirs()) {
                val msg = "创建文件夹失败: ${targetFile.absolutePath}"
                log.error(msg)
                return McpFileResponse(false, msg)
            }
        } else if (fileName != null) {
            targetFile = File(parentDir, fileName)
            operation = "创建文件"
            if (targetFile.exists()) {
                val msg = "文件已存在: ${targetFile.absolutePath}"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }
            // 使用 FileIOUtils 创建空文件
            if (!FileIOUtils.writeFileFromString(targetFile, "")) {
                val msg = "创建文件失败: ${targetFile.absolutePath}"
                log.error(msg)
                return McpFileResponse(false, msg)
            }
            // 通知文件创建事件 (需要 EventBus 和 FileCreationEvent，这里暂时不集成复杂的事件系统)
            // EventBus.getDefault().post(FileCreationEvent(targetFile, context))
        } else {
            val msg = "必须指定 'folder' 或 'files' 参数。"
            log.error(msg)
            return McpFileResponse(false, msg)
        }

        val msg = "$operation 成功: ${targetFile.absolutePath}"
        log.info(msg)
        return McpFileResponse(true, msg, targetFile.absolutePath)
    }

    private fun handleFileWrite(file: File, content: String, writeLine: Int?): McpFileResponse {
        if (!file.exists() || !file.isFile) {
            val msg = "文件不存在或不是文件: ${file.absolutePath}"
            log.error(msg)
            return McpFileResponse(false, msg)
        }

        return try {
            if (writeLine == null) {
                // 覆盖写入
                FileIOUtils.writeFileFromString(file, content)
                val msg = "文件内容已覆盖写入: ${file.absolutePath}"
                log.info(msg)
                McpFileResponse(true, msg)
            } else {
                // 按行写入
                val lines = file.readLines().toMutableList()
                // 确保行号有效，允许在文件末尾追加 (+1)
                if (writeLine < 1 || writeLine > lines.size + 1) {
                    val msg = "指定行号 ${writeLine} 超出文件范围 (1-${lines.size + 1})"
                    log.error(msg)
                    return McpFileResponse(false, msg)
                }

                if (writeLine <= lines.size) {
                    // 修改现有行
                    lines[writeLine - 1] = content
                } else {
                    // 在文件末尾追加（如果指定行号是文件行数+1）
                    lines.add(content)
                }

                FileIOUtils.writeFileFromString(file, lines.joinToString("\n"))
                val msg = "内容已写入文件 ${file.absolutePath} 的第 ${writeLine} 行。"
                log.info(msg)
                McpFileResponse(true, msg)
            }
        } catch (e: Exception) {
            val msg = "写入文件 ${file.absolutePath} 失败: ${e.message}"
            log.error(msg, e)
            McpFileResponse(false, msg)
        }
    }

    private fun handleFileInfo(file: File): McpFileResponse {
        if (!file.exists()) {
            val msg = "文件或文件夹不存在: ${file.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }

        val infoBuilder = StringBuilder()
        infoBuilder.append("--- 文件/文件夹信息 ---\n")
        infoBuilder.append("名称: ${file.name}\n")
        infoBuilder.append("绝对路径: ${file.absolutePath}\n")
        infoBuilder.append("存在: ${file.exists()}\n")
        infoBuilder.append("是文件: ${file.isFile}\n")
        infoBuilder.append("是目录: ${file.isDirectory}\n")
        infoBuilder.append("可读: ${file.canRead()}\n")
        infoBuilder.append("可写: ${file.canWrite()}\n")
        infoBuilder.append("最后修改时间: ${java.util.Date(file.lastModified())}\n")

        if (file.isFile) {
            infoBuilder.append("大小 (字节): ${file.length()}\n")
            // 简单字数统计
            val content = FileIOUtils.readFile2String(file)
            val wordCount = content?.split("\\s+".toRegex())?.filter { it.isNotBlank() }?.size ?: 0
            infoBuilder.append("字数 (估算): $wordCount\n")
            infoBuilder.append("MD5: ${FileUtils.getFileMD5ToString(file)}\n")
            infoBuilder.append("SHA1: ${FileUtils.getFileSha1ToString(file)}\n")
            infoBuilder.append("CRC32: ${FileUtils.getFileCrc32ToString(file)}\n")
        } else if (file.isDirectory) {
            val files = FileUtils.listFilesInDir(file)
            val fileCount = files?.count { it.isFile } ?: 0
            val dirCount = files?.count { it.isDirectory } ?: 0
            infoBuilder.append("文件数量: $fileCount\n")
            infoBuilder.append("子目录数量: $dirCount\n")
        }

        val msg = "成功获取文件/文件夹信息。"
        log.info(msg)
        return McpFileResponse(true, msg, infoBuilder.toString())
    }

    private fun handleFileRename(oldFile: File, newName: String): McpFileResponse {
        if (!oldFile.exists()) {
            val msg = "目标文件或文件夹不存在: ${oldFile.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }
        // 简易名称校验：不允许空白，长度限制，不允许路径分隔符
        if (newName.isBlank() || newName.length > 255 || newName.contains("/") || newName.contains("\\")) {
            val msg = "无效的新名称: $newName"
            log.error(msg)
            return McpFileResponse(false, msg)
        }

        val renamed = FileUtils.rename(oldFile, newName)
        return if (renamed) {
            // 触发文件重命名事件，如果需要的话 (需要集成 EventBus 和 FileRenameEvent)
            // EventBus.getDefault().post(FileRenameEvent(oldFile, File(oldFile.parentFile, newName)))
            val msg = "成功重命名 '${oldFile.name}' 为 '$newName'"
            log.info(msg)
            McpFileResponse(true, msg)
        } else {
            val msg = "重命名 '${oldFile.name}' 失败。可能是新名称已存在或权限不足。"
            log.error(msg)
            McpFileResponse(false, msg)
        }
    }

    private fun handleFileCopy(source: File, destination: File): McpFileResponse {
        if (!source.exists()) {
            val msg = "源文件或文件夹不存在: ${source.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }

        val success: Boolean = try {
            if (source.isFile) {
                FileUtils.copyFile(source, destination)
            } else {
                FileUtils.copyDir(source, destination)
            }
        } catch (e: Exception) {
            log.error("复制文件/文件夹时发生错误", e)
            false
        }

        return if (success) {
            val msg = "成功复制 '${source.name}' 到 '${destination.absolutePath}'"
            log.info(msg)
            McpFileResponse(true, msg)
        } else {
            val msg = "复制 '${source.name}' 到 '${destination.absolutePath}' 失败。可能权限不足或目标路径无效。"
            log.error(msg)
            McpFileResponse(false, msg)
        }
    }

    private fun handleFileMove(source: File, destination: File): McpFileResponse {
        if (!source.exists()) {
            val msg = "源文件或文件夹不存在: ${source.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }

        val success: Boolean = try {
            if (source.isFile) {
                FileUtils.moveFile(source, destination)
            } else {
                FileUtils.moveDir(source, destination)
            }
        } catch (e: Exception) {
            log.error("移动文件/文件夹时发生错误", e)
            false
        }

        return if (success) {
            val msg = "成功移动 '${source.name}' 到 '${destination.absolutePath}'"
            log.info(msg)
            McpFileResponse(true, msg)
        } else {
            val msg = "移动 '${source.name}' 到 '${destination.absolutePath}' 失败。可能权限不足或目标路径无效。"
            log.error(msg)
            McpFileResponse(false, msg)
        }
    }

    private fun handleFileSearch(searchDir: File, content: String): McpFileResponse {
        if (!searchDir.isDirectory) {
            val msg = "搜索路径不是一个有效的目录: ${searchDir.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }
        if (!searchDir.exists()) {
            val msg = "搜索路径不存在: ${searchDir.absolutePath}"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }

        return try {
            val searchPattern = Pattern.compile(content, Pattern.CASE_INSENSITIVE) // 不区分大小写搜索
            val results = RecursiveFileSearcher.search(searchDir, searchPattern)

            val formattedResults = StringBuilder()
            if (results.isEmpty()) {
                formattedResults.append("在 '${searchDir.absolutePath}' 中未找到匹配 '${content}' 的内容。")
            } else {
                formattedResults.append("在 '${searchDir.absolutePath}' 中找到匹配 '${content}' 的结果:\n")
                results.forEach { (file, matches) ->
                    formattedResults.append("文件: ${file.absolutePath}\n")
                    matches.forEach { match ->
                        formattedResults.append("  - 行: ${match.start.line + 1}, 列: ${match.start.column + 1}, 匹配: '${match.match}', 预览: '${match.line}'\n")
                    }
                }
            }
            val msg = "文件搜索完成。"
            log.info(msg)
            McpFileResponse(true, msg, formattedResults.toString())
        } catch (e: Exception) {
            val msg = "文件搜索失败: ${e.message}"
            log.error(msg, e)
            McpFileResponse(false, msg)
        }
    }

    private fun handleFileUpload(file: File?, getFolder: Boolean): McpFileResponse {
        if (file == null || !file.exists()) {
            val msg = "文件或文件夹不存在。"
            log.warn(msg)
            return McpFileResponse(false, msg)
        }

        return try {
            val contentBuilder = StringBuilder()
            if (file.isFile && !getFolder) {
                contentBuilder.append("--- 文件内容: ${file.name} ---\n")
                contentBuilder.append(FileIOUtils.readFile2String(file))
                log.info("已读取文件内容: ${file.absolutePath}")
            } else if (file.isDirectory && getFolder) {
                contentBuilder.append("--- 文件夹内容列表: ${file.name} ---\n")
                FileUtils.listFilesInDir(file, true)?.forEach { f ->
                    // 对于文件夹上传，为了避免内容过多，这里只列出文件路径，不包含实际内容。
                    // 如果需要实际内容，可能需要更复杂的协议（例如，压缩传输或逐个文件请求）。
                    contentBuilder.append("  - ${f.absolutePath}\n")
                }
                log.info("已列出文件夹内容: ${file.absolutePath}")
            } else {
                val msg = "命令与文件类型不匹配，请检查 'Files' 和 'getFolder' 参数。如果 Files 是文件，则不需要 getFolder；如果 Files 是文件夹，则需要 getFolder。"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }
            val msg = "成功上传文件/文件夹信息。"
            McpFileResponse(true, msg, contentBuilder.toString())
        } catch (e: Exception) {
            val msg = "上传文件/文件夹失败: ${e.message}"
            log.error(msg, e)
            McpFileResponse(false, msg)
        }
    }

    // endregion

    // region: @File:workspace 命令的具体实现
    private suspend fun handleWorkspaceInstallApk(variant: String): McpFileResponse {
        return withContext(Dispatchers.Main) { // APK 安装涉及到 UI，必须在主线程
            val stopwatch = StopWatch("handleWorkspaceInstallApk")
            try {
                val projectManager = IProjectManager.getInstance()
                val rootProject = projectManager.rootProject
                    ?: return@withContext McpFileResponse(false, "未打开任何项目。")

                val appModule = rootProject.findFirstAndroidAppModule()
                    ?: return@withContext McpFileResponse(false, "项目中未找到 Android 应用模块。")

                // 根据 QuickRunWithCancellationAction.kt 逻辑寻找 outputListingFile
                // 这里需要模拟或获取构建结果中的 outputListingFile
                // 实际在 AndroidIDE 中，这个信息可能从 IBuildService 或 ProjectManager 中获取
                // 暂时假设我们知道典型的构建输出路径
                val buildDir = appModule.buildDir
                val outputApkDir = File(buildDir, "outputs/apk/${variant}")
                if (!outputApkDir.exists() || !outputApkDir.isDirectory) {
                    val msg = "APK 输出目录不存在或无效: ${outputApkDir.absolutePath}。请先执行构建任务（如 assemble${variant.capitalize()}）。"
                    log.error(msg)
                    return@withContext McpFileResponse(false, msg)
                }

                // 在 outputApkDir 中查找最新的 .apk 文件
                val apkFile = outputApkDir.listFiles { _, name -> name.endsWith(".apk") }
                    ?.maxByOrNull { it.lastModified() }

                if (apkFile == null || !apkFile.exists()) {
                    val msg = "在 ${outputApkDir.absolutePath} 中未找到任何 APK 文件。请确保项目已成功构建。"
                    log.error(msg)
                    return@withContext McpFileResponse(false, msg)
                }

                log.info("找到 APK 文件: ${apkFile.absolutePath}")

                val currentActivity = AppUtils.getTopActivity() ?: IDEApplication.instance as? MainActivity
                if (currentActivity == null) {
                    val msg = "无法获取 Activity Context 进行 APK 安装。请确保 AndroidIDE 处于活跃状态。"
                    log.error(msg)
                    return@withContext McpFileResponse(false, msg)
                }

                // 使用 Android 原生 Intent 进行安装
                // 需要确保应用的 AndroidManifest.xml 中配置了 FileProvider
                // 例如：
                // <provider
                //     android:name="androidx.core.content.FileProvider"
                //     android:authorities="${applicationId}.provider"
                //     android:exported="false"
                //     android:grantUriPermissions="true">
                //     <meta-data
                //         android:name="android.support.FILE_PROVIDER_PATHS"
                //         android:resource="@xml/file_paths" />
                // </provider>
                // 并在 res/xml/file_paths.xml 中定义路径：
                // <paths>
                //     <external-path name="external_files" path="."/>
                // </paths>

                val uri = FileProvider.getUriForFile(
                    currentActivity,
                    "${currentActivity.packageName}.provider", // 使用应用的包名作为 authority
                    apkFile
                )

                val installationIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                currentActivity.startActivity(installationIntent)

                val msg = "已尝试安装 APK: ${apkFile.absolutePath} (变体: $variant)。请在设备上确认安装。"
                log.info(msg)
                stopwatch.log()
                McpFileResponse(true, msg)

            } catch (e: Exception) {
                val msg = "安装 APK 失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                // 同时在 UI 上显示闪烁错误
                uiHandler.post { context.flashError(msg) }
                McpFileResponse(false, msg)
            }
        }
    }

    private fun handleWorkspaceGetModuleInfo(): McpFileResponse {
        val stopwatch = StopWatch("handleWorkspaceGetModuleInfo")
        return try {
            val projectManager = IProjectManager.getInstance()
            val rootProject = projectManager.rootProject
                ?: return McpFileResponse(false, "未打开任何项目。")

            val moduleInfoList = mutableListOf<ModuleInfo>()
            rootProject.subProjects.forEach { gradleProject ->
                moduleInfoList.add(ModuleInfo(
                    name = gradleProject.name,
                    absolutePath = gradleProject.projectDir.absolutePath,
                    relativePath = gradleProject.path
                ))
            }

            val gson = Gson()
            val jsonResult = gson.toJson(moduleInfoList)
            val msg = "成功获取模块信息。"
            log.info(msg)
            stopwatch.log()
            McpFileResponse(true, msg, jsonResult)
        } catch (e: Exception) {
            val msg = "获取模块信息失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpFileResponse(false, msg)
        }
    }

    private fun handleWorkspaceGetGradleWrapperInfo(): McpFileResponse {
        val stopwatch = StopWatch("handleWorkspaceGetGradleWrapperInfo")
        return try {
            val projectManager = IProjectManager.getInstance()
            val rootProjectDir = projectManager.rootProject?.projectDir
                ?: return McpFileResponse(false, "未打开任何项目，无法获取 Gradle Wrapper 信息。")

            val wrapperPropertiesFile = File(rootProjectDir, "gradle/wrapper/gradle-wrapper.properties")
            if (!wrapperPropertiesFile.exists()) {
                val msg = "gradle-wrapper.properties 文件不存在: ${wrapperPropertiesFile.absolutePath}"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }

            val content = FileIOUtils.readFile2String(wrapperPropertiesFile)
            val msg = "成功获取 Gradle Wrapper 信息。"
            log.info(msg)
            stopwatch.log()
            McpFileResponse(true, msg, content)
        } catch (e: Exception) {
            val msg = "获取 Gradle Wrapper 信息失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpFileResponse(false, msg)
        }
    }

    private fun handleWorkspaceModifyGradleVersion(newVersion: String, newType: String): McpFileResponse {
        val stopwatch = StopWatch("handleWorkspaceModifyGradleVersion")
        return try {
            val projectManager = IProjectManager.getInstance()
            val rootProjectDir = projectManager.rootProject?.projectDir
                ?: return McpFileResponse(false, "未打开任何项目，无法修改 Gradle Wrapper 版本。")

            val wrapperPropertiesFile = File(rootProjectDir, "gradle/wrapper/gradle-wrapper.properties")
            if (!wrapperPropertiesFile.exists()) {
                val msg = "gradle-wrapper.properties 文件不存在: ${wrapperPropertiesFile.absolutePath}"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }

            var content = FileIOUtils.readFile2String(wrapperPropertiesFile)
            val distributionUrlRegex = "(distributionUrl=)(.*?gradle-)([\\d.]+)(-(bin|all))?(\\.zip)".toRegex()
            val matchResult = distributionUrlRegex.find(content)

            if (matchResult == null) {
                val msg = "在 gradle-wrapper.properties 中未找到 distributionUrl。无法自动修改版本。"
                log.error(msg)
                return McpFileResponse(false, msg)
            }

            // groups: 1=distributionUrl=, 2=base_url_prefix, 3=version, 4=type_group, 5=type, 6=.zip
            val newDistributionUrl = "${matchResult.groupValues[1]}${matchResult.groupValues[2]}${newVersion}-$newType.zip"
            content = content.replace(matchResult.value, newDistributionUrl)

            FileIOUtils.writeFileFromString(wrapperPropertiesFile, content)

            val msg = "成功将 Gradle Wrapper 版本修改为 $newVersion ($newType)。新的 distributionUrl: $newDistributionUrl"
            log.info(msg)
            stopwatch.log()
            // 提示用户可能需要同步项目
            uiHandler.post { context.flashSuccess("Gradle Wrapper 版本已更新。请同步项目。") }
            McpFileResponse(true, msg)
        } catch (e: Exception) {
            val msg = "修改 Gradle Wrapper 版本失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            uiHandler.post { context.flashError(msg) }
            McpFileResponse(false, msg)
        }
    }

    private fun handleWorkspaceGetModuleSrcFileList(moduleName: String): McpFileResponse {
        val stopwatch = StopWatch("handleWorkspaceGetModuleSrcFileList")
        return try {
            val projectManager = IProjectManager.getInstance()
            val rootProject = projectManager.rootProject
                ?: return McpFileResponse(false, "未打开任何项目。")

            val targetModule = rootProject.subProjects.find { it.name == moduleName }
                ?: return McpFileResponse(false, "未找到模块: $moduleName")

            val srcDir = File(targetModule.projectDir, "src") // 假设 src 目录位于模块根目录下
            if (!srcDir.exists() || !srcDir.isDirectory) {
                val msg = "模块 '$moduleName' 的 src 目录不存在或无效: ${srcDir.absolutePath}"
                log.warn(msg)
                return McpFileResponse(false, msg)
            }

            val fileListBuilder = StringBuilder()
            // 递归列出 src 目录下的所有文件
            FileUtils.listFilesInDir(srcDir, true)?.forEach { file ->
                if (file.isFile) {
                    fileListBuilder.append("${file.relativeTo(srcDir).path}\n")
                }
            }
            val msg = "成功获取模块 '${moduleName}' 的 src 文件列表。"
            log.info(msg)
            stopwatch.log()
            McpFileResponse(true, msg, fileListBuilder.toString())
        } catch (e: Exception) {
            val msg = "获取模块 src 文件列表失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpFileResponse(false, msg)
        }
    }
    // endregion

    // region: @gradle 命令的具体实现
    private suspend fun handleGradleRun(): McpGradleTaskResponse {
        return withContext(Dispatchers.Main) { // 构建操作在主线程触发，但实际执行在后台
            val stopwatch = StopWatch("handleGradleRun")
            try {
                // 需要一个 IBuildService 实例
                val buildService = com.itsaky.androidide.lookup.Lookup.getDefault().lookup(IBuildService::class.java)
                    ?: return@withContext McpGradleTaskResponse(false, "构建服务未找到。")

                log.info("开始运行默认 Gradle 构建任务 ('build')...")
                // 这里调用的是默认的构建任务，例如 ':app:build' 或 'build'
                val result = buildService.executeTasks("build") // 或其他默认构建任务

                // 由于 executeTasks 是异步的，这里需要等待结果或依赖回调
                // 这里的 result 是 TaskExecutionResult，它表示操作本身是否成功发送/启动
                // 真正的构建结果会通过 IBuildEventListener 或日志流发送
                // 为了演示，这里假设 result 立即反映了最终结果（这可能不完全准确，取决于 IBuildService 的实现）

                if (result.isSuccessful) {
                    val msg = "Gradle 构建任务已启动。请查看日志输出获取详细进度和结果。"
                    log.info(msg)
                    uiHandler.post { context.flashSuccess("Gradle 构建任务已启动。") }
                    stopwatch.log()
                    McpGradleTaskResponse(true, msg)
                } else {
                    val msg = "启动 Gradle 构建任务失败: ${result.failure?.name ?: "未知原因"}。详细信息请查看 IDE 日志。"
                    log.error(msg)
                    uiHandler.post { context.flashError(msg) }
                    stopwatch.log()
                    McpGradleTaskResponse(false, msg, msg)
                }
            } catch (e: Exception) {
                val msg = "执行 Gradle 构建失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                uiHandler.post { context.flashError(msg) }
                McpGradleTaskResponse(false, msg)
            }
        }
    }
    // endregion

    // region: @task 命令的具体实现
    private suspend fun handleTaskRun(taskName: String): McpGradleTaskResponse {
        return withContext(Dispatchers.Main) { // 触发 Gradle 任务通常在主线程
            val stopwatch = StopWatch("handleTaskRun:$taskName")
            try {
                val buildService = com.itsaky.androidide.lookup.Lookup.getDefault().lookup(IBuildService::class.java)
                    ?: return@withContext McpGradleTaskResponse(false, "构建服务未找到。")

                log.info("正在运行 Gradle Task: $taskName...")
                val result = buildService.executeTasks(taskName)
                if (result.isSuccessful) {
                    val msg = "Gradle Task '$taskName' 已启动。请查看日志输出获取详细进度和结果。"
                    log.info(msg)
                    uiHandler.post { context.flashSuccess("Gradle Task '$taskName' 已启动。") }
                    stopwatch.log()
                    McpGradleTaskResponse(true, msg)
                } else {
                    val msg = "启动 Gradle Task '$taskName' 失败: ${result.failure?.name ?: "未知原因"}。详细信息请查看 IDE 日志。"
                    log.error(msg)
                    uiHandler.post { context.flashError(msg) }
                    stopwatch.log()
                    McpGradleTaskResponse(false, msg, msg)
                }
            } catch (e: Exception) {
                val msg = "执行 Gradle Task '$taskName' 失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                uiHandler.post { context.flashError(msg) }
                McpGradleTaskResponse(false, msg)
            }
        }
    }

    private suspend fun handleTaskList(): McpGradleTaskResponse {
        return withContext(Dispatchers.IO) {
            val stopwatch = StopWatch("handleTaskList")
            try {
                val projectManager = IProjectManager.getInstance()
                val rootProject = projectManager.rootProject
                    ?: return@withContext McpGradleTaskResponse(false, "未打开任何项目，无法获取任务列表。")

                val allTasks = mutableListOf<String>()
                rootProject.subProjects.forEach { gradleProject ->
                    gradleProject.tasks.forEach { task ->
                        allTasks.add("${task.path} - ${task.description ?: "无描述"}")
                    }
                }
                val result = allTasks.joinToString("\n")
                val msg = "成功获取 Gradle 任务列表。"
                log.info(msg)
                stopwatch.log()
                McpGradleTaskResponse(true, msg, result)
            } catch (e: Exception) {
                val msg = "获取 Gradle 任务列表失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                McpGradleTaskResponse(false, msg)
            }
        }
    }

    private suspend fun handleTaskSearch(query: String): McpGradleTaskResponse {
        return withContext(Dispatchers.IO) {
            val stopwatch = StopWatch("handleTaskSearch:$query")
            try {
                val projectManager = IProjectManager.getInstance()
                val rootProject = projectManager.rootProject
                    ?: return@withContext McpGradleTaskResponse(false, "未打开任何项目，无法搜索任务。")

                val matchingTasks = mutableListOf<String>()
                rootProject.subProjects.forEach { gradleProject ->
                    gradleProject.tasks.forEach { task ->
                        if (task.path.contains(query, ignoreCase = true) ||
                            (task.description?.contains(query, ignoreCase = true) == true)) {
                            matchingTasks.add("${task.path} - ${task.description ?: "无描述"}")
                        }
                    }
                }
                val result = if (matchingTasks.isEmpty()) {
                    "未找到匹配 '$query' 的 Gradle 任务。"
                } else {
                    "找到匹配 '$query' 的 Gradle 任务:\n" + matchingTasks.joinToString("\n")
                }
                val msg = "Gradle 任务搜索完成。"
                log.info(msg)
                stopwatch.log()
                McpGradleTaskResponse(true, msg, result)
            } catch (e: Exception) {
                val msg = "搜索 Gradle 任务失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                McpGradleTaskResponse(false, msg)
            }
        }
    }
    // endregion

    // region: @TabFile 命令的具体实现
    private fun handleTabFileGetFunction(editor: IDEEditor, functionName: String): McpTabFileResponse {
        val stopwatch = StopWatch("handleTabFileGetFunction:$functionName")
        val text = editor.getText().toString()
        // 这是一个简单的正则表达式匹配，可能不完全准确，特别是对于复杂的嵌套函数或多行函数签名。
        // 对于 Java/Kotlin，需要更复杂的 AST 解析或 LSP 集成以实现精确的函数提取。
        // 当前实现会尝试匹配常见的函数签名模式，包括修饰符和泛型。
        // 它会捕获函数名、参数和函数体（直到下一个同级花括号结束）。
        val regex = "(?s)(?:(?:public|private|protected|internal|abstract|final|static|open|override)\\s+)*" + // 修饰符
                "(?:<[A-Za-z0-9,<>\\s]+>\\s+)?(?:[A-Za-z0-9_]+\\s+)?\\b${functionName}\\b\\s*\\([^)]*\\)\\s*(\\{.*\\})".toRegex() // 函数名，参数，函数体
        val matchResult = regex.find(text)

        return if (matchResult != null) {
            val functionContent = matchResult.value
            val msg = "成功获取函数 '$functionName' 的内容。"
            log.info(msg)
            stopwatch.log()
            McpTabFileResponse(true, msg, functionContent)
        } else {
            val msg = "未在当前文件中找到函数 '$functionName'。请注意，此功能使用正则表达式，可能无法精确解析所有复杂的函数结构。"
            log.warn(msg)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        }
    }

    private fun handleTabFileGetCursor(editor: IDEEditor): McpTabFileResponse {
        val stopwatch = StopWatch("handleTabFileGetCursor")
        return try {
            val cursorLine = editor.getText().getCursor().leftLine
            val lineContent = editor.getText().getLineString(cursorLine)
            val msg = "成功获取光标所在行内容 (行号: ${cursorLine + 1})。"
            log.info(msg)
            stopwatch.log()
            McpTabFileResponse(true, msg, lineContent)
        } catch (e: Exception) {
            val msg = "获取光标所在行内容失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        }
    }

    private fun handleTabFileGetLine(editor: IDEEditor, lineRange: String): McpTabFileResponse {
        val stopwatch = StopWatch("handleTabFileGetLine:$lineRange")
        return try {
            val parts = lineRange.split("-")
            val startLine = parts[0].toIntOrNull() ?: return McpTabFileResponse(false, "行范围格式错误，请使用 '行号' 或 '起始行-结束行' 格式。")
            val endLine = if (parts.size > 1) parts[1].toIntOrNull() ?: startLine else startLine

            val totalLines = editor.getText().lineCount
            // 检查行号有效性，行号从 1 开始
            if (startLine < 1 || endLine < 1 || startLine > totalLines || endLine > totalLines || startLine > endLine) {
                val msg = "无效的行范围: $lineRange。总行数: $totalLines。请提供有效的行号 (1 - $totalLines)。"
                log.warn(msg)
                return McpTabFileResponse(false, msg)
            }

            val content = (startLine - 1 until endLine).joinToString("\n") { // IDEEditor 行号从 0 开始
                editor.getText().getLineString(it)
            }
            val msg = "成功获取指定行内容 (${startLine}-${endLine} 行)。"
            log.info(msg)
            stopwatch.log()
            McpTabFileResponse(true, msg, content)
        } catch (e: NumberFormatException) {
            val msg = "行范围格式错误，请使用 '行号' 或 '起始行-结束行' 格式。"
            log.error(msg, e)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        } catch (e: Exception) {
            val msg = "获取指定行内容失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        }
    }

    private fun handleTabFileGetFile(editor: IDEEditor): McpTabFileResponse {
        val stopwatch = StopWatch("handleTabFileGetFile")
        return try {
            val content = editor.getText().toString()
            val msg = "成功获取文件完整内容。"
            log.info(msg)
            stopwatch.log()
            McpTabFileResponse(true, msg, content)
        } catch (e: Exception) {
            val msg = "获取文件完整内容失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        }
    }

    private fun handleTabFileSearch(editor: IDEEditor, content: String): McpTabFileResponse {
        val stopwatch = StopWatch("handleTabFileSearch:$content")
        return try {
            val searchResults = mutableListOf<String>()

            // 在主线程执行 UI 搜索操作
            runOnUiThread {
                editor.searcher.search(content) // 这会触发编辑器的搜索和高亮
                val matches = editor.searcher.getMatchedRanges() // 获取匹配的范围

                if (matches.isEmpty()) {
                    searchResults.add("在当前文件中未找到匹配 '$content' 的内容。")
                } else {
                    matches.forEach { range ->
                        val lineIndex = range.start.line
                        val lineContent = editor.getText().getLineString(lineIndex)
                        val startCol = range.start.column
                        val endCol = range.end.column
                        val matchedText = try {
                            lineContent.substring(startCol, endCol)
                        } catch (e: IndexOutOfBoundsException) {
                            // 如果匹配范围超出了行边界，尝试获取部分或整个行
                            log.warn("搜索匹配的范围超出当前行边界。行: $lineIndex, 范围: $startCol-$endCol, 行长: ${lineContent.length}", e)
                            lineContent // 返回整行内容作为 fallback
                        }

                        searchResults.add("行: ${lineIndex + 1}, 列: ${startCol + 1}-${endCol + 1}, 匹配: '$matchedText', 所在行: '$lineContent'")
                    }
                }
            }

            val msg = "文件搜索完成。"
            log.info(msg)
            stopwatch.log()
            McpTabFileResponse(true, msg, searchResults.joinToString("\n"))
        } catch (e: Exception) {
            val msg = "在当前文件搜索失败: ${e.message}"
            log.error(msg, e)
            stopwatch.log()
            McpTabFileResponse(false, msg)
        }
    }
    // endregion

    // region: @shell 命令的具体实现
    private suspend fun handleShellExecute(commandString: String): McpShellResponse {
        return withContext(Dispatchers.IO) {
            val stopwatch = StopWatch("handleShellExecute:$commandString")
            try {
                log.info("正在执行 Shell 命令: $commandString")
                // 使用 executeProcessAsync，它会返回一个 Process 对象
                val process = executeProcessAsync {
                    // 对于复杂的命令，直接 split(" ") 可能不准确，可能需要更智能的命令解析器
                    // 但对于简单的命令，这通常足够。
                    // 确保命令字符串是可执行的
                    command = commandString.split(" ")
                    redirectErrorStream = true // 将错误流重定向到标准输出
                }

                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor() // 等待进程完成并获取退出码

                val msg = "Shell 命令执行完成，退出码: $exitCode"
                log.info(msg)
                stopwatch.log()
                McpShellResponse(true, msg, output, exitCode)
            } catch (e: Exception) {
                val msg = "执行 Shell 命令失败: ${e.message}"
                log.error(msg, e)
                stopwatch.log()
                McpShellResponse(false, msg = "执行 Shell 命令失败: ${e.message}", output = e.message, exitCode = -1)
            }
        }
    }
    // endregion
}
