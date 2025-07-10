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

/**
 * Makes sure you don't pass 0 to Service.startForeground, as hinted in the docs. Ideally we could
 * have just expressed this with an annotation constraint on the API itself, but we don't have a way
 * to prevent a single value, e.g. we can do
 * > 0 or < 0 but not both.
 */
class InvalidNotificationIdDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(InvalidNotificationIdDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Invalid */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "NotificationId0",
        briefDescription = "Notification Id is 0",
        explanation =
          """
                The notification id **cannot** be 0; using 0 here can make the service not run in \
                the foreground.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("startForeground")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInClass(method, "android.app.Service")) {
      return
    }
    val argument = node.valueArguments.firstOrNull() ?: return
    val id = ConstantEvaluator.evaluate(context, argument) as? Int ?: return
    if (id == 0) {
      val message = "The notification id **cannot** be 0"
      context.report(ISSUE, argument, context.getLocation(argument), message)
    }
  }
}
