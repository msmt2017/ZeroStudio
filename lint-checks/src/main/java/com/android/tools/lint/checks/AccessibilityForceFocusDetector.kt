/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.SdkConstants.CLASS_BUNDLE
import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports calls to
 * `View.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, ...)`.
 */
class AccessibilityForceFocusDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = listOf("performAccessibilityAction")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {

    fun isViewMethod(methodName: String, vararg argumentTypes: String): Boolean =
      method.name == methodName &&
        context.evaluator.methodMatches(method, CLASS_VIEW, allowInherit = true, *argumentTypes)

    when {
      isViewMethod("performAccessibilityAction", TYPE_INT, CLASS_BUNDLE) ->
        checkPerformAccessibilityAction(context, node)
    }
  }

  private fun checkPerformAccessibilityAction(context: JavaContext, node: UCallExpression) {
    val firstArg = node.getArgumentForParameter(0) ?: return

    // For:
    //
    // view.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, ...)
    //
    // I.e. ACTION_ACCESSIBILITY_FOCUS is a constant int field, so we can evaluate it.
    fun UExpression.evaluatesToFocusValue() = this.evaluate() == ACTION_ACCESSIBILITY_FOCUS

    // For:
    //
    // view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_ACCESSIBILITY_FOCUS.getId(), ...);
    //
    // I.e. we cannot evaluate the arg, so just look for a reference to the
    // ACTION_ACCESSIBILITY_FOCUS field.
    fun UExpression.containsRefToFocus(): Boolean {
      var referencesFocus = false
      this.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            if (node.identifier == "ACTION_ACCESSIBILITY_FOCUS") {
              referencesFocus = true
              // Stop visiting.
              return true
            }
            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
      return referencesFocus
    }

    if (firstArg.evaluatesToFocusValue() || firstArg.containsRefToFocus()) {
      context.report(
        issue = ISSUE,
        scope = node,
        location = context.getCallLocation(node, includeReceiver = true, includeArguments = true),
        message =
          "Do not force accessibility focus, as this interferes with screen readers and gives an " +
            "inconsistent user experience, especially across apps",
      )
    }
  }

  companion object {
    private const val ACTION_ACCESSIBILITY_FOCUS = 1 shl 6

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityFocus",
        briefDescription = "Forcing accessibility focus",
        explanation =
          """
          Forcing accessibility focus interferes with screen readers and gives an \
          inconsistent user experience, especially across apps.
          """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(AccessibilityForceFocusDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }
}
