/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.lint.checks

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WatchFaceEditorDetector : Detector(), XmlScanner {
  companion object Issues {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "WatchFaceEditor",
        briefDescription = "Watch face editor must use launchMode=\"standard\"",
        explanation =
          """
                Watch face editor activities must be able to launch in the Wear OS companion app activity \
                task in order to work correctly. Thus only `launchMode="standard"` is allowed. The watch \
                face will not be shown on the watch if it does not satisfy this requirement.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(WatchFaceEditorDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )

    const val WATCH_FACE_EDITOR_ACTION = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"
  }

  override fun getApplicableElements() = listOf(TAG_ACTION)

  override fun visitElement(context: XmlContext, action: Element) {
    if (action.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION) {
      val activity = action.parentNode?.parentNode as? Element
      if (activity == null || activity.nodeName != TAG_ACTIVITY) return
      val launchMode = activity.getAttributeNS(ANDROID_URI, "launchMode")
      if (launchMode.isEmpty()) return // default is "standard"
      if (launchMode != "standard") {
        val fix = fix().set().attribute("launchMode").value("standard").android().build()
        context.report(
          Incident(
            ISSUE,
            activity,
            context.getNameLocation(activity.getAttributeNodeNS(ANDROID_URI, "launchMode")),
            "Watch face editor must use launchMode=\"standard\"",
            fix,
          )
        )
      }
    }
  }
}
