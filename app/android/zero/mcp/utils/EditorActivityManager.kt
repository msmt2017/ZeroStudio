package android.zero.mcp.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import java.lang.ref.WeakReference

/**
 * 编辑器活动管理器
 * 用于获取和管理当前编辑器活动实例
 */
object EditorActivityManager {
    
    private var currentEditorActivityRef: WeakReference<EditorHandlerActivity>? = null
    
    /**
     * 注册编辑器活动
     */
    fun registerEditorActivity(activity: EditorHandlerActivity) {
        currentEditorActivityRef = WeakReference(activity)
    }
    
    /**
     * 注销编辑器活动
     */
    fun unregisterEditorActivity() {
        currentEditorActivityRef?.clear()
        currentEditorActivityRef = null
    }
    
    /**
     * 获取当前编辑器活动
     */
    fun getCurrentEditorActivity(): EditorHandlerActivity? {
        return currentEditorActivityRef?.get()
    }
    
    /**
     * 检查是否有活动的编辑器
     */
    fun hasActiveEditor(): Boolean {
        return getCurrentEditorActivity() != null
    }
    
    /**
     * 获取当前标签页索引
     */
    fun getCurrentTabIndex(): Int {
        return try {
            val activity = getCurrentEditorActivity()
            activity?.getCurrentTabIndex() ?: 0
        } catch (e: Exception) {
            Logger.log("获取当前标签页索引失败: ${e.message}")
            0
        }
    }
    
    /**
     * 获取标签页总数
     */
    fun getTotalTabCount(): Int {
        return try {
            val activity = getCurrentEditorActivity()
            activity?.getTotalTabCount() ?: 0
        } catch (e: Exception) {
            Logger.log("获取标签页总数失败: ${e.message}")
            0
        }
    }
    
    /**
     * 获取所有标签页信息
     */
    fun getAllTabsInfo(): List<TabInfo> {
        return try {
            val activity = getCurrentEditorActivity()
            activity?.getAllTabsInfo() ?: emptyList()
        } catch (e: Exception) {
            Logger.log("获取所有标签页信息失败: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * 切换到指定标签页
     */
    fun switchToTab(tabIndex: Int): Boolean {
        return try {
            val activity = getCurrentEditorActivity()
            activity?.switchToTab(tabIndex) ?: false
        } catch (e: Exception) {
            Logger.log("切换到标签页失败: ${e.message}")
            false
        }
    }
    
    /**
     * 关闭指定标签页
     */
    fun closeTab(tabIndex: Int): Boolean {
        return try {
            val activity = getCurrentEditorActivity()
            activity?.closeTab(tabIndex) ?: false
        } catch (e: Exception) {
            Logger.log("关闭标签页失败: ${e.message}")
            false
        }
    }
    
    /**
     * 通过ActivityManager查找编辑器活动
     * 这是一个备选方案，用于在无法直接获取引用时使用
     */
    fun findEditorActivityFromTaskManager(context: Context): EditorHandlerActivity? {
        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningTasks = activityManager.getRunningTasks(10)
            
            for (taskInfo in runningTasks) {
                val topActivity = taskInfo.topActivity
                if (topActivity?.className?.contains("EditorHandlerActivity") == true) {
                    // 这里需要根据实际情况来获取Activity实例
                    // 可能需要通过反射或其他方式
                    return null // 暂时返回null，需要进一步实现
                }
            }
        } catch (e: Exception) {
            // 权限不足或其他异常
        }
        return null
    }
}

/**
 * 标签页信息
 */
data class TabInfo(
    val index: Int,
    val fileName: String,
    val filePath: String,
    val isActive: Boolean,
    val isModified: Boolean,
    val lineCount: Int
) 