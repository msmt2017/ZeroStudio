/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.successfulConstructorCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastCallKind

/** Looks for inefficient array constructions. */
class ArrayInitDetector : Detector(), SourceCodeScanner, XmlScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(ArrayInitDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Unnecessary array initialization. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UnnecessaryArrayInit",
        briefDescription = "Unnecessary array initialization",
        explanation =
          """
          When constructing an array in Kotlin, you don't need to pass \
          a lambda to set the initial value if it's identical to the \
          default or if you're going to overwrite all the values without \
          reading them anyway.
          """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.INFORMATIONAL,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? {
    // Can't use getApplicableConstructorTypes+visitConstructor here because
    // this doesn't work for built-in types like ByteArray, so we explicitly
    // visit calls instead.
    return listOf(UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        if (node.kind == UastCallKind.CONSTRUCTOR_CALL) {
          val sourcePsi = node.sourcePsi

          if (sourcePsi is KtCallExpression) {
            if (sourcePsi.calleeExpression?.text.isArrayWithOptionalInitializer()) {
              analyze(sourcePsi) {
                val symbol = sourcePsi.resolveToCall()?.successfulConstructorCallOrNull()?.symbol
                if (symbol != null) {
                  val classId = symbol.returnType.symbol?.classId
                  if (
                    classId != null &&
                      classId.packageFqName.asString() == "kotlin" &&
                      classId.relativeClassName.asString().isArrayWithOptionalInitializer()
                  ) {
                    checkConstructor(context, node)
                  }
                } else {
                  checkConstructor(context, node)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun checkConstructor(context: JavaContext, node: UCallExpression) {
    val lambda = node.valueArguments.lastOrNull() as? ULambdaExpression ?: return
    if (node.valueArguments.lastOrNull() !is ULambdaExpression) {
      return
    }

    val expression = lambda.body
    if (expression is UBlockExpression) {
      val expressions = expression.expressions
      if (expressions.size == 1) {
        val uExpression = expressions[0]
        if (uExpression is UReturnExpression) {
          val returnExpression = uExpression.returnExpression
          if (returnExpression != null) {
            val constant = returnExpression.evaluate() ?: return
            if (constant is Number && constant.toDouble() == 0.0) {
              val sourcePsi = lambda.sourcePsi
              val reference = if (sourcePsi != null) " (`${sourcePsi.text}`)" else ""
              val message =
                "This initialization lambda$reference is unnecessary and is less efficient"
              val fix =
                fix()
                  .name("Remove initialization")
                  .replace()
                  .apply {
                    if (sourcePsi != null)
                      pattern("\\s*\\Q${lambda.sourcePsi?.text}\\E")
                        .range(context.getLocation(node))
                    else all().reformat(true)
                  }
                  .with("")
                  .autoFix()
                  .build()
              context.report(ISSUE, lambda, context.getLocation(lambda), message, fix)
            }
          }
        }
      }
    }
  }

  /** Is this simple class name the name of an array class where the initializer is optional? */
  private fun String?.isArrayWithOptionalInitializer(): Boolean {
    return this != null && this.endsWith("Array") && this != "Array"
  }
}
