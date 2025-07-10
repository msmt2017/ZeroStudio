/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isBelow
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown

/** Detector looking for suspicious combinations of intent.setData and intent.setType. */
class IntentDetector : Detector(), SourceCodeScanner {
  companion object {
    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "IntentReset",
        briefDescription = "Suspicious mix of `setType` and `setData`",
        explanation =
          """
                Intent provides the following APIs: `setData(Uri)` and `setType(String)`. \
                Unfortunately, setting one clears the other. If you want to set both, you \
                should call `setDataAndType(Uri, String)` instead.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(IntentDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private const val INTENT_CLASS = "android.content.Intent"
    private const val ANDROID_NET_URI = "android.net.Uri"
    private const val SET_DATA = "setData"
    private const val SET_TYPE = "setType"
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(INTENT_CLASS)
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    var seenInConstructor = false
    var seenData: UElement? = null
    var seenType: UElement? = null

    // Did we pass in a non-null Uri? If so record that
    for (topArgument in node.valueArguments) {
      val argument = topArgument.skipParenthesizedExprDown() ?: continue
      val type = argument.getExpressionType() ?: continue
      if (
        type.canonicalText == ANDROID_NET_URI &&
          !(argument is ULiteralExpression && argument.isNull)
      ) {
        seenInConstructor = true
        seenData = argument
        break
      }
    }

    val method = node.getParentOfType(UMethod::class.java) ?: return
    val analyzer =
      object : DataFlowAnalyzer(listOf(node)) {
        override fun receiver(call: UCallExpression) {
          val args = call.valueArguments
          if (args.size != 1) {
            return
          }
          val arg = args[0].skipParenthesizedExprDown()
          if (arg is ULiteralExpression && arg.isNull) {
            return
          }

          val name = call.methodName
          when (name) {
            SET_DATA -> seenData = call
            SET_TYPE -> seenType = call
            else -> return
          }

          seenData ?: return
          seenType ?: return

          val dataParent = findParent(seenData)
          val typeParent = findParent(seenType)
          //noinspection LintImplPsiEquals
          if (dataParent != typeParent) {
            return
          } else if (dataParent is UIfExpression) {
            // Make sure they're both inside the same then clause or same else clause
            val parent = dataParent.thenExpression
            if (parent != null && seenData!!.isBelow(parent) != seenType!!.isBelow(parent)) {
              return
            }
          }

          val prev = if (name == SET_DATA) seenType else seenData
          val prevDesc =
            if (seenInConstructor) {
              "setting URI in `Intent` constructor"
            } else {
              "calling `${(prev as? UCallExpression)?.methodName}`"
            }
          val data = if (name == SET_DATA) "type" else "data"
          val location =
            context
              .getCallLocation(call, includeReceiver = false, includeArguments = true)
              .withSecondary(context.getLocation(prev), "Originally set here")
          val message =
            "Calling `$name` after $prevDesc will clear the $data: Call `setDataAndType` instead?"
          context.report(ISSUE, call, location, message, null)
          seenData = null
          seenType = null
        }

        private fun findParent(call: UElement?): UElement? {
          call ?: return null
          return call.getParentOfType(
            strict = true,
            UMethod::class.java,
            UBlockExpression::class.java,
            UIfExpression::class.java,
            USwitchClauseExpression::class.java,
          )
        }
      }
    method.accept(analyzer)
  }
}
