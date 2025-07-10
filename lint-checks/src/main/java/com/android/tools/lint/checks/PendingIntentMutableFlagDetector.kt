/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression

class PendingIntentMutableFlagDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames() = PendingIntentUtils.GET_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, "android.app.PendingIntent")) return
    val flagsArgument =
      node.getArgumentForParameter(PendingIntentUtils.GET_ARGUMENT_POSITION_FLAG) ?: return
    val flags = ConstantEvaluator.evaluate(context, flagsArgument) as? Int ?: return
    if (flags and FLAG_MASK == 0) {
      val fix =
        fix()
          .alternatives(
            buildMutabilityFlagFix(context, flagsArgument),
            buildMutabilityFlagFix(context, flagsArgument, mutable = true),
          )
      val incident =
        Incident(
          issue = ISSUE,
          scope = node,
          location = context.getLocation(flagsArgument),
          message = "Missing `PendingIntent` mutability flag",
          fix,
        )
      context.report(incident, map())
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk < 23) return false
    if (context.mainProject.targetSdk < 31) {
      incident.overrideSeverity(Severity.WARNING)
    }
    return true
  }

  companion object {
    private const val FLAG_MASK =
      PendingIntentUtils.FLAG_IMMUTABLE or
        PendingIntentUtils.FLAG_MUTABLE or
        PendingIntentUtils.FLAG_UPDATE_CURRENT

    @JvmField
    val ISSUE =
      Issue.create(
          id = "UnspecifiedImmutableFlag",
          briefDescription = "Missing `PendingIntent` mutability flag",
          explanation =
            """
                Apps targeting Android 12 and higher must specify either `FLAG_IMMUTABLE` or \
                `FLAG_MUTABLE` when constructing a `PendingIntent`.

                `FLAG_IMMUTABLE` is available since target SDK 23, and is almost always the best choice. \
                See https://developer.android.com/guide/components/intents-filters#CreateImmutablePendingIntents \
                for a list of common exceptions to this rule.
            """,
          category = Category.SECURITY,
          priority = 5,
          /**
           * The severity of this issue is reported conditionally. See the overridden
           * `filterIncident` method.
           * - targetSdk >= 31: FATAL
           * - 23 <= targetSdk < 31: WARNING
           * - targetSdk < 23: not reported
           */
          severity = Severity.FATAL,
          implementation =
            Implementation(PendingIntentMutableFlagDetector::class.java, Scope.JAVA_FILE_SCOPE),
          androidSpecific = true,
          moreInfo =
            "https://developer.android.com/about/versions/12/behavior-changes-12#pending-intent-mutability",
        )
        .addMoreInfo("https://goo.gle/UnspecifiedImmutableFlag")

    private fun buildMutabilityFlagFix(
      context: JavaContext,
      originalArg: UExpression,
      mutable: Boolean = false,
    ): LintFix {
      val addFlagText =
        if (mutable) PendingIntentUtils.FLAG_MUTABLE_STR else PendingIntentUtils.FLAG_IMMUTABLE_STR
      val name = if (mutable) "Add FLAG_MUTABLE" else "Add FLAG_IMMUTABLE (preferred)"
      val isKotlin = context.uastFile?.lang == KotlinLanguage.INSTANCE
      val originalArgString = originalArg.asSourceString()

      val fixText =
        if (originalArgString == "0") addFlagText
        else if (isKotlin) "$originalArgString or $addFlagText"
        else "$originalArgString | $addFlagText"

      return LintFix.create()
        .name(name)
        .replace()
        .reformat(true)
        .shortenNames()
        .range(context.getLocation(originalArg))
        .with(fixText)
        .build()
    }
  }
}
