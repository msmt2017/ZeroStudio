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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.minSdkLessThan
import com.android.utils.XmlUtils
import com.android.utils.subtag
import org.w3c.dom.Element

class WearSplashScreenDetector : WearDetector(), XmlScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(WearSplashScreenDetector::class.java, Scope.MANIFEST_SCOPE, Scope.GRADLE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
          id = "WearSplashScreen",
          briefDescription = "Wear: Use `SplashScreen` library",
          explanation =
            """
              If your app implements a custom splash screen or uses a launcher theme, migrate your app to the `SplashScreen` library, \
              available in Jetpack, to ensure it displays correctly on all Wear OS versions. \
              Starting in Android 12, the system always applies the new Android system default splash screen on cold and warm starts \
              for all apps. By default, this system default splash screen is constructed using your app’s launcher icon element and \
              the `windowBackground` of your theme (if it's a single color). \
              If you do not migrate your app, your app launch experience on Android 12 and higher will be either degraded or may have \
              unintended results.
            """,
          category = Category.USABILITY,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
          enabledByDefault = true,
          androidSpecific = true,
        )
        .addMoreInfo("https://developer.android.com/training/wearables/apps/splash-screen")

    private const val MAIN_ACTION = "android.intent.action.MAIN"
    private const val CATEGORY_LAUNCHER = "android.intent.category.LAUNCHER"
  }

  private var hasSplashScreenLibrary = false

  override fun getApplicableElements(): Collection<String> =
    listOf(SdkConstants.TAG_ACTIVITY, SdkConstants.TAG_ACTIVITY_ALIAS)

  override fun beforeCheckFile(context: Context) {
    hasSplashScreenLibrary =
      context.project.isGradleProject() &&
        (context.project.dependsOn(SdkConstants.ANDROIDX_CORE_SPLASHSCREEN) ?: false)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // We check that this is a wear project and that we have not detected a launcher activity
    // already
    if (!isWearProject) return
    if (hasSplashScreenLibrary) return

    // Flag only suspicious activities using the name Splash.
    if (
      !element.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME).contains("Splash")
    )
      return

    val intentFilterTag =
      XmlUtils.getFirstSubTagByName(element, SdkConstants.TAG_INTENT_FILTER) ?: return
    val isLauncherActivity =
      intentFilterTag
        .subtag(SdkConstants.TAG_ACTION)
        ?.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
        ?.equals(MAIN_ACTION) == true &&
        intentFilterTag
          .subtag(SdkConstants.TAG_CATEGORY)
          ?.getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_NAME)
          ?.equals(CATEGORY_LAUNCHER) == true

    if (isLauncherActivity) {
      context.report(
        Incident(
          ISSUE,
          context.getLocation(element),
          "Applications using splash screens are strongly recommended to use the '${SdkConstants.ANDROIDX_CORE_SPLASHSCREEN}' library",
        ),
        minSdkLessThan(31),
      )
    }
  }
}
