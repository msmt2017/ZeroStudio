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

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
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
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Makes sure that methods do not call super when overriding methods annotated with `@EmptySuper`.
 */
class EmptySuperDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(EmptySuperDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Missing call to super. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "EmptySuperCall",
        briefDescription = "Calling an empty super method",
        explanation =
          """
                For methods annotated with `@EmptySuper`, overriding methods should not also call the super implementation, either \
                because it is empty, or perhaps it contains code not intended to be run when the method is overridden.
                """,
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    const val EMPTY_SUPER_ANNOTATION = "androidx.annotation.EmptySuper"

    /**
     * Checks whether the given method overrides a method annotated with `@EmptySuper`, and if so,
     * returns it (otherwise returns null)
     */
    private fun getEmptySuperMethods(evaluator: JavaEvaluator, method: UMethod): List<PsiMethod> =
      method.javaPsi.findSuperMethods().filter { directSuper ->
        evaluator.getAnnotations(directSuper, false).any {
          it.qualifiedName == EMPTY_SUPER_ANNOTATION
        }
      }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val evaluator = context.evaluator
        val superMethods = getEmptySuperMethods(evaluator, node)
        if (superMethods.isEmpty()) return
        node.accept(
          object : AbstractUastVisitor() {
            override fun visitCallExpression(node: UCallExpression): Boolean {
              if (node.receiver is USuperExpression) {
                val resolved = node.resolve()
                val superMethod = superMethods.find { it.isEquivalentTo(resolved) }
                if (superMethod != null) {
                  val message =
                    "No need to call `super.${superMethod.name}`; the super method is defined to be empty"
                  val location = context.getNameLocation(node)
                  context.report(ISSUE, node, location, message)
                }
              }
              return super.visitCallExpression(node)
            }
          }
        )
      }
    }
}
