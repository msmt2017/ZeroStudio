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
import com.android.tools.lint.detector.api.ConstantEvaluator
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

/**
 * Detector looking for usages of Resources.getIdentifier(insetName, "dimen", "android") for
 * platform-internal insetName resources.
 */
class InternalInsetResourceDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> {
    return listOf("getIdentifier")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.content.res.Resources")) return
    if (method.name != "getIdentifier") return

    val args = node.valueArguments
    if (args.size != 3) return

    fun getStringArgumentValue(argument: UExpression): String? =
      ConstantEvaluator.evaluateString(context, argument, false)

    val nameArg = getStringArgumentValue(args[0])
    val defTypeArg = getStringArgumentValue(args[1])
    val defPackageArg = getStringArgumentValue(args[2])

    when (nameArg) {
      "status_bar_height",
      "status_bar_height_portrait",
      "status_bar_height_landscape",
      "navigation_bar_height",
      "navigation_bar_height_landscape",
      "navigation_bar_width" -> Unit
      else -> return
    }

    if (defTypeArg == "dimen" && defPackageArg == "android") {
      context.report(
        ISSUE,
        node,
        context.getLocation(node),
        "Using internal inset dimension resource `$nameArg` is not supported",
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "InternalInsetResource",
        briefDescription = "Using internal inset dimension resource",
        explanation =
          """
                    The internal inset dimension resources are not a supported way to \
                    retrieve the relevant insets for your application. The insets are \
                    dynamic values that can change while your app is visible, and your \
                    app's window may not intersect with the system UI. \
                    To get the relevant value for your app and listen to updates, use \
                    `androidx.core.view.WindowInsetsCompat` and related APIs.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(InternalInsetResourceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )
  }
}
