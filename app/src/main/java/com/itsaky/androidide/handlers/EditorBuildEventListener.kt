/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.handlers

import com.itsaky.androidide.R
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.preferences.internal.isFirstBuild
import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.services.builder.GradleBuildService
import com.itsaky.androidide.tooling.api.messages.result.BuildInfo
import com.itsaky.androidide.tooling.events.ProgressEvent
import com.itsaky.androidide.tooling.events.configuration.ProjectConfigurationStartEvent
import com.itsaky.androidide.tooling.events.task.TaskStartEvent
import com.itsaky.androidide.utils.flashError
import com.itsaky.androidide.utils.flashSuccess
import java.lang.ref.WeakReference

/**
 * Handles events received from [GradleBuildService] updates [EditorHandlerActivity].
 * @author Akash Yadav
 */
class EditorBuildEventListener : GradleBuildService.EventListener {

  private var activityReference: WeakReference<EditorHandlerActivity> = WeakReference(null)

  fun setActivity(activity: EditorHandlerActivity) {
    this.activityReference = WeakReference(activity)
  }

  override fun prepareBuild(buildInfo: BuildInfo) {
  val act = getSafeActivity() ?: return
    val isFirstBuild = isFirstBuild
    /**activity()*/act.setStatus(
        /**activity()*/act.getString(if (isFirstBuild) string.preparing_first else string.preparing)
      )

    if (isFirstBuild) {
      /**activity()*/act.showFirstBuildNotice()
    }

    /**activity()*/act.editorViewModel.isBuildInProgress = true
    /**activity()*/act.binding.bottomSheet.clearBuildOutput()

    if (buildInfo.tasks.isNotEmpty()) {
      /**activity()*/act.binding.bottomSheet.appendBuildOut(
        /**activity()*/act.getString(R.string.title_run_tasks) + " : " + buildInfo.tasks)
    }
  }

  override fun onBuildSuccessful(tasks: List<String?>) {
  val act = getSafeActivity() ?: return
    analyzeCurrentFile()

    isFirstBuild = false
    /**activity()*/act.editorViewModel.isBuildInProgress = false

    /**activity()*/act.flashSuccess(R.string.build_status_sucess)
  }

  override fun onProgressEvent(event: ProgressEvent) {
  val act = getSafeActivity() ?: return
    if (event is ProjectConfigurationStartEvent || event is TaskStartEvent) {
      /**activity()*/act.setStatus(event.descriptor.displayName)
    }
  }

  override fun onBuildFailed(tasks: List<String?>) {
val act = getSafeActivity() ?: return
    analyzeCurrentFile()

    isFirstBuild = false
    /**activity()*/act.editorViewModel.isBuildInProgress = false

    /**activity()*/act.flashError(R.string.build_status_failed)
  }

  override fun onOutput(line: String?) {
val act = getSafeActivity() ?: return
    line?.let { /**activity()*/act.appendBuildOutput(it) }
    // TODO This can be handled better when ProgressEvents are received from Tooling API server
    if (line!!.contains("BUILD SUCCESSFUL") || line.contains("BUILD FAILED")) {
      /**activity()*/act.setStatus(line)
    }
  }

  private fun analyzeCurrentFile() {
val act = getSafeActivity() ?: return
    val editorView = /**activity()*/act.getCurrentEditor()
    if (editorView != null) {
      val editor = editorView.editor
      editor?.analyze()
    }
  }
// /**
     // * Safely gets the activity reference without throwing an exception.
     // * This is the most efficient way to check the activity's state.
     // * Returns null if the activity is null, finishing, or has been destroyed.
     // val act = activityReference.get()
        // if (act == null || act.isDestroying) {
            // return
        // }
     // */
    // private fun getSafeActivity(): EditorHandlerActivity? {
        // val activity = activityReference.get()
        // if (activity == null || activity.isDestroying) {
            // return null
        // }
        // return activity
    // }
    
    //val act = getSafeActivity() ?: return
    private fun getSafeActivity(): EditorHandlerActivity? {
    val activity = activityReference.get()
    return if (activity == null || activity.isDestroying) null else activity
}




  fun activity(): EditorHandlerActivity {
    return activityReference.get()
      ?: throw IllegalStateException("Activity reference has been destroyed!")
  }
}
