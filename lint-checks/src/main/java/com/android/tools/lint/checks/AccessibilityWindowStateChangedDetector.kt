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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType

/**
 * Reports calls to `View.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)`.
 *
 * Reports references to `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED` from within overrides of
 * `View.dispatchPopulateAccessibilityEvent` and `View.onPopulateAccessibilityEvent`.
 */
class AccessibilityWindowStateChangedDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = listOf("sendAccessibilityEvent")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {

    fun isViewMethod(methodName: String, vararg argumentTypes: String): Boolean =
      method.name == methodName &&
        context.evaluator.methodMatches(method, CLASS_VIEW, allowInherit = true, *argumentTypes)

    when {
      isViewMethod("sendAccessibilityEvent", TYPE_INT) -> checkSendAccessibilityEvent(context, node)
    }
  }

  override fun getApplicableReferenceNames() = listOf("TYPE_WINDOW_STATE_CHANGED")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    checkTypeWindowStateChangedWithinOverride(context, reference, referenced)
  }

  private fun checkSendAccessibilityEvent(context: JavaContext, node: UCallExpression) {
    val firstArg = node.getArgumentForParameter(0) ?: return
    val value = firstArg.evaluate() ?: return
    if (value != TYPE_WINDOW_STATE_CHANGED) return

    context.report(
      issue = ISSUE,
      scope = node,
      location = context.getCallLocation(node, includeReceiver = true, includeArguments = true),
      message = WINDOW_STATE_CHANGED_EVENT_MESSAGE,
    )
  }

  private fun checkTypeWindowStateChangedWithinOverride(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    // "referenced" must be AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED.
    val field = referenced as? PsiField ?: return
    if (field.name != "TYPE_WINDOW_STATE_CHANGED") return
    if (!context.evaluator.isMemberInClass(field, "android.view.accessibility.AccessibilityEvent"))
      return

    // "reference" must be within a method that overrides View.dispatchPopulateAccessibilityEvent or
    // View.onPopulateAccessibilityEvent.
    val parentMethod = reference.getParentOfType<UMethod>() ?: return

    if (
      parentMethod.name !in
        arrayOf("dispatchPopulateAccessibilityEvent", "onPopulateAccessibilityEvent")
    )
      return

    val containingClass = parentMethod.getContainingUClass() ?: return
    // Note: Do not warn about uses within the View class itself; hence, strict = true.
    if (!context.evaluator.extendsClass(containingClass.javaPsi, CLASS_VIEW, strict = true)) return
    if (!context.evaluator.isOverride(parentMethod, includeInterfaces = false)) return

    context.report(
      issue = ISSUE,
      scope = reference,
      location = context.getLocation(reference),
      message = WINDOW_STATE_CHANGED_EVENT_MESSAGE,
    )
  }

  companion object {
    private const val TYPE_WINDOW_STATE_CHANGED = 1 shl 5

    private const val WINDOW_STATE_CHANGED_EVENT_MESSAGE =
      "Manually populating or sending TYPE_WINDOW_STATE_CHANGED events should be avoided. " +
        "They may be ignored on certain versions of Android. " +
        "Prefer setting UI metadata using `View.onInitializeAccessibilityNodeInfo`, " +
        "`Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, etc. " +
        "to inform users of crucial changes to the UI."

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityWindowStateChangedEvent",
        briefDescription = "Use of accessibility window state change events",
        explanation =
          """
          Sending or populating `TYPE_WINDOW_STATE_CHANGED` events in your code \
          is strongly discouraged. \
          Instead, prefer to use or extend system-provided widgets that are as far down Android's \
          class hierarchy as possible. \
          System-provided widgets that are far down the hierarchy already have most of the \
          accessibility capabilities your app needs. \
          If you must extend `View` or `Canvas` directly, then still prefer to: set UI metadata by \
          calling `Activity.setTitle`, `ViewCompat.setAccessibilityPaneTitle`, or \
          `ViewCompat.setAccessibilityLiveRegion`; \
          implement `View.onInitializeAccessibilityNodeInfo`; \
          and (for very specialized custom controls) implement \
          `View.getAccessibilityNodeProvider` to provide a virtual view hierarchy. \
          These approaches allow accessibility services to inspect the view \
          hierarchy, rather than relying on incomplete information provided by events. \
          Events like `TYPE_WINDOW_STATE_CHANGED` will be sent automatically when updating \
          this metadata, and so trying to manually send this event will result in duplicate \
          events, or the event may be ignored entirely.
          """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            AccessibilityWindowStateChangedDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
        androidSpecific = true,
      )
  }
}
