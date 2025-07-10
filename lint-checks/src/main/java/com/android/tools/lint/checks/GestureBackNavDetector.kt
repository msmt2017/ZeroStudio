/*
 * Copyright (C) 2022 The Android Open Source Project
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
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.utils.subtag
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.skipParenthesizedExprUp
import org.w3c.dom.Document

/**
 * Looks for usages of KeyEvent.KEYCODE_BACK in an if/switch conditional and warns user as it's a
 * signal for handling a custom back navigation.
 */
class GestureBackNavDetector : ResourceXmlDetector(), SourceCodeScanner {
  override fun getApplicableReferenceNames(): List<String> = listOf("KEYCODE_BACK")

  private fun checkEnabledBackInvokedCallback(document: Document?): Boolean {
    val manifest = document?.documentElement
    val application = manifest?.subtag(TAG_APPLICATION)
    return application?.getAttributeNS(ANDROID_URI, ENABLE_ON_BACK_INVOKED_CALLBACK) == VALUE_TRUE
  }

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    if (
      referenced is PsiField &&
        context.evaluator.isMemberInClass(referenced, "android.view.KeyEvent")
    ) {
      val keycodeBack = skipParenthesizedExprUp(reference.uastParent) ?: return
      val parent = skipParenthesizedExprUp(keycodeBack.uastParent) ?: return
      val ifExpression = skipParenthesizedExprUp(parent.uastParent) ?: return
      if (
        ifExpression is UIfExpression ||
          ifExpression is USwitchClauseExpression ||
          parent is USwitchClauseExpression
      ) {
        val message =
          "If intercepting back events, this should be handled through " +
            "the registration of callbacks on the window level; " +
            "Please see https://developer.android.com/about/versions/13/features/predictive-back-gesture"
        val fix =
          fix()
            .url("https://developer.android.com/about/versions/13/features/predictive-back-gesture")
            .build()
        context.report(
          Incident(ISSUE, reference, context.getLocation(keycodeBack), message, fix),
          map(),
        )
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    return checkEnabledBackInvokedCallback(context.mainProject.mergedManifest)
  }

  companion object {
    private const val ENABLE_ON_BACK_INVOKED_CALLBACK = "enableOnBackInvokedCallback"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "GestureBackNavigation",
        briefDescription = "Usage of KeyEvent.KEYCODE_BACK",
        explanation =
          """
                Starting in Android 13 (API 33+), the handling of back events is moving to \
                an ahead-of-time callback model. \
                Use `OnBackInvokedDispatcher.registerOnBackInvokedCallback(...)` and \
                `onBackInvokedCallback` or AndroidX's `OnBackPressedDispatcher` with an implemented \
                `onBackPressedCallback` to handle back gestures and key presses.
                """,
        category = Category.CORRECTNESS,
        priority = 7,
        severity = Severity.WARNING,
        implementation = Implementation(GestureBackNavDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
        moreInfo =
          "https://developer.android.com/about/versions/13/features/predictive-back-gesture",
      )
  }
}
