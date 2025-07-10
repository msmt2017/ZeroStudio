/*
 * Copyright (C) 2012 The Android Open Source Project
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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown

/** Detector looking for Toast.makeText() without a corresponding show() call. */
class ToastDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> {
    return listOf("makeText", "make")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingClass = method.containingClass ?: return
    val className = containingClass.qualifiedName
    val name = method.name
    if (className == "android.widget.Toast" && name == "makeText") {
      // Make sure you pass the right kind of duration: it's not a delay, it's
      //  LENGTH_SHORT or LENGTH_LONG
      // (see http://code.google.com/p/android/issues/detail?id=3655)
      // (Note that this *is* allowed for Snackbars)
      val args = node.valueArguments
      if (args.size == 3) {
        val duration = args[2].skipParenthesizedExprDown()
        if (duration is ULiteralExpression) {
          context.report(
            ISSUE,
            duration,
            context.getLocation(duration),
            "Expected duration `Toast.LENGTH_SHORT` or `Toast.LENGTH_LONG`, a custom " +
              "duration value is not supported",
          )
        }
      }

      checkShown(context, node, "Toast")
    } else if (
      name == "make" &&
        (className == "com.google.android.material.snackbar.Snackbar" ||
          className == "android.support.design.widget.Snackbar")
    ) {
      checkShown(context, node, "Snackbar")
    }
  }

  private fun checkShown(context: JavaContext, node: UCallExpression, toastName: String) {
    val method = node.getParentOfType(UMethod::class.java) ?: return
    if (method.isMissingTarget(TargetMethodDataFlowAnalyzer.create(node, "show", null))) {
      val fix =
        if (CheckResultDetector.isExpressionValueUnused(node)) {
          fix()
            .replace()
            .name("Call show()")
            .range(context.getLocation(node))
            .end()
            .with(".show()")
            .build()
        } else {
          null
        }

      context.report(
        ISSUE,
        node,
        context.getCallLocation(node, includeReceiver = true, includeArguments = false),
        "$toastName created but not shown: did you forget to call `show()`?",
        fix,
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ShowToast",
        briefDescription = "Toast created but not shown",
        explanation =
          """
                    `Toast.makeText()` creates a `Toast` but does **not** show it. You must \
                    call `show()` on the resulting object to actually make the `Toast` \
                    appear.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(ToastDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }
}
