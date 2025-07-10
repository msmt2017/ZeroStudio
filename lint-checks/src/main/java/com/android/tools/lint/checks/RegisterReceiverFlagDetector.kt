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

import com.android.SdkConstants.CLASS_BROADCASTRECEIVER
import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT_FILTER
import com.android.sdklib.AndroidVersion.VersionCodes.UPSIDE_DOWN_CAKE
import com.android.tools.lint.checks.BroadcastReceiverUtils.BROADCAST_RECEIVER_METHOD_NAMES
import com.android.tools.lint.checks.BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_STRING
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
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findArgument
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.isNullLiteral

/**
 * Detector that identifies `registerReceiver()` calls which are missing the `RECEIVER_EXPORTED` or
 * `RECEIVER_NOT_EXPORTED` flags.
 */
class RegisterReceiverFlagDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = BROADCAST_RECEIVER_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, CLASS_CONTEXT)) return

    if (isReceiverExportedFlagPresent(node)) return

    // The parameter positions vary across the various registerReceiver*() methods, so rather
    // than hardcode them we simply look them up based on the parameter type.
    val receiverArg = findArgument(node, CLASS_BROADCASTRECEIVER) ?: return
    if (receiverArg.isNullLiteral()) return
    val filterArg = findArgument(node, CLASS_INTENT_FILTER) ?: return

    val (isProtected, unprotectedActionsList) =
      checkIsProtectedReceiverAndReturnUnprotectedActions(filterArg, node, context.evaluator)
    if (isProtected) return

    val actionsList = unprotectedActionsList.joinToString(", ", "", "", -1, "")
    val registeredFor = actionsList.ifEmpty { "an IntentFilter that cannot be inspected by lint" }
    val message =
      """`${receiverArg.sourcePsi?.text ?: receiverArg.asSourceString()}` \
              |is missing `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED` flag for unprotected \
              |broadcasts registered for $registeredFor"""
        .trimMargin()
    val lintMap = map().put(HAS_UNPROTECTED_KEY, unprotectedActionsList.isNotEmpty())

    context.report(
      Incident(
        RECEIVER_EXPORTED_FLAG,
        node,
        context.getLocation(node),
        message,
        buildAlternativesFix(context, node),
      ),
      lintMap,
    )
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    // We are only sure an app will crash if:
    // 1. the app is targeting U+ AND
    // 2. we found unprotected actions
    if (
      context.mainProject.targetSdk < UPSIDE_DOWN_CAKE ||
        map.getBoolean(HAS_UNPROTECTED_KEY) != true
    ) {
      incident.overrideSeverity(Severity.WARNING)
    }
    return true
  }

  companion object {
    private const val RECEIVER_EXPORTED = 0x2
    private const val RECEIVER_NOT_EXPORTED = 0x4
    private const val RECEIVER_EXPORTED_FLAG_PRESENT_MASK =
      RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED

    private const val CLASS_HANDLER = "android.os.Handler"
    private const val FLAG_EXPORTED_STR = "androidx.core.content.ContextCompat.RECEIVER_EXPORTED"
    private const val FLAG_NOT_EXPORTED_STR =
      "androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED"
    private const val METHOD_REGISTER_RECEIVER_ANDROIDX =
      "androidx.core.content.ContextCompat.registerReceiver"

    private const val HAS_UNPROTECTED_KEY = "hasUnprotected"

    private const val EXPLANATION =
      """
      In Android U, all receivers registering for non-system broadcasts are required \
      to include a flag indicating the receiver's exported state. Apps registering for non-system \
      broadcasts should use the `ContextCompat#registerReceiver` APIs with flags set to either \
      `RECEIVER_EXPORTED` or `RECEIVER_NOT_EXPORTED`.

      If you are not expecting broadcasts from other apps on the device, \
      register your receiver with `RECEIVER_NOT_EXPORTED` to protect your receiver on all platform releases.
      """

    @JvmField
    val RECEIVER_EXPORTED_FLAG: Issue =
      Issue.create(
        id = "UnspecifiedRegisterReceiverFlag",
        briefDescription = "Missing `registerReceiver()` exported flag",
        explanation = EXPLANATION,
        moreInfo =
          "https://developer.android.com/reference/androidx/core/content/ContextCompat#registerReceiver(android.content.Context,android.content.BroadcastReceiver,android.content.IntentFilter,int)",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation =
          Implementation(
            RegisterReceiverFlagDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
          ),
      )

    private fun isReceiverExportedFlagPresent(node: UCallExpression): Boolean {
      val evaluator = ConstantEvaluator().allowFieldInitializers()

      val flagsArg = findArgument(node, TYPE_INT) ?: return false
      val flags = evaluator.evaluate(flagsArg) as? Int ?: return true
      return (flags and RECEIVER_EXPORTED_FLAG_PRESENT_MASK) != 0
    }

    private fun buildAlternativesFix(
      context: JavaContext,
      registerReceiverCallNode: UCallExpression,
    ): LintFix =
      LintFix.create()
        .alternatives(
          buildFlagFix(context, registerReceiverCallNode),
          buildFlagFix(context, registerReceiverCallNode, exported = true),
        )

    private fun buildFlagFix(
      context: JavaContext,
      registerReceiverCallNode: UCallExpression,
      exported: Boolean = false,
    ): LintFix? {
      val contextArgumentText =
        (registerReceiverCallNode.uastParent as? UQualifiedReferenceExpression)?.receiver?.let {
          retrieveUElementText(it)
        } ?: return null
      val broadcastReceiverText =
        retrieveArgumentText(registerReceiverCallNode, CLASS_BROADCASTRECEIVER) ?: return null
      val intentFilterText =
        retrieveArgumentText(registerReceiverCallNode, CLASS_INTENT_FILTER) ?: return null

      val originalFlagArg = retrieveArgumentText(registerReceiverCallNode, TYPE_INT) ?: ""
      val addFlagText = if (exported) FLAG_EXPORTED_STR else FLAG_NOT_EXPORTED_STR
      val isKotlin = context.uastFile?.lang == KotlinLanguage.INSTANCE
      val flagsText =
        if (originalFlagArg == "" || originalFlagArg == "0") {
          addFlagText
        } else if (isKotlin) {
          "$originalFlagArg or $addFlagText"
        } else {
          "$originalFlagArg | $addFlagText"
        }

      val broadcastPermissionText = retrieveArgumentText(registerReceiverCallNode, TYPE_STRING)
      val schedulerText = retrieveArgumentText(registerReceiverCallNode, CLASS_HANDLER)

      val fixText =
        if (broadcastPermissionText is String && schedulerText is String) {
          "$METHOD_REGISTER_RECEIVER_ANDROIDX($contextArgumentText, $broadcastReceiverText," +
            " $intentFilterText, $broadcastPermissionText, $schedulerText, $flagsText)"
        } else {
          "$METHOD_REGISTER_RECEIVER_ANDROIDX($contextArgumentText, $broadcastReceiverText," +
            " $intentFilterText, $flagsText)"
        }
      val name = if (exported) "Add RECEIVER_EXPORTED" else "Add RECEIVER_NOT_EXPORTED (preferred)"

      val fix =
        LintFix.create()
          .name(name)
          .replace()
          .reformat(true)
          .shortenNames()
          .range(context.getLocation(registerReceiverCallNode.uastParent))
          .with(fixText)

      return fix.build()
    }

    private fun retrieveArgumentText(call: UCallExpression, argumentType: String) =
      findArgument(call, argumentType)?.let { retrieveUElementText(it) }

    private fun retrieveUElementText(element: UElement) =
      element.sourcePsi?.text ?: element.asSourceString()
  }
}
