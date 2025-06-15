package android.zero.mcp.registry

import android.content.Context
import android.zero.mcp.McpServer
import android.zero.mcp.tools.file.*
import android.zero.mcp.tools.gradle.*
import android.zero.mcp.tools.shell.ExecuteShellTool
import android.zero.mcp.tools.tabfile.*
import android.zero.mcp.tools.task.*
import android.zero.mcp.tools.workspace.*
import com.itsaky.androidide.project.ProjectManager
import com.termux.shell.TermuxShellExecutor
import io.github.rosemoe.sora.text.Content
import java.io.File

object McpToolRegistry2 {

    fun registerAllTools(context: Context, mcpServer: McpServer) {
        val projectManager = ProjectManager.getInstance()
        val workspaceRoot = projectManager.getWorkspaceRootDir()
        val gradleWrapperFile = File(workspaceRoot, "gradle/wrapper/gradle-wrapper.properties")
        val termuxExecutor = TermuxShellExecutor.getInstance()

        val getEditorContent: () -> Content? = {
            // 替换为你项目中的编辑器访问方式
            projectManager.getActiveEditor()?.content
        }

        val getCursor = {
            projectManager.getActiveEditor()?.cursor
        }

        val getApkFile: (String) -> File? = { variant ->
            val apkDir = File(workspaceRoot, "app/build/outputs/apk/$variant")
            apkDir.listFiles()?.firstOrNull { it.extension == "apk" }
        }

        val launchInstaller: (uri: android.net.Uri) -> Unit = { uri ->
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                data = uri
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                type = "application/vnd.android.package-archive"
            }
            context.startActivity(intent)
        }

        val gradleTaskManager = com.itsaky.androidide.gradle.GradleTaskManager()

        // Register TabFile tools
        mcpServer.registerTool("TabFile.getCursor", GetCursorTool(getCursor, getEditorContent))
        mcpServer.registerTool("TabFile.getFile", GetFileTool(getEditorContent))
        mcpServer.registerTool("TabFile.searchTabFile", SearchTabFileTool(getEditorContent))
        mcpServer.registerTool("TabFile.getFunction", GetFunctionTool(getEditorContent))
        mcpServer.registerTool("TabFile.insertLine", InsertLineTool(getEditorContent))
        mcpServer.registerTool("TabFile.replaceLine", ReplaceLineTool(getEditorContent))
        mcpServer.registerTool("TabFile.deleteLine", DeleteLineTool(getEditorContent))

        // Register Gradle tools
        mcpServer.registerTool("gradle.run-project", RunProjectTool { projectManager.quickRun() })
        mcpServer.registerTool("gradle.Refresh-project", RefreshProjectTool())

        // Register Task tools
        mcpServer.registerTool("task.runTask", RunTaskTool(gradleTaskManager))
        mcpServer.registerTool("task.taskList", TaskListTool(gradleTaskManager))
        mcpServer.registerTool("task.searchTask", SearchTaskTool(gradleTaskManager))

        // Register File:workspace tools
        mcpServer.registerTool("File.workspace.getmoduleInfo", GetModuleInfoTool(projectManager))
        mcpServer.registerTool("File.workspace.getGradleWrapperInfo", GetGradleWrapperInfoTool(gradleWrapperFile))
        mcpServer.registerTool("File.workspace.getinstallApk", InstallApkTool(getApkFile, launchInstaller))
        mcpServer.registerTool("File.workspace.ModifyGradleVersion", ModifyGradleVersionTool(gradleWrapperFile))
        mcpServer.registerTool("File.workspace.GetModuleSrcFileList", GetModuleSrcFileListTool(workspaceRoot))

        // Register File operations
        mcpServer.registerTool("File.WriteFile", WriteFileTool(workspaceRoot))
        mcpServer.registerTool("File.Rename", RenameFileTool(workspaceRoot))
        mcpServer.registerTool("File.move", MoveFileTool(workspaceRoot))
        mcpServer.registerTool("File.copy", CopyFileTool(workspaceRoot))
        mcpServer.registerTool("File.delete", FileDeleteTool(workspaceRoot))
        mcpServer.registerTool("File.search", FileSearchTool(workspaceRoot))
        mcpServer.registerTool("File.create", FileCreateTool(workspaceRoot))
        mcpServer.registerTool("File.info", FileInfoTool(workspaceRoot))

        // Register Termux Shell
        mcpServer.registerTool("shell.execute", ExecuteShellTool(termuxExecutor))
    }
} 
