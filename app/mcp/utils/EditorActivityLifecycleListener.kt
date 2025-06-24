package android.zero.mcp.utils

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.itsaky.androidide.activities.editor.EditorHandlerActivity

/**
 * 编辑器活动生命周期监听器
 * 用于自动管理编辑器活动的注册和注销
 */
class EditorActivityLifecycleListener : Application.ActivityLifecycleCallbacks, LifecycleObserver {

    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {
        if (activity is EditorHandlerActivity) {
            EditorActivityManager.registerEditorActivity(activity)
        }
    }

    override fun onActivityStarted(activity: android.app.Activity) {
        // 不需要特殊处理
    }

    override fun onActivityResumed(activity: android.app.Activity) {
        if (activity is EditorHandlerActivity) {
            // 确保编辑器活动被注册
            EditorActivityManager.registerEditorActivity(activity)
        }
    }

    override fun onActivityPaused(activity: android.app.Activity) {
        // 不需要特殊处理
    }

    override fun onActivityStopped(activity: android.app.Activity) {
        // 不需要特殊处理
    }

    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {
        // 不需要特殊处理
    }

    override fun onActivityDestroyed(activity: android.app.Activity) {
        if (activity is EditorHandlerActivity) {
            EditorActivityManager.unregisterEditorActivity()
        }
    }

    /**
     * 注册到应用程序
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    /**
     * 注销监听器
     */
    fun unregister(application: Application) {
        application.unregisterActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onAppBackgrounded() {
        // 应用进入后台时的处理
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onAppForegrounded() {
        // 应用进入前台时的处理
    }
} 