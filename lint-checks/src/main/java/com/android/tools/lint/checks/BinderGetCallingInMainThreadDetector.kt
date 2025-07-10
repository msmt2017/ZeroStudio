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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUMethod

class BinderGetCallingInMainThreadDetector : Detector(), Detector.UastScanner {
  private data class Method(val className: String, val methodsNames: List<String>)

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "BinderGetCallingInMainThread",
        briefDescription = "Incorrect usage of getCallingUid() or getCallingPid()",
        explanation =
          """
                `Binder.getCallingUid()` and `Binder.getCallingPid()` will return information about the current process if called \
                inside a thread that is not handling a binder transaction. This can cause security issues. \
                If you still want to use your own uid/pid, use `Process.myUid()` or `Process.myPid()`.
                """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(BinderGetCallingInMainThreadDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private val GET_CALLING_METHODS =
      Method("android.os.Binder", listOf("getCallingUid", "getCallingPid"))
    private val DISALLOWED_METHODS_LIST: List<Method> =
      listOf(
        Method("android.app.Activity", listOf("onCreate", "onRestart", "onStart")),
        Method("android.app.Service", listOf("onCreate", "onBind", "onRebind")),
        Method(
          "android.app.Fragment",
          listOf("onAttach", "onCreate", "onCreateView", "onStart", "onViewCreated"),
        ),
        Method(
          "androidx.fragment.app.Fragment",
          listOf("onAttach", "onCreate", "onCreateView", "onStart", "onViewCreated"),
        ),
      )
  }

  override fun getApplicableMethodNames() = GET_CALLING_METHODS.methodsNames

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val containingMethod = node.getContainingUMethod() ?: return
    val containingClass = containingMethod.javaPsi.containingClass ?: return
    val invokedClass: PsiClass = method.containingClass ?: return
    if (context.evaluator.inheritsFrom(invokedClass, GET_CALLING_METHODS.className)) {
      for ((className, methodNames) in DISALLOWED_METHODS_LIST) {
        if (
          context.evaluator.inheritsFrom(containingClass, className, true) &&
            methodNames.contains(containingMethod.name)
        ) {
          val incident =
            Incident(
              ISSUE,
              node,
              context.getLocation(node),
              """Binder.${method.name}() should not be used inside ${containingMethod.name}()""",
            )
          context.report(incident)
          return
        }
      }
    }
  }
}
