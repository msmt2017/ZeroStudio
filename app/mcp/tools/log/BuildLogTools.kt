package android.zero.mcp.tools.log

import android.zero.mcp.McpRequest
import android.zero.mcp.McpResponse
import android.zero.mcp.McpTool
import android.zero.mcp.McpServerLog
import com.itsaky.androidide.fragments.output.BuildOutputFragment
import com.itsaky.androidide.fragments.output.AppLogFragment
import com.itsaky.androidide.fragments.output.IDELogFragment
import com.itsaky.androidide.fragments.output.LogViewFragment
import com.itsaky.androidide.adapters.EditorBottomSheetTabAdapter
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.ui.EditorBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Tool: @BuildLog:getBuildLog
 * 功能：获取和BuildOutputFragment编译构建输出（gradle构建运行生成的task任务构建日志）输出的日志内容
 * 示例：@BuildLog:getBuildLog
 */
class GetBuildLogTool(
    private val getEditorActivity: () -> EditorHandlerActivity?
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("获取构建日志...")
            
            val activity = getEditorActivity()
            if (activity == null) {
                return@withContext McpResponse.error("编辑器活动不可用", request.id)
            }
            
            // 获取BuildOutputFragment
            val buildOutputFragment = activity.getBuildOutputFragment()
            if (buildOutputFragment == null) {
                return@withContext McpResponse.error("构建输出Fragment不可用", request.id)
            }
            
            // 获取构建日志内容
            val buildLogContent = buildOutputFragment.getContent()
            
            val result = buildString {
                appendLine("📋 构建日志")
                appendLine("=" * 50)
                appendLine("日志类型: Gradle构建输出")
                appendLine("获取时间: ${System.currentTimeMillis()}")
                appendLine("内容长度: ${buildLogContent.length} 字符")
                appendLine()
                appendLine("📄 日志内容:")
                if (buildLogContent.isNotBlank()) {
                    appendLine(buildLogContent)
                } else {
                    appendLine("(暂无构建日志)")
                }
                appendLine()
                appendLine("💡 说明:")
                appendLine("- 此日志包含Gradle构建过程的输出")
                appendLine("- 包括任务执行、编译、打包等信息")
                appendLine("- 日志会实时更新构建状态")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetBuildLogTool error: ${e.message}")
            McpResponse.error("获取构建日志失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @BuildLog:getAppLog
 * 功能：获取IDE构建开发的apk安装后运行输出的日志，参考AppLogFragment的实现
 * 示例：@BuildLog:getAppLog
 */
class GetAppLogTool(
    private val getEditorActivity: () -> EditorHandlerActivity?
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("获取应用日志...")
            
            val activity = getEditorActivity()
            if (activity == null) {
                return@withContext McpResponse.error("编辑器活动不可用", request.id)
            }
            
            // 获取AppLogFragment
            val appLogFragment = activity.getAppLogFragment()
            if (appLogFragment == null) {
                return@withContext McpResponse.error("应用日志Fragment不可用", request.id)
            }
            
            // 获取应用日志内容
            val appLogContent = appLogFragment.getContent()
            
            val result = buildString {
                appendLine("📱 应用日志")
                appendLine("=" * 50)
                appendLine("日志类型: 应用运行日志")
                appendLine("获取时间: ${System.currentTimeMillis()}")
                appendLine("内容长度: ${appLogContent.length} 字符")
                appendLine()
                appendLine("📄 日志内容:")
                if (appLogContent.isNotBlank()) {
                    appendLine(appLogContent)
                } else {
                    appendLine("(暂无应用日志)")
                }
                appendLine()
                appendLine("💡 说明:")
                appendLine("- 此日志包含已安装APK的运行输出")
                appendLine("- 包括应用启动、运行、错误等信息")
                appendLine("- 需要LogSender服务连接才能获取日志")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetAppLogTool error: ${e.message}")
            McpResponse.error("获取应用日志失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @BuildLog:getIDELog
 * 功能：获取IDE构建与运行时输出的完整日志信息文本内容，参考IDELogFragment的实现
 * 示例：@BuildLog:getIDELog
 */
class GetIDELogTool(
    private val getEditorActivity: () -> EditorHandlerActivity?
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("获取IDE日志...")
            
            val activity = getEditorActivity()
            if (activity == null) {
                return@withContext McpResponse.error("编辑器活动不可用", request.id)
            }
            
            // 获取IDELogFragment
            val ideLogFragment = activity.getIDELogFragment()
            if (ideLogFragment == null) {
                return@withContext McpResponse.error("IDE日志Fragment不可用", request.id)
            }
            
            // 获取IDE日志内容
            val ideLogContent = ideLogFragment.getContent()
            
            val result = buildString {
                appendLine("🔧 IDE日志")
                appendLine("=" * 50)
                appendLine("日志类型: IDE运行日志")
                appendLine("获取时间: ${System.currentTimeMillis()}")
                appendLine("内容长度: ${ideLogContent.length} 字符")
                appendLine()
                appendLine("📄 日志内容:")
                if (ideLogContent.isNotBlank()) {
                    appendLine(ideLogContent)
                } else {
                    appendLine("(暂无IDE日志)")
                }
                appendLine()
                appendLine("💡 说明:")
                appendLine("- 此日志包含AndroidIDE的运行输出")
                appendLine("- 包括IDE启动、插件加载、错误等信息")
                appendLine("- 使用Logback框架记录日志")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetIDELogTool error: ${e.message}")
            McpResponse.error("获取IDE日志失败: ${e.message}", request.id)
        }
    }
}

/**
 * Tool: @BuildLog:getLogView
 * 功能：获取log日志，参考LogViewFragment里面的代码实现
 * 示例：@BuildLog:getLogView
 */
class GetLogViewTool(
    private val getEditorActivity: () -> EditorHandlerActivity?
) : McpTool {
    override suspend fun invoke(request: McpRequest): McpResponse = withContext(Dispatchers.IO) {
        try {
            McpServerLog.log("获取日志视图...")
            
            val activity = getEditorActivity()
            if (activity == null) {
                return@withContext McpResponse.error("编辑器活动不可用", request.id)
            }
            
            // 获取当前活动的日志Fragment
            val currentLogFragment = activity.getCurrentLogFragment()
            if (currentLogFragment == null) {
                return@withContext McpResponse.error("日志Fragment不可用", request.id)
            }
            
            // 获取日志内容
            val logContent = currentLogFragment.getContent()
            val fragmentName = currentLogFragment.javaClass.simpleName
            
            val result = buildString {
                appendLine("📊 日志视图")
                appendLine("=" * 50)
                appendLine("当前视图: $fragmentName")
                appendLine("获取时间: ${System.currentTimeMillis()}")
                appendLine("内容长度: ${logContent.length} 字符")
                appendLine()
                appendLine("📄 日志内容:")
                if (logContent.isNotBlank()) {
                    appendLine(logContent)
                } else {
                    appendLine("(当前视图无日志内容)")
                }
                appendLine()
                appendLine("💡 说明:")
                appendLine("- 显示当前活动的日志视图内容")
                appendLine("- 可能是构建输出、应用日志或IDE日志")
                appendLine("- 内容会根据当前选择的标签页变化")
            }
            
            McpResponse.success(request.id, result)
            
        } catch (e: Exception) {
            McpServerLog.log("GetLogViewTool error: ${e.message}")
            McpResponse.error("获取日志视图失败: ${e.message}", request.id)
        }
    }
} 