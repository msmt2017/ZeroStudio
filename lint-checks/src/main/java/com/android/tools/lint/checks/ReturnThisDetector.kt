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
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.DEFINITION
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_OVERRIDE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Makes sure that you return "this" from methods annotated `@ReturnThis`. */
class ReturnThisDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(ReturnThisDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Not returning this from annotated methods */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ReturnThis",
        briefDescription = "Method must return `this`",
        explanation =
          """
                Methods annotated with `@ReturnThis` (usually in the super method that this method is overriding) should \
                also `return this`.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    const val RETURN_THIS_ANNOTATION = "androidx.annotation.ReturnThis"
  }

  override fun applicableAnnotations(): List<String> = listOf(RETURN_THIS_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == METHOD_OVERRIDE || type == DEFINITION

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val method =
      if (usageInfo.type == DEFINITION) element.getParentOfType<UMethod>(true) ?: return
      else element as? UMethod ?: return
    method.accept(
      object : AbstractUastVisitor() {
        override fun visitReturnExpression(node: UReturnExpression): Boolean {
          val jumpTarget = node.jumpTarget
          if (jumpTarget != null && jumpTarget != method) {
            return super.visitReturnExpression(node)
          }

          val expression = node.returnExpression
          if (expression !is UThisExpression) {
            val message =
              "This method should `return this` (because it has been annotated with `@ReturnThis`)"
            context.report(ISSUE, node, context.getLocation(node), message)
          }
          return super.visitReturnExpression(node)
        }

        override fun visitClass(node: UClass): Boolean {
          return true
        }
      }
    )
  }
}
