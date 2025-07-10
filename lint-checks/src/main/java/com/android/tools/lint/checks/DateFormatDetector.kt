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

import com.android.tools.lint.client.api.TYPE_STRING
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
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.expressions.UInjectionHost

/** Checks for errors related to Date Formats. */
class DateFormatDetector : Detector(), SourceCodeScanner {
  // ---- implements SourceCodeScanner ----
  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(JAVA_SIMPLE_DATE_FORMAT_CLS, ICU_SIMPLE_DATE_FORMAT_CLS)
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    if (!specifiesLocale(constructor)) {
      val location = context.getLocation(node)
      val message =
        "To get local formatting use `getDateInstance()`, " +
          "`getDateTimeInstance()`, or `getTimeInstance()`, or use " +
          "`new SimpleDateFormat(String template, Locale locale)` with for " +
          "example `Locale.US` for ASCII dates."
      context.report(DATE_FORMAT, node, location, message)
    }

    if (context.isEnabled(WEEK_YEAR)) {
      checkDateFormat(context, node)
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("ofPattern")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (
      context.evaluator.isMemberInClass(method, CLS_DATE_TIME_FORMATTER) &&
        context.isEnabled(WEEK_YEAR)
    ) {
      checkDateFormat(context, node)
    }
  }

  private fun checkDateFormat(context: JavaContext, call: UCallExpression) {
    for (argument in call.valueArguments) {
      val type = argument.getExpressionType() ?: continue
      if (type.canonicalText == TYPE_STRING) {
        checkDateFormat(context, argument)
        break
      }
    }
  }

  private fun checkDateFormat(context: JavaContext, argument: UExpression) {
    val value =
      when (argument) {
        is ULiteralExpression -> argument.value
        is UInjectionHost ->
          argument.evaluateToString()
            ?: ConstantEvaluator().allowUnknowns().evaluate(argument)
            ?: return
        else -> ConstantEvaluator().allowUnknowns().evaluate(argument) ?: return
      }
    val format = value.toString()
    if (!format.contains("Y")) {
      return
    }
    var escaped = false
    var weekYearStart = -1
    var haveDate = false
    var haveEraYear = false
    for ((index, c) in format.withIndex()) {
      if (c == '\'') {
        escaped = !escaped
      } else if (c == 'M' || c == 'L' || c == 'd') {
        haveDate = true
      } else if (c == 'y' || c == 'u') {
        haveEraYear = true
      } else if (c == 'Y' && !escaped && weekYearStart == -1) {
        weekYearStart = index
      }
    }
    if (weekYearStart != -1 && haveDate && !haveEraYear) {
      // The date string is referencing week-based years while also referencing
      // months and/or days. That's probably a bug -- when you see week-based years
      // that's typically combined with other week based quantities, like week-of-month.
      val index = weekYearStart
      var location = context.getLocation(argument)
      var end = index + 1
      while (end < format.length && format[end] == 'Y') {
        end++
      }
      val digits = format.substring(index, end)
      if (argument is ULiteralExpression) {
        location = context.getRangeLocation(argument, index, end - index)
      } else if (
        argument is UInjectionHost &&
          argument is UPolyadicExpression &&
          argument.operator == UastBinaryOperator.PLUS &&
          argument.operands.size == 1 &&
          argument.operands.first() is ULiteralExpression
      ) {
        location = context.getRangeLocation(argument.operands[0], index, end - index)
      }

      context.report(
        WEEK_YEAR,
        argument,
        location,
        "`DateFormat` character 'Y' in $digits is the week-era-year; did you mean 'y'?",
      )
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(DateFormatDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Constructing SimpleDateFormat without an explicit locale. */
    @JvmField
    val DATE_FORMAT =
      Issue.create(
        id = "SimpleDateFormat",
        briefDescription = "Implied locale in date format",
        explanation =
          """
                    Almost all callers should use `getDateInstance()`, `getDateTimeInstance()`, \
                    or `getTimeInstance()` to get a ready-made instance of SimpleDateFormat \
                    suitable for the user's locale. The main reason you'd create an instance \
                    this class directly is because you need to format/parse a specific \
                    machine-readable format, in which case you almost certainly want to \
                    explicitly ask for US to ensure that you get ASCII digits (rather than, \
                    say, Arabic digits).

                    Therefore, you should either use the form of the SimpleDateFormat \
                    constructor where you pass in an explicit locale, such as Locale.US, or \
                    use one of the get instance methods, or suppress this error if really know \
                    what you are doing.
                    """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        moreInfo = "https://developer.android.com/reference/java/text/SimpleDateFormat.html",
        implementation = IMPLEMENTATION,
      )

    /** Accidentally(?) using week year instead of era year. */
    @JvmField
    val WEEK_YEAR =
      Issue.create(
        id = "WeekBasedYear",
        briefDescription = "Week Based Year",
        explanation =
          """
                The `DateTimeFormatter` pattern `YYYY` returns the *week* based year, not \
                the era-based year. This means that 12/29/2019 will format to 2019, but \
                12/30/2019 will format to 2020!

                If you expected this to format as 2019, you should use the pattern `yyyy` \
                instead.
                """,
        moreInfo =
          "https://stackoverflow.com/questions/46847245/using-datetimeformatter-on-january-first-cause-an-invalid-year-value",
        category = Category.I18N,
        priority = 6,
        severity = Severity.WARNING,
        enabledByDefault = true,
        implementation = IMPLEMENTATION,
      )

    const val LOCALE_CLS = "java.util.Locale"
    private const val CLS_DATE_TIME_FORMATTER = "java.time.format.DateTimeFormatter"
    private const val JAVA_SIMPLE_DATE_FORMAT_CLS = "java.text.SimpleDateFormat"
    private const val ICU_SIMPLE_DATE_FORMAT_CLS = "android.icu.text.SimpleDateFormat"

    private fun specifiesLocale(method: PsiMethod): Boolean {
      val parameterList = method.parameterList
      val parameters = parameterList.parameters
      for (parameter in parameters) {
        val type = parameter.type
        if (type.canonicalText == LOCALE_CLS) {
          return true
        }
      }
      return false
    }
  }
}
