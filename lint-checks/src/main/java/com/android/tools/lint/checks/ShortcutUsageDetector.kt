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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

/* If an app does not call pushDynamicShortcut or reportShortcutUsed then report all calls to
 * setDynamicShortcuts and addDynamicShortcuts as an issue, as the calls to setDynamicShortcuts
 * indicate use of dynamic shortcuts without any calls to methods that track usage of the dynamic
 * shortcuts.
 */
class ShortcutUsageDetector : Detector(), SourceCodeScanner {

  /**
   * The number of calls to setDynamicShortcuts() in the current lint invocation. This is used as
   * the next key for storing the location of a call to setDynamicShortcuts in a LintMap. See
   * [visitMethodCall].
   */
  private var numSetOrAddDynamicShortcutsCalls = 0

  override fun getApplicableMethodNames(): List<String>? {
    return listOf(
      "addDynamicShortcuts",
      "setDynamicShortcuts",
      "pushDynamicShortcut",
      "reportShortcutUsed",
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (
      evaluator.isMemberInClass(method, SHORTCUT_MANAGER_CLASS) ||
        evaluator.isMemberInClass(method, SHORTCUT_MANAGER_COMPAT_CLASS)
    ) {
      val map = context.getPartialResults(ISSUE).map()
      when (method.name) {
        ADD_DYNAMIC_SHORTCUTS,
        SET_DYNAMIC_SHORTCUTS -> {
          if (!context.driver.isSuppressed(context, ISSUE, node)) {
            map.put(numSetOrAddDynamicShortcutsCalls++.toString(), context.getLocation(node))
          }
        }
        PUSH_DYNAMIC_SHORTCUT -> {
          map.put(HAS_PUSH_DYNAMIC_SHORTCUT, true)
        }
        REPORT_SHORTCUT_USED -> {
          map.put(HAS_REPORT_SHORTCUT_USED, true)
        }
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.mainProject.isLibrary) return

    // Return early if these calls are present as they report shortcut usage.
    val hasReportShortcutUsage: (LintMap) -> Boolean = {
      it.containsKey(HAS_PUSH_DYNAMIC_SHORTCUT) || it.containsKey(HAS_REPORT_SHORTCUT_USED)
    }
    if (partialResults.maps().any(hasReportShortcutUsage)) {
      return
    }
    // Otherwise, the values are locations to setDynamicShortcuts calls.
    for (perModuleLintMap in partialResults.maps()) {
      for (key in perModuleLintMap) {
        val url =
          "https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts#track-usage"
        context.report(
          Incident(context)
            .issue(ISSUE)
            .location(perModuleLintMap.getLocation(key)!!)
            .message(
              "Calling this method indicates use of dynamic shortcuts, but " +
                "there are no calls to methods that track shortcut usage, such " +
                "as `pushDynamicShortcut` or `reportShortcutUsed`. Calling these " +
                "methods is recommended, as they track shortcut usage and allow " +
                "launchers to adjust which shortcuts appear based on activation " +
                "history. Please see $url"
            )
            .fix(fix().url(url).build())
        )
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {
    const val SHORTCUT_MANAGER_CLASS = "android.content.pm.ShortcutManager"

    const val SHORTCUT_MANAGER_COMPAT_CLASS = "androidx.core.content.pm.ShortcutManagerCompat"

    const val SET_DYNAMIC_SHORTCUTS = "setDynamicShortcuts"
    const val ADD_DYNAMIC_SHORTCUTS = "addDynamicShortcuts"
    const val PUSH_DYNAMIC_SHORTCUT = "pushDynamicShortcut"
    const val REPORT_SHORTCUT_USED = "reportShortcutUsed"

    const val HAS_PUSH_DYNAMIC_SHORTCUT = "hasPushDynamicShortcut"
    const val HAS_REPORT_SHORTCUT_USED = "hasReportShortcutUsed"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ReportShortcutUsage",
        briefDescription = "Report shortcut usage",
        explanation =
          """
                Reporting shortcut usage is important to improving the ranking of shortcuts
                """,
        category = Category.USABILITY,
        priority = 2,
        severity = Severity.INFORMATIONAL,
        implementation =
          Implementation(ShortcutUsageDetector::class.java, EnumSet.of(Scope.ALL_JAVA_FILES)),
        androidSpecific = true,
        moreInfo =
          "https://developer.android.com/develop/ui/views/launch/shortcuts/managing-shortcuts",
      )
  }
}
