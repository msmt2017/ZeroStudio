/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.minSdkLessThan
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.tryResolveNamed

/** Detector for finding inefficiencies and errors in logging calls. */
class LogDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION = Implementation(LogDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Log call missing surrounding if. */
    @JvmField
    val CONDITIONAL =
      Issue.create(
          id = "LogConditional",
          briefDescription = "Unconditional Logging Calls",
          explanation =
            """
            The `BuildConfig` class provides a constant, `DEBUG`, which indicates \
            whether the code is being built in release mode or in debug mode. In release mode, you typically \
            want to strip out all the logging calls. Since the compiler will automatically remove all code \
            which is inside a `if (false)` check, surrounding your logging calls with a check for \
            `BuildConfig.DEBUG` is a good idea.

            If you **really** intend for the logging to be present in release mode, you can suppress this \
            warning with a `@SuppressLint` annotation for the intentional logging calls.""",
          category = Category.PERFORMANCE,
          priority = 5,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION,
        )
        .setEnabledByDefault(false)

    /** Mismatched tags between isLogging and log calls within it. */
    @JvmField
    val WRONG_TAG =
      Issue.create(
        id = "LogTagMismatch",
        briefDescription = "Mismatched Log Tags",
        explanation =
          """
            When guarding a `Log.v(tag, ...)` call with `Log.isLoggable(tag)`, the tag passed to both calls \
            should be the same. Similarly, the level passed in to `Log.isLoggable` should typically match \
            the type of `Log` call, e.g. if checking level `Log.DEBUG`, the corresponding `Log` call should \
            be `Log.d`, not `Log.i`.""",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Log tag is too long. */
    @JvmField
    val LONG_TAG =
      Issue.create(
        id = "LongLogTag",
        briefDescription = "Too Long Log Tags",
        explanation =
          """
            Log tags are only allowed to be at most 23 tag characters long.""",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val IS_LOGGABLE = "isLoggable"
    private const val PRINTLN = "println"
    const val LOG_CLS = "android.util.Log"
  }

  override fun getApplicableMethodNames(): List<String>? =
    listOf("d", "e", "i", "v", "w", PRINTLN, IS_LOGGABLE)

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (!evaluator.isMemberInClass(method, LOG_CLS)) {
      return
    }

    val name = method.name
    val withinConditional =
      IS_LOGGABLE == name || checkWithinConditional(context, node.uastParent, node)

    // See if it's surrounded by an if statement (and it's one of the non-error, spammy
    // log methods (info, verbose, etc))
    if (
      ("i" == name || "d" == name || "v" == name || PRINTLN == name) &&
        !withinConditional &&
        performsWork(node) &&
        context.isEnabled(CONDITIONAL)
    ) {
      val message =
        String.format(
          "The log call Log.%1\$s(...) should be " +
            "conditional: surround with `if (Log.isLoggable(...))` or " +
            "`if (BuildConfig.DEBUG) { ... }`",
          node.methodName,
        )
      val location = context.getLocation(node)
      context.report(CONDITIONAL, node, location, message)
    }

    // Check tag length
    if (context.isEnabled(LONG_TAG)) {
      val tagArgumentIndex = if (PRINTLN == name) 1 else 0
      val parameterList = method.parameterList
      val argumentList = node.valueArguments
      if (
        evaluator.parameterHasType(method, tagArgumentIndex, TYPE_STRING) &&
          parameterList.parametersCount == argumentList.size
      ) {
        val argument = argumentList[tagArgumentIndex]
        val tag = ConstantEvaluator.evaluateString(context, argument, true)
        if (tag != null && tag.length > 23 && context.project.minSdk <= 23) {
          val message = "The logging tag can be at most 23 characters, was ${tag.length} ($tag)"
          val incident = Incident(LONG_TAG, message, context.getLocation(argument), node)
          context.report(incident, minSdkLessThan(23))
        }
      }
    }
  }

  private fun getTagForMethod(method: String): String? =
    when (method) {
      "d" -> "DEBUG"
      "e" -> "ERROR"
      "i" -> "INFO"
      "v" -> "VERBOSE"
      "w" -> "WARN"
      else -> null
    }

  /** Returns true if the given logging call performs "work" to compute the message. */
  private fun performsWork(node: UCallExpression): Boolean {
    val referenceName = node.methodName ?: return false
    val messageArgumentIndex = if (PRINTLN == referenceName) 2 else 1
    val arguments = node.valueArguments
    if (arguments.size > messageArgumentIndex) {
      val argument = arguments[messageArgumentIndex]
      return performsWork(argument)
    }

    return false
  }

  private fun performsWork(argument: UExpression): Boolean {
    if (argument is ULiteralExpression) {
      return false
    }
    if (argument is UPolyadicExpression) {
      val string = argument.evaluateString()

      if (string != null) { // does it resolve to a constant?
        return false
      }
    } else if (argument is UBinaryExpression) {
      // Not currently a polyadic expr in UAST: repeat check done for polyadic
      val string = argument.evaluateString()

      if (string != null) { // does it resolve to a constant?
        return false
      }
    } else if (argument is USimpleNameReferenceExpression) {
      // Just a simple local variable/field reference
      return false
    } else if (argument is UParenthesizedExpression) {
      return performsWork(argument.expression)
    } else if (argument is UQualifiedReferenceExpression) {
      val string = argument.evaluateString()

      if (string != null) {
        return false
      }
      val resolved = argument.resolve()
      if (resolved is PsiVariable) {
        // Just a reference to a property/field, parameter or variable
        return false
      }
    }

    // Method invocations etc
    return true
  }

  private fun checkWithinConditional(
    context: JavaContext,
    start: UElement?,
    logCall: UCallExpression,
  ): Boolean {
    var curr = start
    while (curr != null) {
      if (curr is UIfExpression) {
        return checkLoggingCondition(curr.condition, context, logCall)
      } else if (curr is USwitchClauseExpression) {
        var within = false
        for (case in curr.caseValues) {
          within = checkLoggingCondition(case, context, logCall) || within
        }
        return within
      } else if (
        curr is UCallExpression ||
          curr is UMethod ||
          curr is UClassInitializer ||
          curr is UField ||
          curr is UClass
      ) { // static block
        break
      }
      curr = curr.uastParent
    }
    return false
  }

  private fun checkLoggingCondition(
    expression: UExpression,
    context: JavaContext,
    logCall: UCallExpression,
  ): Boolean {
    val condition = expression.findSelector()
    if (condition is UCallExpression) {
      if (IS_LOGGABLE == condition.methodName) {
        checkTagConsistent(context, logCall, condition)
      }
    } else if (condition is ULiteralExpression) {
      val value = condition.value
      if (value == true) {
        return false
      }
    }

    return true
  }

  /** Checks that the tag passed to Log.s and Log.isLoggable match. */
  private fun checkTagConsistent(
    context: JavaContext,
    logCall: UCallExpression,
    isLoggableCall: UCallExpression,
  ) {
    val isLoggableArguments = isLoggableCall.valueArguments
    val logArguments = logCall.valueArguments
    if (isLoggableArguments.isEmpty() || logArguments.isEmpty()) {
      return
    }
    val isLoggableTag = isLoggableArguments[0]
    var logTag: UExpression? = logArguments[0]

    val logCallName = logCall.methodName ?: return
    val isPrintln = PRINTLN == logCallName
    if (isPrintln && logArguments.size > 1) {
      logTag = logArguments[1]
    }

    if (logTag != null) {
      if (
        !areLiteralsEqual(isLoggableTag, logTag) &&
          !UastLintUtils.areIdentifiersEqual(isLoggableTag, logTag)
      ) {
        val resolved1 = isLoggableTag.tryResolveNamed()
        val resolved2 = logTag.tryResolveNamed()
        if (
          (resolved1 == null || resolved2 == null || resolved1 != resolved2) &&
            context.isEnabled(WRONG_TAG)
        ) {
          val location = context.getLocation(logTag)
          val alternate = context.getLocation(isLoggableTag)
          alternate.message = "Conflicting tag"
          location.secondary = alternate
          val isLoggableDescription =
            if (resolved1 != null) resolved1.name else isLoggableTag.asRenderString()
          val logCallDescription =
            if (resolved2 != null) resolved2.name else logTag.asRenderString()
          val message =
            String.format(
              "Mismatched tags: the `%1\$s()` and `isLoggable()` calls typically should pass the same tag: `%2\$s` versus `%3\$s`",
              logCallName,
              isLoggableDescription,
              logCallDescription,
            )
          context.report(WRONG_TAG, isLoggableCall, location, message)
        }
      }
    }

    // Check log level versus the actual log call type (e.g. flag
    //    if (Log.isLoggable(TAG, Log.DEBUG) Log.info(TAG, "something")

    if (logCallName.length != 1 || isLoggableArguments.size < 2) { // e.g. println
      return
    }
    val isLoggableLevel = isLoggableArguments[1]
    val resolved = isLoggableLevel.tryResolveNamed() ?: return
    if (resolved is PsiVariable) {
      val containingClass = PsiTreeUtil.getParentOfType(resolved, PsiClass::class.java)
      if (
        containingClass == null ||
          "android.util.Log" != containingClass.qualifiedName ||
          resolved.getName() == null ||
          resolved.getName() == getTagForMethod(logCallName)
      ) {
        return
      }

      val expectedCall = resolved.getName()!!.substring(0, 1).lowercase(Locale.getDefault())

      val message =
        String.format(
          "Mismatched logging levels: when checking `isLoggable` level `%1\$s`, the " +
            "corresponding log call should be `Log.%2\$s`, not `Log.%3\$s`",
          resolved.getName(),
          expectedCall,
          logCallName,
        )
      val location = context.getCallLocation(logCall, false, false)
      val alternate = context.getLocation(isLoggableLevel)
      alternate.message = "Conflicting tag"
      location.secondary = alternate
      context.report(WRONG_TAG, isLoggableCall, location, message)
    }
  }

  private fun getLastInQualifiedChain(node: UQualifiedReferenceExpression): UExpression {
    var last = node.selector
    while (last is UQualifiedReferenceExpression) {
      last = last.selector
    }
    return last
  }

  private fun areLiteralsEqual(first: UExpression, second: UExpression): Boolean {
    if (first !is ULiteralExpression) {
      return false
    }

    if (second !is ULiteralExpression) {
      return false
    }

    val firstValue = first.value
    val secondValue = second.value

    if (firstValue == null) {
      return secondValue == null
    }

    return firstValue == secondValue
  }
}
