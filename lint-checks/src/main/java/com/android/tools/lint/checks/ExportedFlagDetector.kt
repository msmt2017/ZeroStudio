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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_ACTIVITY_ALIAS
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_NAV_GRAPH
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.sdklib.AndroidVersion.VersionCodes.S
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.subtag
import org.w3c.dom.Attr
import org.w3c.dom.Element

class ExportedFlagDetector : Detector(), XmlScanner {
  override fun getApplicableElements() =
    listOf(TAG_ACTIVITY, TAG_ACTIVITY_ALIAS, TAG_SERVICE, TAG_RECEIVER)

  override fun visitElement(context: XmlContext, element: Element) {
    val intentFilter = element.subtag(TAG_INTENT_FILTER)
    val navGraph = element.subtag(TAG_NAV_GRAPH)
    val exported = element.getAttributeNodeNS(ANDROID_URI, ATTR_EXPORTED)
    if ((intentFilter != null || navGraph != null) && exported == null) {
      val message =
        """
                As of Android 12, `android:exported` must be set; use `true` to make the activity \
                available to other apps, and `false` otherwise.
            """
          .trimIndent()

      // Check if the intent filter is for a launcher activity
      val incident =
        if (
          intentFilter
            ?.subtag(TAG_ACTION)
            ?.getAttributeNS(ANDROID_URI, ATTR_NAME)
            ?.equals(MAIN_ACTION) == true
        ) {
          Incident(
            ISSUE,
            element,
            context.getNameLocation(element),
            "$message For launcher activities, this should be set to `true`.",
            createSetToTrueFix(),
          )
        } else {
          Incident(
            ISSUE,
            element,
            context.getNameLocation(element),
            message,
            LintFix.create().alternatives(createSetToTrueFix(), createSetToFalseFix()),
          )
        }
      context.report(incident, map())
    } else if (isNonExportedLaunchable(exported, intentFilter)) {
      val incident =
        Incident(
            ISSUE,
            exported,
            context.getLocation(exported),
            "A launchable activity must be exported as of Android 12, which also makes it available to other apps.",
            createSetToTrueFix(),
          )
          .overrideSeverity(Severity.ERROR)
      context.report(incident, map())
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk >= S) {
      incident.overrideSeverity(Severity.ERROR)
    }
    return true
  }

  private fun isNonExportedLaunchable(exported: Attr?, intentFilterTag: Element?) =
    exported?.value == VALUE_FALSE &&
      intentFilterTag
        ?.subtag(TAG_ACTION)
        ?.getAttributeNS(ANDROID_URI, ATTR_NAME)
        ?.equals(MAIN_ACTION) == true &&
      intentFilterTag
        .subtag(TAG_CATEGORY)
        ?.getAttributeNS(ANDROID_URI, ATTR_NAME)
        ?.equals(CATEGORY_LAUNCHER) == true

  private fun createSetToTrueFix() =
    fix().set().android().attribute(ATTR_EXPORTED).value(VALUE_TRUE).build()

  private fun createSetToFalseFix() =
    fix().set().android().attribute(ATTR_EXPORTED).value(VALUE_FALSE).build()

  companion object {
    private const val MAIN_ACTION = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "IntentFilterExportedReceiver",
        briefDescription = "Unspecified `android:exported` in manifest",
        explanation =
          """
                Apps targeting Android 12 and higher are required to specify an explicit value \
                for `android:exported` when the corresponding component has an intent filter defined. \
                Otherwise, installation will fail. Set it to `true` to make this activity accessible \
                to other apps, and `false` to limit it to be used only by this app or the OS. For \
                launch activities, this should be set to true; otherwise, the app will fail to launch.

                Previously, `android:exported` for components without any intent filters present \
                used to default to `false`, and when intent filters were present, the default was \
                `true`. Defaults which change value based on other values are confusing and lead to \
                apps accidentally exporting components as a side-effect of adding intent filters. \
                This is a security risk, and we have made this change to avoid introducing \
                accidental vulnerabilities.

                While the default without intent filters remains unchanged, it is now required to \
                explicitly specify a value when intent filters are present. Any app failing to meet \
                this requirement will fail to install on any Android version after Android 11.

                We recommend setting `android:exported` to false (even on previous versions of Android \
                prior to this requirement) unless you have a good reason to export a particular \
                component.
            """,
        moreInfo = "https://goo.gle/IntentFilterExportedReceiver",
        category = Category.SECURITY,
        priority = 5,
        severity = Severity.WARNING,
        implementation = Implementation(ExportedFlagDetector::class.java, Scope.MANIFEST_SCOPE),
      )
  }
}
