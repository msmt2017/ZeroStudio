/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.VALUE_TRUE
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.utils.next
import com.android.utils.subtag
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

/**
 * Reports the <application> element of the merged manifest if all the following hold:
 * 1. There is an <activity> element in the merged manifest with supportsPictureInPicture=true.
 * 2. There is some Java/Kotlin code that indicates PiP implementation (calls to
 *    setPictureInPictureParams or enterPictureInPictureMode).
 * 3. The Java/Kotlin code does NOT use the new approach (no calls to setAutoEnterEnabled or no
 *    calls to setSourceRectHint; both must be called when using the new approach, so missing either
 *    of these results in a warning).
 * 4. The targetSdkVersion is Android 12 or above.
 *
 * Requirement (2) ensures we don't incorrectly report a warning when a prebuilt dependency provides
 * a PiP activity; the activity would end up in the merged manifest, but we won't see ANY PiP code
 * for it. Thus, we only report a warning if we can see PiP implementation code AND we cannot see
 * the new approach.
 */
class PictureInPictureDetector : Detector(), SourceCodeScanner {

  companion object {
    private val IMPLEMENTATION =
      Implementation(PictureInPictureDetector::class.java, EnumSet.of(Scope.ALL_JAVA_FILES))

    @JvmField
    val ISSUE =
      Issue.create(
        id = "PictureInPictureIssue",
        briefDescription = "Picture In Picture best practices not followed",
        explanation =
          """
          Starting in Android 12, the recommended approach for enabling picture-in-picture (PiP) \
          has changed. If your app does not use the new approach, your app's transition animations \
          will be of poor quality compared to other apps. The new approach requires calling \
          `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`.
        """,
        moreInfo =
          "https://developer.android.com/develop/ui/views/picture-in-picture#smoother-transition",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    const val FOUND_AUTO_ENTER_USAGE = "autoEnterUsage"
    const val FOUND_SRC_RECT_HINT_USAGE = "sourceRectHintUsage"
    const val FOUND_ENTER_PIP_MODE_USAGE = "enterPipMode"
    const val FOUND_SET_PIP_PARAMS_USAGE = "pipParamsUsage"
    const val ATTR_SUPPORTS_PICTURE_IN_PICTURE = "supportsPictureInPicture"
  }

  override fun getApplicableMethodNames() =
    listOf(
      "setSourceRectHint",
      "setAutoEnterEnabled",
      "enterPictureInPictureMode",
      "setPictureInPictureParams",
      "trackPipAnimationHintView",
    )

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val map = context.getPartialResults(ISSUE).map()

    val containingClass = method.containingClass ?: return
    val containingClassFqn = containingClass.qualifiedName ?: return

    fun isMethod(methodName: String, classFqn: String): Boolean =
      method.name == methodName &&
        (containingClassFqn == classFqn ||
          context.evaluator.extendsClass(containingClass, classFqn, false))

    when {
      isMethod("setAutoEnterEnabled", "android.app.PictureInPictureParams.Builder") -> {
        map.put(FOUND_AUTO_ENTER_USAGE, true)
      }
      isMethod("setSourceRectHint", "android.app.PictureInPictureParams.Builder") -> {
        map.put(FOUND_SRC_RECT_HINT_USAGE, true)
      }
      isMethod("setPictureInPictureParams", CLASS_ACTIVITY) -> {
        map.put(FOUND_SET_PIP_PARAMS_USAGE, true)
      }
      isMethod("enterPictureInPictureMode", CLASS_ACTIVITY) -> {
        map.put(FOUND_ENTER_PIP_MODE_USAGE, true)
      }
      // This AndroidX function calls setSourceRectHint.
      method.name == "trackPipAnimationHintView" -> {
        map.put(FOUND_SRC_RECT_HINT_USAGE, true)
      }
    }
  }

  override fun afterCheckEachProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) {
      return
    }

    if (context.mainProject.targetSdk < VersionCodes.S) {
      return
    }

    val application =
      context.mainProject.mergedManifest?.documentElement?.subtag(TAG_APPLICATION) ?: return
    var activity = application.subtag(TAG_ACTIVITY)

    var isFoundPipActivity = false
    while (activity != null) {
      if (activity.getAttributeNS(ANDROID_URI, ATTR_SUPPORTS_PICTURE_IN_PICTURE) == VALUE_TRUE) {
        isFoundPipActivity = true
        break
      }
      activity = activity.next(TAG_ACTIVITY)
    }

    // No activity found that supports PiP.
    if (!isFoundPipActivity) {
      return
    }

    val combinedMap = LintMap()
    partialResults.maps().forEach { combinedMap.putAll(it) }

    // Return early if it seems like there is no PiP implementation code.
    if (
      !combinedMap.containsKey(FOUND_SET_PIP_PARAMS_USAGE) &&
        !combinedMap.containsKey(FOUND_ENTER_PIP_MODE_USAGE)
    ) {
      return
    }

    // Return early if BOTH new approach functions are called.
    if (
      combinedMap.containsKey(FOUND_AUTO_ENTER_USAGE) &&
        combinedMap.containsKey(FOUND_SRC_RECT_HINT_USAGE)
    ) {
      return
    }

    context.report(
      Incident(context)
        .issue(ISSUE)
        .location(context.getLocation(application))
        .message(
          "An activity in this app supports picture-in-picture and the " +
            "targetSdkVersion is 31 or above; it is therefore strongly recommended to call " +
            "both `setAutoEnterEnabled(true)` and `setSourceRectHint(...)`"
        )
    )
  }
}
