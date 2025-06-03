
package com.itsaky.androidide.handlers

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.itsaky.androidide.eventbus.events.Event
import com.itsaky.androidide.eventbus.events.EventReceiver
import com.itsaky.androidide.eventbus.events.editor.OnCreateEvent
import com.itsaky.androidide.eventbus.events.editor.OnDestroyEvent
import com.itsaky.androidide.eventbus.events.editor.OnPauseEvent
import com.itsaky.androidide.eventbus.events.editor.OnResumeEvent
import com.itsaky.androidide.eventbus.events.editor.OnStartEvent
import com.itsaky.androidide.eventbus.events.editor.OnStopEvent
import com.itsaky.androidide.projects.ProjectManagerImpl
import com.itsaky.androidide.projects.util.BootClasspathProvider
import com.itsaky.androidide.utils.EditorActivityActions
import com.itsaky.androidide.utils.EditorSidebarActions
import com.itsaky.androidide.utils.Environment
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.CompletableFuture

/**
 * Observes lifecycle events if [com.itsaky.androidide.EditorActivityKt].
 *
 * @author Akash Yadav
 */
class EditorActivityLifecyclerObserver : DefaultLifecycleObserver {

  private val fileActionsHandler = FileTreeActionHandler()

  override fun onCreate(owner: LifecycleOwner) {
    EditorActivityActions.register(owner as Context)
    EditorSidebarActions.registerActions(owner as Context)
    dispatchEvent(OnCreateEvent())
  }

  override fun onStart(owner: LifecycleOwner) {
    CompletableFuture.runAsync(this::initBootclasspathProvider)
    register(fileActionsHandler, ProjectManagerImpl.getInstance())

    dispatchEvent(OnStartEvent())
  }

  override fun onResume(owner: LifecycleOwner) {
    EditorActivityActions.register(owner as Context)
    dispatchEvent(OnResumeEvent())
  }

  override fun onPause(owner: LifecycleOwner) {
    EditorActivityActions.clear()
    dispatchEvent(OnPauseEvent())
  }

  override fun onStop(owner: LifecycleOwner) {
    unregister(fileActionsHandler, ProjectManagerImpl.getInstance())
    dispatchEvent(OnStopEvent())
  }

  override fun onDestroy(owner: LifecycleOwner) {
    dispatchEvent(OnDestroyEvent())
  }

  private fun register(vararg receivers: EventReceiver) {
    receivers.forEach { it.register() }
  }

  private fun unregister(vararg receivers: EventReceiver) {
    receivers.forEach { it.unregister() }
  }

  private fun dispatchEvent(event: Event) {
    EventBus.getDefault().post(event)
  }

  private fun initBootclasspathProvider() {
    BootClasspathProvider.update(listOf(Environment.ANDROID_JAR.absolutePath))
  }
}
