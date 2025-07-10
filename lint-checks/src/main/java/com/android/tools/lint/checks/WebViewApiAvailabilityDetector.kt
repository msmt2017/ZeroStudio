/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement

/** Looks for `WebView` method usages that should be replaced by `androidx.webkit` methods. */
class WebViewApiAvailabilityDetector : Detector(), SourceCodeScanner {
  companion object {
    private const val WEBVIEW_CLASS_NAME = "android.webkit.WebView"

    // There are some methods that we never intend to bring to AndroidX (ex.
    // getRendererPriorityWaivedWhenNotVisible), and others weren't brought over yet (ex.
    // setDataDirectorySuffix). This list ensures we don't issue a warning for any of them.
    private val BLOCKED_METHODS =
      setOf(
        "getAccessibilityClassName",
        "onProvideVirtualStructure",
        "autofill",
        "getRendererPriorityWaivedWhenNotVisible",
        "getRendererRequestedPriority",
        "onProvideAutofillVirtualStructure",
        "setRendererPriorityPolicy",
        "getTextClassifier",
        "setTextClassifier",
        "getWebViewClassLoader",
        "disableWebView",
        "setDataDirectorySuffix",
        "getWebViewLooper",
        "isVisibleToUserForAutofill",
      )

    /** Main issue investigated by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "WebViewApiAvailability",
        briefDescription = "WebView API Availability",
        explanation =
          "The `androidx.webkit` library is a static library you can add to your " +
            "Android application allowing you to use new APIs on older platform " +
            "versions, targeting more devices.",
        category = Category.CORRECTNESS,
        priority = 7,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/reference/androidx/webkit/package-summary",
        implementation =
          Implementation(WebViewApiAvailabilityDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf<Class<out UElement>>(UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return Handler(context)
  }

  private class Handler(private val context: JavaContext) : UElementHandler() {

    override fun visitCallExpression(node: UCallExpression) {
      val method = node.resolve() ?: return

      if (BLOCKED_METHODS.contains(method.name)) {
        return
      }

      val evaluator = context.evaluator
      if (!evaluator.isMemberInClass(method, WEBVIEW_CLASS_NAME)) {
        return
      }

      val client = context.client
      val apiLookup = ApiLookup.getOrNull(client, context.project.buildTarget) ?: return
      val api =
        apiLookup.getMethodVersions(
          WEBVIEW_CLASS_NAME,
          method.name,
          evaluator.getMethodDescription(method, includeName = false, includeReturn = false)!!,
        )

      // Note: we expect to bump the maximum sdk for future releases (but doing so requires
      // updating the deny list).
      if (api == ApiConstraint.UNKNOWN) {
        return
      }
      val level = api.min()
      if (level <= 21 || level > 28) {
        return
      }
      if (!VersionChecks.isWithinVersionCheckConditional(client, evaluator, node, api)) {
        return
      }

      val incident =
        Incident(
          issue = ISSUE,
          scope = node,
          location = context.getLocation(node),
          message =
            "Consider using `WebViewCompat." +
              method.name +
              "` instead which will support more devices.",
        )
      context.report(incident)
    }
  }
}
