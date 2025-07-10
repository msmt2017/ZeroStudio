/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/** Some lint checks around SharedPreferences. */
class SharedPrefsDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(SharedPrefsDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Modifying a string set. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MutatingSharedPrefs",
        briefDescription = "Mutating an Immutable SharedPrefs Set",
        explanation =
          """
                As stated in the docs for `SharedPreferences.getStringSet`, you must \
                not modify the set returned by `getStringSet`:

                  "Note that you <em>must not</em> modify the set instance returned \
                   by this call.  The consistency of the stored data is not guaranteed \
                   if you do, nor is your ability to modify the instance at all."
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getStringSet")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.SharedPreferences")) {
      return
    }

    val surrounding = node.getParentOfType<UMethod>(UMethod::class.java, true) ?: return
    surrounding.accept(
      object : DataFlowAnalyzer(listOf(node), emptySet()) {
        override fun receiver(call: UCallExpression) {
          val methodName = getMethodName(call) ?: return
          if (
            methodName.startsWith("add") ||
              methodName.startsWith("remove") ||
              methodName == "retainAll" ||
              methodName == "clear"
          ) {
            context.report(
              ISSUE,
              call,
              context.getLocation(call),
              "Do not modify the set returned by `SharedPreferences.getStringSet()``",
            )
          }
        }
      }
    )
  }
}
