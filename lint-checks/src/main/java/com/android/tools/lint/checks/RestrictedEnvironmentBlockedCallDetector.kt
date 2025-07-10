/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.model.LintModelModuleType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import java.util.EnumSet
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.toUElementOfType

/**
 * Reports calls to methods that are blocked in the Privacy Sandbox (or any other restricted
 * environment).
 *
 * The check is disabled by default because most code will never run in the Privacy Sandbox. When
 * enabled, the check reports conditional incidents; these will only be reported (as definite
 * incidents) once we reach a sandboxed SDK module, and only if the targetSdk is high enough.
 *
 * The check includes a hardcoded map of blocked Android Platform classes that are blocked starting
 * at some targetSdk (and above). The check also handles `@RestrictedForEnvironment` and
 * `@ChecksRestrictedEnvironment` annotations.
 *
 * Note that referencing a blocked class is always allowed; only method calls are considered
 * blocked.
 *
 * The check is currently very forgiving, which can lead to false-negatives (missed warnings). Any
 * environment check (such as `Process.isSdkSandbox()`) within a method/block will prevent any
 * blocked calls from being reported within that method/block below that point. Note that this could
 * be a large method/block containing nested functions/classes/objects, and the blocked calls within
 * the nested elements will not be reported. This could be improved at the cost of probably
 * introducing false-positives.
 */
class RestrictedEnvironmentBlockedCallDetector : Detector(), SourceCodeScanner {

  /**
   * Stores relevant blocked calls and "environment check" expressions. The list of expressions (the
   * value) is associated with the outermost block/method (the key) so that we can ignore blocked
   * calls that occur after an "environment check" within that scope.
   *
   * The map is cleared in [beforeCheckFile] and the actual reporting is done in [afterCheckFile].
   */
  private val relevantExprs: MutableMap<UElement, MutableList<RelevantExpr>> = mutableMapOf()

  private sealed interface RelevantExpr {
    data class BlockedCall(
      val callExpression: UCallExpression,
      val from: Int,
      val environment: String,
    ) : RelevantExpr

    data class EnvironmentCheck(val expression: UExpression, val environment: String) :
      RelevantExpr
  }

