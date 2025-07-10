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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.tools.lint.detector.api.targetSdkLessThan
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

class TileServiceActivityDetector : Detector(), SourceCodeScanner {

  companion object Issues {

    val IMPLEMENTATION =
      Implementation(
        TileServiceActivityDetector::class.java,
        EnumSet.of(Scope.BINARY_RESOURCE_FILE, Scope.MANIFEST),
      )

    private const val UPSIDE_DOWN_CAKE_API_VERSION: Int = 34

    @JvmField
    val START_ACTIVITY_AND_COLLAPSE_DEPRECATED =
      Issue.create(
        id = "StartActivityAndCollapseDeprecated",
        briefDescription = "TileService.startActivityAndCollapse(Intent) is deprecated",
        explanation =
          """
                `TileService#startActivityAndCollapse(Intent)` has been deprecated, and will throw \
                an `UnsupportedOperationException` if used in apps targeting Android versions UpsideDownCake and higher. \
                Convert the Intent to a PendingIntent.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(TileServiceActivityDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableMethodNames() = listOf("startActivityAndCollapse")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (
      !context.evaluator.isMemberInSubClassOf(method, "android.service.quicksettings.TileService")
    ) {
      return
    }
    val argument = node.valueArguments.firstOrNull() ?: return
    if (argument.getExpressionType()?.canonicalText != "android.content.Intent") return

    val location = context.getLocation(argument)
    val message =
      "TileService#startActivityAndCollapse(Intent) is deprecated. Use TileService#startActivityAndCollapse(PendingIntent) instead."
    context.report(
      Incident(START_ACTIVITY_AND_COLLAPSE_DEPRECATED, node, location, message)
        .overrideSeverity(Severity.WARNING),
      targetSdkLessThan(UPSIDE_DOWN_CAKE_API_VERSION),
    )
    context.report(
      Incident(START_ACTIVITY_AND_COLLAPSE_DEPRECATED, node, location, message),
      targetSdkAtLeast(UPSIDE_DOWN_CAKE_API_VERSION),
    )
  }
}
