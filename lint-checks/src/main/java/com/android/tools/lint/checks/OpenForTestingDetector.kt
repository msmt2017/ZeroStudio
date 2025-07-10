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

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.EXTENDS
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_OVERRIDE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement

/** Makes sure that APIs annotated `OpenForTesting` are only overridden or subclassed from tests. */
class OpenForTestingDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(OpenForTestingDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Overriding open-for-testing APIs */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "OpenForTesting",
        briefDescription = "Extending API only allowed from tests",
        explanation =
          """
                Classes or methods annotated with `@OpenForTesting` are only allowed to be subclassed or overridden from \
                unit tests.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    const val OPEN_FOR_TESTING_ANNOTATION = "androidx.annotation.OpenForTesting"
  }

  override fun applicableAnnotations(): List<String> = listOf(OPEN_FOR_TESTING_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == METHOD_OVERRIDE || type == EXTENDS

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (context.isTestSource) {
      return
    }

    val message =
      when (usageInfo.type) {
        METHOD_OVERRIDE -> {
          if (annotationInfo.origin != AnnotationOrigin.METHOD) {
            return
          }
          val superMethod = usageInfo.referenced as PsiMethod
          val containingClass = superMethod.containingClass
          "`${containingClass?.name}.${superMethod.name}` should only be overridden from tests"
        }
        else -> {
          if (annotationInfo.origin != AnnotationOrigin.CLASS) {
            return
          }
          val superClass = usageInfo.referenced as PsiClass
          "${superClass.name} should only be subclassed from tests"
        }
      }
    context.report(ISSUE, element, context.getLocation(element), message)
  }
}
