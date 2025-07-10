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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.checks.PermissionDetector.Companion.handlesException
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkLessThan
import com.android.utils.XmlUtils
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.w3c.dom.Attr
import org.w3c.dom.Element

/** Makes sure that alarms are handled correctly. */
class AlarmDetector : Detector(), SourceCodeScanner, XmlScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(
        AlarmDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        Scope.JAVA_FILE_SCOPE,
        Scope.MANIFEST_SCOPE,
      )

    /** Alarm set too soon/frequently. */
    @JvmField
    val SHORT_ALARM =
      Issue.create(
        id = "ShortAlarm",
        briefDescription = "Short or Frequent Alarm",
        explanation =
          """
            Frequent alarms are bad for battery life. As of API 22, the `AlarmManager` will override \
            near-future and high-frequency alarm requests, delaying the alarm at least 5 seconds into the \
            future and ensuring that the repeat interval is at least 60 seconds.

            If you really need to do work sooner than 5 seconds, post a delayed message or runnable to a \
            Handler.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val EXACT_ALARM =
      Issue.create(
        id = "ExactAlarm",
        briefDescription = "Invalid Usage of Exact Alarms",
        explanation =
          """
                The `USE_EXACT_ALARM` permission is only available when targeting API level 33 \
                and above. Also, note that this permission is only permitted for apps whose core \
                functionality requires precisely-timed actions for user facing features.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/training/scheduling/alarms",
      )

    @JvmField
    val SCHEDULE_EXACT_ALARM =
      Issue.create(
        id = "ScheduleExactAlarm",
        briefDescription = "Scheduling Exact Alarms Without Required Permission",
        explanation =
          """
                Applications looking to schedule exact alarms should ensure that the `SCHEDULE_EXACT_ALARM` \
                permission is granted by calling the `AlarmManager#canScheduleExactAlarms` API before attempting \
                to set an exact alarm. If the permission is not granted to your application, please consider \
                requesting it from the user by starting the `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` intent or gracefully \
                falling back to another option.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
        moreInfo = "https://developer.android.com/training/scheduling/alarms#exact",
      )

    // Relevant permissions for the two issues above
    const val USE_EXACT_ALARM_PERMISSION = "android.permission.USE_EXACT_ALARM"
    const val SCHEDULE_EXACT_ALARM_PERMISSION = "android.permission.SCHEDULE_EXACT_ALARM"

    // Used as a partial results map key to note that `AlarmManager#canScheduleExactAlarms` is
    // called somewhere
    const val CHECKS_EXACT_ALARM_PERMISSION = "ChecksExactAlarmPermission"

    // Min target SDK version for the `SCHEDULE_EXACT_ALARM` issue
    const val SCHEDULE_MIN_TARGET = 31
  }

  private val exactAlarmPermissionMethod = "canScheduleExactAlarms"
  private val shortAlarmMethod = "setRepeating"

  // Same strategy as in CommunicationDeviceDetector - we keep track of the locations of all calls
  // to
  // methods that schedule exact alarms, using strings "1", "2", "3", ... as keys.
  private var numScheduleCalls = 0

  override fun getApplicableMethodNames(): List<String> =
    listOf(shortAlarmMethod, exactAlarmPermissionMethod)

  override fun applicableAnnotations(): List<String> =
    listOf(PERMISSION_ANNOTATION.oldName(), PERMISSION_ANNOTATION.newName())

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val requirement = PermissionRequirement.create(annotationInfo.annotation)
    if (
      requirement.contains(SCHEDULE_EXACT_ALARM_PERMISSION) &&
        !handlesException(element, null, allowSuperClass = false, SECURITY_EXCEPTION) &&
        !context.driver.isSuppressed(context, SCHEDULE_EXACT_ALARM, element)
    )
      context
        .getPartialResults(SCHEDULE_EXACT_ALARM)
        .map()
        .put(numScheduleCalls++.toString(), context.getLocation(element))
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (context.isEnabled(SHORT_ALARM) || context.isEnabled(SCHEDULE_EXACT_ALARM)) {
      // We intentionally skip checking the containing class, and instead assume that any method
      // called "canScheduleExactAlarms" is checking for the permission (not just
      // android.app.AlarmManager#canScheduleExactAlarms). This covers
      // androidx.core.app.AlarmManagerCompat#canScheduleExactAlarms, as well as any other custom
      // methods implemented in other codebases.
      if (context.isEnabled(SCHEDULE_EXACT_ALARM) && method.name == exactAlarmPermissionMethod) {
        context
          .getPartialResults(SCHEDULE_EXACT_ALARM)
          .map()
          .put(CHECKS_EXACT_ALARM_PERMISSION, true)
      }
      val evaluator = context.evaluator
      if (evaluator.isMemberInClass(method, "android.app.AlarmManager")) {

        if (context.isEnabled(SHORT_ALARM) && evaluator.getParameterCount(method) == 4) {
          ensureAtLeast(context, node, 1, 5000L)
          ensureAtLeast(context, node, 2, 60000L)
        }
      }
    }
  }

  private fun ensureAtLeast(
    context: JavaContext,
    node: UCallExpression,
    parameter: Int,
    min: Long,
  ) {
    val argument = node.valueArguments[parameter]
    val value = getLongValue(context, argument)
    if (value < min) {
      val message =
        "Value will be forced up to $min as of Android 5.1; " + "don't rely on this to be exact"
      context.report(SHORT_ALARM, argument, context.getLocation(argument), message)
    }
  }

  private fun getLongValue(context: JavaContext, argument: UExpression): Long {
    val value = ConstantEvaluator.evaluate(context, argument)
    if (value is Number) {
      return value.toLong()
    }

    return java.lang.Long.MAX_VALUE
  }

  override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

  override fun visitElement(context: XmlContext, element: Element) {
    if (context.isEnabled(EXACT_ALARM)) {
      assert(element.tagName == TAG_USES_PERMISSION)
      val nameAttr: Attr? = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
      if (nameAttr != null && nameAttr.value == USE_EXACT_ALARM_PERMISSION) {
        context.report(
          Incident(
            EXACT_ALARM,
            "`USE_EXACT_ALARM` can only be used when targeting API level 33 or higher",
            context.getValueLocation(nameAttr),
          ),
          constraint = targetSdkLessThan(33),
        )
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(SCHEDULE_EXACT_ALARM))
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (partialResults.issue != SCHEDULE_EXACT_ALARM) return
    if (!context.isEnabled(SCHEDULE_EXACT_ALARM)) return
    if (context.mainProject.isLibrary) return
    if (context.mainProject.targetSdk < SCHEDULE_MIN_TARGET) return
    val mergedManifest = context.mainProject.mergedManifest ?: return

    // If there are any calls, globally, to `AlarmManager#canScheduleExactAlarms`, then we
    // assume all API calls are valid, and report no issues
    if (partialResults.maps().any { it.containsKey(CHECKS_EXACT_ALARM_PERMISSION) }) return

    var usesUsePermission = false
    var usesSchedulePermission = false

    for (element in XmlUtils.getSubTags(mergedManifest.documentElement)) {
      val nodeName = element.nodeName
      if (TAG_USES_PERMISSION == nodeName) {
        val nameAttr: Attr = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: continue
        when (nameAttr.value) {
          USE_EXACT_ALARM_PERMISSION -> usesUsePermission = true
          SCHEDULE_EXACT_ALARM_PERMISSION -> usesSchedulePermission = true
        }
      }
    }

    if (!(usesSchedulePermission && !usesUsePermission)) return

    for (perModuleLintMap in partialResults.maps()) {
      perModuleLintMap.forEach {
        // There should only be locations in the map since no map contained
        // `CHECKS_EXACT_ALARM_PERMISSION`
        val location = perModuleLintMap.getLocation(it)!!
        context.report(
          Incident(
            SCHEDULE_EXACT_ALARM,
            "When scheduling exact alarms, apps should explicitly call `AlarmManager#canScheduleExactAlarms`" +
              " or handle `SecurityException`s",
            location,
          )
        )
      }
    }
  }
}
