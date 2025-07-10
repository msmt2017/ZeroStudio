/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants.CLASS_INTENT
import com.android.tools.lint.client.api.TYPE_CLASS
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Finds locations in source code where an Intent is created:
 * - without specifying an action
 * - that is not explicit by component
 * - that is used in a call to sendBroadcast or startActivity
 *
 * Reports the call where the intent is instantiated.
 *
 * This scenario indicates that the developer needs to either set an action or make the intent
 * explicit by component
 *
 * For example:
 *
 * In some Kotlin code:
 * ```kotlin
 * val intent = Intent()
 * intent.setPackage("some.package")
 * // Fix: intent.setClassName(/* application ID */ packageName, /* class name */ "test.pkg.TestActivity")
 * startActivity(intent)
 * ```
 *
 * Note that we should NOT hardcode the application ID (application package name) in quick fixes.
 *
 * Partial results data is a list of Location
 */
class IntentWillNullActionDetector : Detector(), SourceCodeScanner, XmlScanner {

  override fun getApplicableConstructorTypes() = listOf(CLASS_INTENT)

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    // This is an Intent constructor. We will track the Intent to see if it
    // satisfies various conditions.

    // Skip constructors that use the action as a parameter (which is always the first parameter)
    // Also skip constructors that take a class (and thus create an intent for a
    // specific component).
    if (
      constructor.parameterList.parameters.firstOrNull()?.type?.canonicalText == TYPE_STRING ||
        constructor.parameterList.parameters.any { it.type.canonicalText == TYPE_CLASS }
    ) {
      return
    }

    // Skip the copy constructor.
    if (context.evaluator.parametersMatch(constructor, CLASS_INTENT)) {
      return
    }

    var isIntentComponentExplicitOrActionIsSet = false
    var isIntentUsedToLaunchComponent = false
    var escaped = false
    val visitor =
      object : DataFlowAnalyzer(setOf(node)) {

        override fun receiver(call: UCallExpression) {
          when (call.methodName) {
            "setComponent",
            "setClass",
            "setClassName",
            "setAction" -> isIntentComponentExplicitOrActionIsSet = true
          }
        }

        override fun argument(call: UCallExpression, reference: UElement) {
          when (call.methodName) {
            // TODO: We should track the intent being added to an array and then
            //  detect use of startActivities(...).
            "startActivity",
            "sendBroadcast",
            "sendBroadcastAsUser" -> {
              isIntentUsedToLaunchComponent = true
            }
            else -> {
              // The intent is used as an argument that we will not track
              // TODO: try to track deeply the intent object
              escaped = true
            }
          }
        }
      }

    val parent = node.getParentOfType(UMethod::class.java) ?: return
    parent.accept(visitor)
    if (!isIntentUsedToLaunchComponent || isIntentComponentExplicitOrActionIsSet || escaped) return
    val lintMap = context.getPartialResults(ISSUE).map()
    val location: Location = context.getLocation(node)
    lintMap.put("${lintMap.size}", location)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    for (intents in partialResults.maps()) {
      for (intentId in intents) {
        val location = intents.getLocation(intentId)
        val message =
          "This intent has no action set and is not explicit by component. " +
            "You should either make this intent explicit by component or set an action matching the targeted intent filter."
        context.report(Incident(ISSUE, location!!, message, buildQuickFix(location)))
      }
    }
  }

  private fun buildQuickFix(location: Location): LintFix {
    val setAction =
      LintFix.create()
        .name("Set action...")
        .replace()
        .reformat(true)
        .range(location)
        .end()
        .with(".setAction(\"your.custom.action\")")
        .select("your.custom.action")
        .build()
    val setClass =
      LintFix.create()
        .name("Set class...")
        .replace()
        .reformat(true)
        .range(location)
        .end()
        .with(".setClassName(\"app.package.name\", \"your.classname\")")
        .select("app.package.name")
        .build()
    return fix().alternatives(setAction, setClass)
  }

  override fun afterCheckRootProject(context: Context) {
    // A detector that uses partial results and LintMaps should just work in
    // global analysis mode, with one caveat; checkPartialResults is not called
    // in global analysis mode. Therefore, we call it here if we are in global
    // analysis mode.
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        IntentWillNullActionDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        Scope.JAVA_FILE_SCOPE,
      )

    /** Issue describing the problem and pointing to the detector implementation. */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "IntentWithNullActionLaunch",
        briefDescription = "Unsafe intent launched with no action set",
        explanation =
          """
                    Intents that have no action and do not specify a component are a potential security risk, \
                    and using them will result in a crash in an upcoming version of Android. \
                    If a specific app is being targeted (including the case where the current app is the target) \
                    then set the targeted component using `setComponent()`, `setClass()`, `setClassName()`, \
                    or the Intent constructors that take a Class parameter. \
                    If the intent is not intended for a specific app then the action name should be set.
                    """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }
}
