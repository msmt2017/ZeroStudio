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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.singleVariableAccessCall
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaValueParameterSymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression

/** Looks for trim calls with a redundant lambda */
class TrimDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION = Implementation(TrimDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Redundant lambda in trim calls. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "TrimLambda",
        briefDescription = "Unnecessary lambda with `trim()`",
        explanation =
          """
          The Kotlin standard library `trim()` call takes an optional lambda \
          to specify which characters are considered whitespace.

          When converting Java code to Kotlin code, the converter will convert \
          calls for Java's `s.trim()` into `s.trim() { it <= ' ' }`. This \
          preserves the exact semantics of the Java code, but is likely not \
          what you want: the default in Kotlin uses a better definition of what \
          constitutes a whitespace character (`Char::isWhitespace`) and also \
          results in less bytecode at the call-site.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.INFORMATIONAL,
        implementation = IMPLEMENTATION,
      )

    private const val STRING_TRIM_OWNER = "kotlin.text.StringsKt__StringsKt"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("trim")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (
      node.valueArgumentCount == 1 && method.containingClass?.qualifiedName == STRING_TRIM_OWNER
    ) {
      val argument = node.valueArguments[0] as? ULambdaExpression ?: return
      val lambda = argument.sourcePsi as? KtLambdaExpression ?: return
      if (!isDefaultLambda(lambda)) {
        return
      }

      val (fix, message) = createRemovalFix(lambda, context, node)
      context.report(ISSUE, node, context.getLocation(lambda), message, fix)
    }
  }

  private fun createRemovalFix(
    lambda: KtLambdaExpression,
    context: JavaContext,
    node: UCallExpression,
  ): Pair<LintFix, String> {
    val replacement =
      if ((node.sourcePsi as? KtCallExpression)?.valueArgumentList?.rightParenthesis == null) {
        "()"
      } else {
        ""
      }
    val fix =
      fix()
        .name("Remove lambda")
        .replace()
        .pattern("(\\s*\\Q${lambda.text}\\E)")
        .with(replacement)
        .range(context.getLocation(node))
        .autoFix()
        .build()
    val message = "The lambda argument (`${lambda.text}`) is unnecessary"
    return Pair(fix, message)
  }

  /**
   * Returns true if this [lambda] is structurally the same as either `{ it <= ' ' }` or `{
   * it.isWhitespace() }`
   */
  private fun isDefaultLambda(lambda: KtLambdaExpression): Boolean {
    val body = lambda.bodyExpression ?: return false
    val statements = body.statements
    if (statements.size != 1) {
      return false
    }
    val expression = statements[0]
    val statement = expression.skipParenthesizedExprDown()
    if (statement is KtDotQualifiedExpression) {
      val selector = statement.selectorExpression

      if (
        selector is KtCallExpression &&
          selector.valueArguments.isEmpty() &&
          lambda.isLambdaParameterReference(
            statement.receiverExpression.skipParenthesizedExprDown()
          )
      ) {
        // Make sure you're calling isWhitespace on the character
        analyze(selector) {
          val symbol = selector.resolveToCall()?.singleFunctionCallOrNull()?.symbol
          if (
            symbol is KaNamedFunctionSymbol &&
              symbol.name.identifier == "isWhitespace" &&
              symbol.containingFile == null
          ) {
            return true
          }
        }
      }
      return false
    } else if (statement is KtBinaryExpression) {
      val binary = statement
      if (binary.operationToken != KtTokens.LTEQ) {
        return false
      }
      val left = binary.left
      val right = binary.right
      if (right !is KtConstantExpression || right.text != "' '") {
        return false
      }
      if (left !is KtNameReferenceExpression) {
        return false
      }

      if (lambda.isLambdaParameterReference(left)) {
        return true
      }
    }

    return false
  }

  /**
   * Returns true if [element] references the implicit or explicit first parameter in this lambda.
   */
  private fun KtLambdaExpression.isLambdaParameterReference(element: KtElement): Boolean {
    analyze(element) {
      val symbol = element.resolveToCall()?.singleVariableAccessCall()?.symbol
      if (symbol is KaValueParameterSymbol) {
        if (symbol.isImplicitLambdaParameter) {
          return true
        }

        val psi = symbol.psi
        @Suppress("LintImplPsiEquals")
        if (psi == valueParameters.firstOrNull()) {
          return true
        }
      }
    }

    return false
  }
}
