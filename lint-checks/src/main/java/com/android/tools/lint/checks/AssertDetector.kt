/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.psi.PsiAssertStatement
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import kotlin.math.min
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySetterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolOrigin
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPostfixOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve

/** Looks for assertion usages. */
class AssertDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION = Implementation(AssertDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Whether assertions have a side effect. */
    @JvmField
    val SIDE_EFFECT =
      Issue.create(
        id = "AssertionSideEffect",
        briefDescription = "Assertions with Side Effects",
        explanation =
          """
                Assertion conditions can have side effects. This is risky because the behavior \
                depends on whether assertions are on or off. This is usually not intentional, \
                and can lead to bugs where the production version differs from the version tested \
                during development.

                Generally, you'll want to perform the operation with the side effect before the \
                assertion, and then assert that the result was what you expected.
                """,
        category = Category.PERFORMANCE,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** In Kotlin arguments to assertions are always evaluated. */
    @JvmField
    val EXPENSIVE =
      Issue.create(
        id = "ExpensiveAssertion",
        briefDescription = "Expensive Assertions",
        explanation =
          """
                In Kotlin, assertions are not handled the same way as from the Java programming \
                language. In particular, they're just implemented as a library call, and inside \
                the library call the error is only thrown if assertions are enabled.

                This means that the arguments to the `assert` call will **always** \
                be evaluated. If you're doing any computation in the expression being \
                asserted, that computation will unconditionally be performed whether or not \
                assertions are turned on. This typically turns into wasted work in release \
                builds.

                This check looks for cases where the assertion condition is nontrivial, e.g. \
                it is performing method calls or doing more work than simple comparisons \
                on local variables or fields.

                You can work around this by writing your own inline assert method instead:

                ```kotlin
                @Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
                inline fun assert(condition: () -> Boolean) {
                    if (_Assertions.ENABLED && !condition()) {
                        throw AssertionError()
                    }
                }
                ```

                In Android, because assertions are not enforced at runtime, instead use this:

                ```kotlin
                inline fun assert(condition: () -> Boolean) {
                    if (BuildConfig.DEBUG && !condition()) {
                        throw AssertionError()
                    }
                }
                ```
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        enabledByDefault = false,
      )

    /** Maximum number of indirect calls it will search */
    private val MAX_CALL_DEPTH = if (LintClient.isStudio) 1 else 2
    /** Maximum depth of the AST tree it will search */
    private val MAX_RECURSION_DEPTH = if (LintClient.isStudio) 5 else 10
    /**
     * When analyzing a block such as a method, the maximum number of statements it will consider
     */
    private val MAX_STATEMENT_COUNT = if (LintClient.isStudio) 8 else 20
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UCallExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        if (node.sourcePsi is PsiAssertStatement) {
          val condition = (node.sourcePsi as PsiAssertStatement).assertCondition ?: return
          UastFacade.convertElement(condition, node, UExpression::class.java)?.let {
            checkSideEffect(context, it as UExpression)
          }
        }
      }
    }
  }

  override fun getApplicableMethodNames(): List<String> =
    // Kotlin assertions -- regular method call
    listOf("assert")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    // Make sure it's Kotlin, and that the assert() call being called is the stdlib one
    if (!isKotlin(node.lang)) {
      return
    }

    val containingClass = method.containingClass?.qualifiedName ?: return
    if (
      containingClass != "kotlin.PreconditionsKt__AssertionsJVMKt" &&
        containingClass != "kotlin.PreconditionsKt"
    ) {
      return
    }

    val valueArguments = node.valueArguments
    val first = valueArguments.firstOrNull()?.skipParenthesizedExprDown() ?: return
    val condition =
      if (first is ULambdaExpression) {
        valueArguments.last()
      } else {
        first
      }

    checkKotlinAssertion(context, node, condition)
    checkSideEffect(context, condition)
  }

  private fun checkKotlinAssertion(
    context: JavaContext,
    assertion: UCallExpression,
    condition: UExpression,
  ) {
    if (context.isEnabled(EXPENSIVE) && warnAboutWork(assertion, condition)) {
      val location = context.getLocation(condition)
      var message = "Kotlin assertion arguments are always evaluated, even when assertions are off"
      val fix: LintFix?
      val cls = assertion.getParentOfType(UClass::class.java, true)
      if (cls?.sourcePsi != null) { // sourcePsi == null: top level functions
        fix = createKotlinAssertionStatusFix(context, assertion)
        message +=
          ". Consider surrounding assertion with `if (javaClass.desiredAssertionStatus()) { assert(...) }`"
      } else {
        fix = null
      }

      context.report(EXPENSIVE, assertion, location, message, fix)
    }
  }

  private fun checkSideEffect(context: JavaContext, condition: UExpression) {
    val sideEffect = getSideEffect(condition, 0, 0)
    if (sideEffect != null) {
      context.report(
        SIDE_EFFECT,
        condition,
        context.getLocation(sideEffect.first),
        "Assertion condition has a side effect: ${sideEffect.second}",
      )
    }
  }

  /** Looks for side effects, up to 2 calls deep and up to 5 statements in each method. */
  private fun getSideEffect(
    node: UExpression?,
    depth: Int,
    callDepth: Int,
  ): Pair<UExpression, String>? {
    node ?: return null
    if (depth == MAX_RECURSION_DEPTH) {
      return null
    }
    when (node) {
      is UUnaryExpression -> {
        val operator = node.operator
        if (
          operator == UastPrefixOperator.INC ||
            operator == UastPrefixOperator.DEC ||
            operator == UastPostfixOperator.INC ||
            operator == UastPostfixOperator.DEC
        ) {
          if ((callDepth > 0 && isLocal(node.operand))) {
            // If we're inside a called method and we're just manipulating local variables,
            // don't flag that as a side effect
            return null
          }
          return Pair(node, node.sourcePsi?.text ?: node.operator.text)
        }
      }
      is UPolyadicExpression -> {
        if (
          node is UBinaryExpression &&
            node.operator is UastBinaryOperator.AssignOperator &&
            (callDepth == 0 || !isLocal(node.leftOperand))
        ) {
          return Pair(node, node.sourcePsi?.text ?: node.operator.text)
        }
        for (operand in node.operands) {
          val sideEffect = getSideEffect(operand, depth + 1, callDepth)
          if (sideEffect != null) {
            return sideEffect
          }
        }
      }
      is UQualifiedReferenceExpression -> {
        return getSideEffect(node.selector, depth + 1, callDepth)
      }
      is UParenthesizedExpression -> {
        return getSideEffect(node.expression, depth + 1, callDepth)
      }
      is UCallExpression -> {
        if (callDepth > MAX_CALL_DEPTH) {
          return null
        }
        val sourcePsi = node.sourcePsi as? KtElement ?: return null
        analyze(sourcePsi) {
          val functionSymbol = getFunctionLikeSymbol(sourcePsi) ?: return null
          if (functionSymbol.origin != KaSymbolOrigin.SOURCE) {
            when (functionSymbol) {
              is KaPropertySetterSymbol -> {
                return Pair(node, sourcePsi.text)
              }
              is KaFunctionSymbol -> {
                val callableId = functionSymbol.callableId ?: return null
                if (
                  !callableId.packageName.startsWith(FqName("kotlin")) &&
                    mayHaveSideEffects(callableId.callableName.identifier)
                ) {
                  return Pair(node, sourcePsi.text)
                }
              }
              else -> return null
            }
          } else {
            val body = functionSymbol.psi.toUElementOfType<UMethod>()?.uastBody ?: return null
            val sideEffect = getSideEffect(body, depth + 1, callDepth + 1)
            if (sideEffect != null) {
              // Point to assertion call location, not the side effect inside the called method
              return Pair(node, sideEffect.second)
            }
          }
        }
      }
      is UReturnExpression -> {
        return getSideEffect(node.returnExpression, depth + 1, callDepth)
      }
      is UIfExpression -> {
        getSideEffect(node.condition, depth + 1, callDepth)?.let {
          return it
        }
        getSideEffect(node.thenExpression, depth + 1, callDepth)?.let {
          return it
        }
        getSideEffect(node.elseExpression, depth + 1, callDepth)?.let {
          return it
        }
      }
      is UTryExpression -> {
        getSideEffect(node.tryClause, depth + 1, callDepth)?.let {
          return it
        }
        getSideEffect(node.finallyClause, depth + 1, callDepth)?.let {
          return it
        }
      }
      is UBlockExpression -> {
        val expressions: List<UExpression> = node.expressions
        for (i in 0 until min(MAX_STATEMENT_COUNT, expressions.size)) {
          val sideEffect = getSideEffect(expressions[i], depth + 1, callDepth)
          if (sideEffect != null) {
            // Place error on the assertion call, not inside the called method
            return Pair(node, sideEffect.second)
          }
        }
      }
      is ULoopExpression -> {
        return getSideEffect(node.body, depth + 1, callDepth)
      }
    }

    return null
  }

  private fun mayHaveSideEffects(name: String): Boolean =
    name == "add" ||
      name == "remove" ||
      name == "put" ||
      name == "delete" ||
      name == "mkdir" ||
      name == "mkdirs" ||
      // generic setter
      name.startsWith("set") && name.length > 3 && name[3].isUpperCase()

  private fun isLocal(lhs: UExpression): Boolean {
    val resolved = lhs.tryResolve()
    return resolved is PsiLocalVariable || resolved is PsiParameter
  }

  private fun createKotlinAssertionStatusFix(
    context: JavaContext,
    assertCall: UCallExpression,
  ): LintFix {
    return fix()
      .name("Surround with desiredAssertionStatus() check")
      .replace()
      .range(context.getLocation(assertCall))
      .pattern("(.*)")
      .with("if (javaClass.desiredAssertionStatus()) { \\k<1> }")
      .reformat(true)
      .build()
  }

  /**
   * Returns true if the given assert call is performing computation in its condition without
   * explicitly checking for whether assertions are enabled.
   */
  private fun warnAboutWork(assertCall: UCallExpression, condition: UExpression): Boolean {
    return isExpensive(condition, 0) && !isWithinAssertionStatusCheck(assertCall)
  }

  /** Returns true if the given logging call performs "work" to compute the message. */
  private fun isExpensive(argument: UExpression, depth: Int): Boolean {
    if (depth == 4) {
      return true
    }
    if (argument is ULiteralExpression || argument is UInstanceExpression) {
      return false
    }
    if (argument is UBinaryExpressionWithType) {
      return if (argument.sourcePsi is KtIsExpression) {
        false
      } else {
        isExpensive(argument.operand, depth + 1)
      }
    }
    if (argument is UPolyadicExpression) {
      for (value in argument.operands) {
        if (isExpensive((value), depth + 1)) {
          return true
        }
      }
      return false
    } else if (argument is UParenthesizedExpression) {
      return isExpensive(
        argument.expression,
        depth,
      ) // not +1: cheap and want to allow parenthesis mode tests
    } else if (argument is UBinaryExpression) {
      return isExpensive(argument.leftOperand, depth + 1) ||
        isExpensive(argument.rightOperand, depth + 1)
    } else if (argument is UUnaryExpression) {
      return isExpensive(argument.operand, depth + 1)
    } else if (argument is USimpleNameReferenceExpression) {
      // Just a simple local variable/field reference
      return false
    } else if (argument is UQualifiedReferenceExpression) {
      return false
    } else if (argument is UCallExpression) {
      val method = argument.resolve()
      if (method != null && method !is PsiCompiledElement) {
        val body = skipParenthesizedExprUp(UastFacade.getMethodBody(method)) ?: return true
        if (body is UBlockExpression) {
          val expressions = body.expressions
          if (expressions.size == 1 && expressions[0] is UReturnExpression) {
            val retExp = (expressions[0] as UReturnExpression).returnExpression
            return retExp == null || isExpensive(retExp, depth + 1)
          }
        } else if (body is UExpression) {
          // Expression body
          return isExpensive(body, depth + 1)
        }
      } else {
        // Look for some well known cheap and common methods
        val name = argument.methodName
        if (name == "isEmpty" || name == "isNotEmpty" || name == "contains") {
          return false
        }
      }
    } else if (argument is UArrayAccessExpression) {
      for (value in argument.indices) {
        if (isExpensive((value), depth + 1)) {
          return true
        }
      }
      return false
    }

    // Method invocations etc
    return true
  }

  private fun isWithinAssertionStatusCheck(node: UExpression): Boolean {
    var curr = node.uastParent ?: return false
    while (true) {
      if (
        curr is UIfExpression && isAssertionStatusCheck(curr.condition) ||
          curr is USwitchClauseExpressionWithBody && curr.caseValues.all(::isAssertionStatusCheck)
      ) {
        return true
      }
      curr = curr.uastParent ?: return false
    }
  }

  private fun isAssertionStatusCheck(condition: UExpression): Boolean {
    // This is inefficient, but works around current resolve bug on javaClass access
    return condition.sourcePsi?.text?.contains("desiredAssertionStatus") == true
  }
}