  override fun getApplicableUastTypes() = listOf<Class<out UElement>>(UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {

    // TODO: return a null handler if/when the compileSdk is recent enough, such that
    //  the blocked Android Platform classes/methods are annotated.

    return object : UElementHandler() {

      override fun visitCallExpression(node: UCallExpression) {

        val method = node.resolve() ?: return
        if (
          method.name == "isSdkSandbox" &&
            context.evaluator.methodMatches(method, "android.os.Process", false)
        ) {
          // If the method is annotated, then return early, as we will trigger the same code via
          // visitAnnotationUsage, possibly with more info from the annotation.

          // No need to check outer nor inherited annotations, as this is a static method of
          // android.os.Process.
          if (
            method.toUElementOfType<UMethod>()?.uAnnotations?.any {
              it.qualifiedName == CHECKS_SANDBOX_ANNOTATION_FQN
            } == true
          )
            return

          visitCheckExpression(node, SDK_SANDBOX_ENVIRONMENT_NAME)
          return
        }
        val containingClassFqn = method.containingClass?.qualifiedName ?: return
        val blockedFrom = BLOCKED_CLASSES_FROM[containingClassFqn]
        if (blockedFrom != null) {
          // If the method is annotated, then return early, as we will trigger the same code via
          // visitAnnotationUsage, possibly with more info from the annotation.
          if (
            method.outerAnnotations(context).any { it.qualifiedName == RESTRICTED_ANNOTATION_FQN }
          )
            return

          visitBlockedCall(context, node, blockedFrom, SDK_SANDBOX_ENVIRONMENT_NAME)
          return
        }
      }
    }
  }

  override fun applicableAnnotations() =
    listOf(RESTRICTED_ANNOTATION_FQN, CHECKS_SANDBOX_ANNOTATION_FQN)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType) =
    when (type) {
      AnnotationUsageType.METHOD_CALL,
      AnnotationUsageType.FIELD_REFERENCE,
      AnnotationUsageType.VARIABLE_REFERENCE -> true
      else -> false
    }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    when (annotationInfo.qualifiedName) {
      RESTRICTED_ANNOTATION_FQN -> {
        when (usageInfo.type) {
          AnnotationUsageType.METHOD_CALL -> {
            val callExpression = element as? UCallExpression ?: return
            val from =
              annotationInfo.annotation.findAttributeValue(ANNOTATION_ATTR_FROM)?.evaluate() as? Int
                ?: return
            for (environment in annotationInfo.annotation.restrictedEnvironments) {
              visitBlockedCall(context, callExpression, from, environment)
            }
          }
          else -> {}
        }
      }
      CHECKS_SANDBOX_ANNOTATION_FQN -> {
        when (usageInfo.type) {
          AnnotationUsageType.METHOD_CALL,
          AnnotationUsageType.FIELD_REFERENCE,
          AnnotationUsageType.VARIABLE_REFERENCE -> {
            val expression = element as? UExpression ?: return
            for (environment in annotationInfo.annotation.restrictedEnvironments) {
              visitCheckExpression(expression, environment)
            }
          }
          else -> {}
        }
      }
      else -> {}
    }
  }

  private val UAnnotation.restrictedEnvironments: Sequence<String>
    get() {
      val annotation = this
      val envArrayCall =
        annotation.findAttributeValue(ANNOTATION_ATTR_ENVS) as? UCallExpression
          ?: return emptySequence()
      if (!envArrayCall.hasKind(UastCallKind.NESTED_ARRAY_INITIALIZER)) return emptySequence()
      return envArrayCall.valueArguments.asSequence().mapNotNull {
        (it as? UReferenceExpression)?.resolvedName
      }
    }

  private fun PsiElement.outerAnnotations(context: JavaContext): Sequence<UAnnotation> = sequence {
    var cur: PsiElement? = this@outerAnnotations
    while (cur != null) {
      if (cur is PsiModifierListOwner) {
        yieldAll(context.evaluator.getAnnotations(cur, true))
      }
      cur = cur.parent
    }
  }

  private fun UElement.outerAnnotations(context: JavaContext): Sequence<UAnnotation> = sequence {
    var cur: UElement? = this@outerAnnotations
    while (cur != null) {
      if (cur is UAnnotated) {
        yieldAll(context.evaluator.getAllAnnotations(cur, true))
      }
      cur = cur.uastParent
    }
  }

  private fun outermostMethodOrBlock(element: UElement): UElement? {
    var lastMethodOrBlock: UElement? = null
    var cur: UElement? = element
    while (cur != null) {
      if (cur is UMethod || cur is UBlockExpression || cur is UField) {
        lastMethodOrBlock = cur
      }
      cur = cur.uastParent
    }
    return lastMethodOrBlock
  }

  private fun visitBlockedCall(
    context: JavaContext,
    callExpression: UCallExpression,
    blockedSinceTargetSdk: Int,
    environment: String,
  ) {
    // Check outer annotations, and potentially return early.
    for (anno in callExpression.outerAnnotations(context)) {
      if (anno.qualifiedName != RESTRICTED_ANNOTATION_FQN) continue
      if (!anno.restrictedEnvironments.contains(environment)) continue
      // Note: We give up completely (return, not continue) if we can't evaluate the "from"
      // attribute.
      val blockedSinceOuter =
        anno.findAttributeValue(ANNOTATION_ATTR_FROM)?.evaluate() as? Int ?: return
      // If the outer annotation's "blocked since targetSdk" <= the call's "blocked since
      // targetSdk", then there will already be a warning due to the outer annotation, so return
      // early.
      if (blockedSinceOuter <= blockedSinceTargetSdk) return
    }

    val outermost = outermostMethodOrBlock(callExpression) ?: return
    relevantExprs
      .getOrPut(outermost) { mutableListOf() }
      .add(RelevantExpr.BlockedCall(callExpression, blockedSinceTargetSdk, environment))
  }

  private fun visitCheckExpression(expression: UExpression, environment: String) {
    val outermost = outermostMethodOrBlock(expression) ?: return
    relevantExprs
      .getOrPut(outermost) { mutableListOf() }
      .add(RelevantExpr.EnvironmentCheck(expression, environment))
  }

  override fun beforeCheckFile(context: Context) {
    relevantExprs.clear()
  }

  override fun afterCheckFile(context: Context) {
    if (context !is JavaContext) return

    for ((_, exprs) in relevantExprs) {
      // TODO: This is where we could improve the check by analyzing each block/method
      //  via the CFG or DataflowAnalyzer. For example:
      //  - Look for conditional early returns, where the condition flows from an environment check.
      //  - Look for conditional blocked calls, where the condition flows from a environment check.
      //  There would probably be false-positives, but perhaps this is acceptable.
      //  Our approach below is very simple and will result in false-negatives (missed warnings)
      //  when, for example, there are multiple blocked calls within a method/block and just a
      //  single call to `isSdkSandbox()` (even if nothing is done with the result).

      // Track each environment check seen so far (within the current block/method).
      val environmentChecksSeen = hashSetOf<String>()

      for (expr in exprs) {
        when (expr) {
          is RelevantExpr.EnvironmentCheck -> environmentChecksSeen.add(expr.environment)
          is RelevantExpr.BlockedCall -> {
            // Skip the blocked call if we have seen an environment check.
            if (environmentChecksSeen.contains(expr.environment)) continue
            // Otherwise, report the blocked call as a conditional incident (depends on the
            // targetSdk and whether we end up reaching a sandboxed SDK module).
            var message = "Call is blocked in the Privacy Sandbox"
            if (expr.from > 1) {
              message += " when `targetSdk` is ${expr.from} or above"
            }
            context.report(
              Incident(ISSUE, context.getLocation(expr.callExpression), message),
              LintMap()
                .put(LINT_MAP_ENVIRONMENT_KEY, expr.environment)
                .put(LINT_MAP_TARGET_SDK_KEY, expr.from),
            )
          }
        }
      }
    }
    relevantExprs.clear()
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (
      isPrivacySandboxSdkProject(context.mainProject) &&
        map.getString(LINT_MAP_ENVIRONMENT_KEY) == SDK_SANDBOX_ENVIRONMENT_NAME
    ) {
      val blockedSinceTargetSdk = map.getInt(LINT_MAP_TARGET_SDK_KEY) ?: return false
      if (context.mainProject.targetSdk >= blockedSinceTargetSdk) return true
    }
    return false
  }

  private fun isPrivacySandboxSdkProject(project: Project): Boolean {
    return project.type == LintModelModuleType.PRIVACY_SANDBOX_SDK
  }

  companion object {
    private const val ANNOTATION_ATTR_FROM = "from"
    private const val ANNOTATION_ATTR_ENVS = "environments"
    private const val RESTRICTED_ANNOTATION_FQN = "androidx.annotation.RestrictedForEnvironment"
    private const val CHECKS_SANDBOX_ANNOTATION_FQN =
      "androidx.annotation.ChecksRestrictedEnvironment"

    /**
     * The initial release of the environment names enum was just "SDK_SANDBOX". We treat
     * `Process.isSdkSandbox()` as being annotated with `@ChecksSandbox(environment = SDK_SANDBOX)`.
     */
    private const val SDK_SANDBOX_ENVIRONMENT_NAME = "SDK_SANDBOX"

    private const val LINT_MAP_ENVIRONMENT_KEY = "env"
    private const val LINT_MAP_TARGET_SDK_KEY = "sdk"

    // We could use JAVA_FILE_SCOPE so that the check runs on-the-fly; however, at the time of
    // writing, the warnings will not show because the sandboxed SDK is usually (always?) a
    // separate module with no code.
    private val IMPLEMENTATION =
      Implementation(
        RestrictedEnvironmentBlockedCallDetector::class.java,
        EnumSet.of(Scope.ALL_JAVA_FILES),
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "PrivacySandboxBlockedCall",
        briefDescription = "Call is blocked in the Privacy Sandbox",
        explanation =
          """
          Many APIs are unavailable in the Privacy Sandbox, depending on the `targetSdk`.

          If your code is designed to run in the sandbox (and never outside the sandbox) then you should remove the \
          blocked calls to avoid exceptions at runtime.

          If your code is part of a library that can be executed both inside and outside the sandbox, \
          surround the code with `if (!Process.isSdkSandbox()) { ... }` (or use your own field or method annotated \
          with `@ChecksRestrictedEnvironment`) to avoid executing blocked calls when in the sandbox. Or, add the \
          `@RestrictedForEnvironment` annotation to the containing method if the entire method should not be called \
          when in the sandbox.

          This check is disabled by default, and should only be enabled in modules that may execute in the \
          Privacy Sandbox.
          """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
        enabledByDefault = false,
      )

    /**
     * A map of hardcoded fully-qualified class names (the keys) that are blocked in the Privacy
     * Sandbox, starting at the given targetSdk (the values). These will hopefully not be needed
     * from a certain compileSdk because the classes/methods will be annotated.
     */
    private val BLOCKED_CLASSES_FROM =
      hashMapOf(
        "android.hardware.biometrics.BiometricManager" to 34,
        "android.app.blob.BlobStoreManager" to 34,
        "android.os.BugReportManager" to 34,
        "android.content.pm.CrossProfileApps" to 34,
        "android.app.admin.DevicePolicyManager" to 34,
        "android.content.pm.verify.domain.DomainVerificationManager" to 34,
        "android.security.FileIntegrityManager" to 34,
        "android.hardware.fingerprint.FingerprintManager" to 34,
        "android.health.connect.HealthConnectManager" to 34,
        "android.app.people.PeopleManager" to 34,
        "android.app.sdksandbox.SdkSandboxManager" to 34,
        "android.content.pm.ShortcutManager" to 34,
        "android.app.slice.SliceManager" to 34,
        "android.companion.virtual.VirtualDeviceManager" to 34,
        "android.net.VpnManager" to 34,
        "android.net.wifi.WifiManager" to 34,
        "android.net.wifi.aware.WifiAwareManager" to 34,
        "android.net.wifi.p2p.WifiP2pManager" to 34,
        "android.location.CountryDetector" to 1,
        "android.app.tare.EconomyManager" to 1,
        "android.app.trust.TrustManager" to 1,
        "android.hardware.devicestate.DeviceStateManager" to 1,
      )
  }
}
