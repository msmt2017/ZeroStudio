/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.AndroidXConstants.CLASS_V4_FRAGMENT
import com.android.SdkConstants.CLASS_ACTIVITY
import com.android.SdkConstants.CLASS_VIEW
import com.android.sdklib.AndroidVersion.VersionCodes.S
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.nameFromSource
import com.android.tools.lint.detector.api.targetSdkAtLeast
import org.jetbrains.uast.UClass

class SplashScreenDetector : Detector(), SourceCodeScanner {
  override fun getApplicableUastTypes() = listOf(UClass::class.java)

  override fun createUastHandler(context: JavaContext) =
    object : UElementHandler() {
      override fun visitClass(node: UClass) {
        if (
          SPLASH_SCREEN_KEYWORDS.any {
            node.nameFromSource?.contains(it, ignoreCase = true) == true
          } && isActivityOrFragment(context, node)
        ) {
          if (node.sourcePsi == null) {
            // A compilation unit class for top level functions
            return
          }
          val incident =
            Incident(
              ISSUE,
              context.getNameLocation(node),
              "The application should not provide its own launch screen",
            )
          context.report(incident, targetSdkAtLeast(S))
        }
      }
    }

  private fun isActivityOrFragment(context: JavaContext, cls: UClass) =
    PROHIBITED_SUPERCLASSES.any { context.evaluator.extendsClass(cls.javaPsi, it) }

  companion object {
    private val SPLASH_SCREEN_KEYWORDS =
      listOf("SplashScreen", "SplashActivity", "LaunchActivity", "LaunchScreen")
    private val PROHIBITED_SUPERCLASSES =
      listOf(CLASS_ACTIVITY, CLASS_V4_FRAGMENT.oldName(), CLASS_V4_FRAGMENT.newName(), CLASS_VIEW)

    @JvmField
    val ISSUE =
      Issue.create(
          id = "CustomSplashScreen",
          briefDescription = "Application-defined Launch Screen",
          explanation =
            """
                Starting in Android 12 (API 31+), the application's Launch Screen is provided by \
                the system and the application should not create its own, otherwise the user will \
                see two splashscreens. Please check the `SplashScreen` class to check how the \
                Splash Screen can be controlled and customized.
            """,
          category = Category.CORRECTNESS,
          priority = 5,
          severity = Severity.WARNING,
          implementation = Implementation(SplashScreenDetector::class.java, Scope.JAVA_FILE_SCOPE),
          androidSpecific = true,
        )
        .addMoreInfo("https://developer.android.com/guide/topics/ui/splash-screen")
  }
}
