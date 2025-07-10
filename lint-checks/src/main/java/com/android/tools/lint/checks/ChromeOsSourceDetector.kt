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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.visitor.AbstractUastVisitor

class ChromeOsSourceDetector : Detector(), SourceCodeScanner {
  override fun createUastHandler(context: JavaContext) =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        if (node.name == "onConfigurationChanged") {
          node.uastBody?.accept(
            object : AbstractUastVisitor() {
              override fun visitCallExpression(node: UCallExpression): Boolean {
                if (node.methodName == "finish" && node.valueArgumentCount == 0) {
                  reportFinishInOnConfigurationChanged(node, context)
                }
                return super.visitCallExpression(node)
              }
            }
          )
        }
      }
    }

  override fun getApplicableUastTypes() = listOf(UMethod::class.java)

  override fun getApplicableMethodNames(): List<String> {
    return listOf("setRequestedOrientation", "hasSystemFeature")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val name = method.name
    if ("setRequestedOrientation" == name) {
      reportUnsupportedOrientationViolations(node, context)
    } else if ("hasSystemFeature" == name) {
      reportRearCameraOnlySystemFeatureViolations(node, context)
    }
  }

  private fun reportRearCameraOnlySystemFeatureViolations(
    node: UCallExpression,
    context: JavaContext,
  ) {
    val cameraFeatureRequested = determinePropertyString(node)
    if (FEATURE_CAMERA_STRING == cameraFeatureRequested) {
      val message = "You should look for any camera available on the device, not just the rear"
      val fix =
        fix()
          .name("Switch to look for FEATURE_CAMERA_ANY")
          .replace()
          .text(cameraFeatureRequested)
          .with(FEATURE_CAMERA_ANY_STRING)
          .build()

      context.report(UNSUPPORTED_CAMERA_FEATURE, node, context.getLocation(node), message, fix)
    }
  }

  private fun reportUnsupportedOrientationViolations(node: UCallExpression, context: JavaContext) {
    val orientationValue = determinePropertyString(node) ?: return
    if (UNSUPPORTED_ORIENTATIONS.contains(orientationValue)) {
      val message =
        "You should not lock orientation of your activities, so that you can " +
          "support a good user experience for any device or orientation"
      val fix =
        fix()
          .name("Set the orientation to SCREEN_ORIENTATION_UNSPECIFIED")
          .replace()
          .text(orientationValue)
          .with(UNSPECIFIED_ORIENTATION_VALUE)
          .build()

      context.report(UNSUPPORTED_LOCKED_ORIENTATION, node, context.getLocation(node), message, fix)
    }
  }

  private fun reportFinishInOnConfigurationChanged(node: UCallExpression, context: JavaContext) {
    val message = "Calling `finish()` within `onConfigurationChanged()` can lead to redraws"
    context.report(CHROMEOS_ON_CONFIGURATION_CHANGED, node, context.getLocation(node), message)
  }

  private fun determinePropertyString(node: UCallExpression): String? {
    val firstArgument = node.valueArguments.firstOrNull()
    val reference = firstArgument as? UReferenceExpression ?: return null
    return reference.resolvedName
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(ChromeOsSourceDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val CHROMEOS_ON_CONFIGURATION_CHANGED =
      Issue.create(
          id = "ChromeOsOnConfigurationChanged",
          briefDescription = "Poor performance with APIs inside `onConfigurationChanged()`",
          explanation =
            """
                When users resize the Android emulator in Android 13 and Chrome OS, an \
                `onConfigurationChanged()` API call occurs. If your `onConfigurationChanged()` \
                method contains any code that can cause a redraw, your app might take a performance \
                hit on large screens. To fix the issue, ensure your `onConfigurationChanged()` method \
                does not contain any calls to UI redraw logic for specific elements.
            """,
          category = Category.CHROME_OS,
          priority = 4,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION,
        )
        .setEnabledByDefault(true)

    @JvmField
    val UNSUPPORTED_LOCKED_ORIENTATION =
      Issue.create(
          id = "SourceLockedOrientationActivity",
          briefDescription = "Incompatible setRequestedOrientation value",
          explanation =
            """
                The `Activity` should not be locked to a portrait orientation so that users \
                can take advantage of the multi-window environments and larger landscape-first \
                screens that Android runs on such as ChromeOS, tablets, and foldables. To fix \
                the issue, consider calling `setRequestedOrientation` with the \
                `ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR` or \
                `ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED` options or removing the \
                call all together.
                """,
          category = Category.CORRECTNESS,
          priority = 4,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION,
          moreInfo =
            "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#restricted_app_orientation",
        )
        .setEnabledByDefault(true)

    @JvmField
    val UNSUPPORTED_CAMERA_FEATURE =
      Issue.create(
          id = "UnsupportedChromeOsCameraSystemFeature",
          briefDescription = "Looking for Rear Camera only feature",
          explanation =
            """
                You should look for the `FEATURE_CAMERA_ANY` features to include all possible \
                cameras that may be on the device. Looking for `FEATURE_CAMERA` only looks \
                for a rear facing camera, which certain large screen devices don't have, \
                as well as newer device configurations and modes may place the device \
                in a state where the rear camera is not available. To fix the issue, \
                look for `FEATURE_CAMERA_ANY` instead.
                """,
          category = Category.CORRECTNESS,
          priority = 4,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION,
          moreInfo =
            "https://developer.android.com/guide/topics/large-screens/large-screen-cookbook#chromebook_camera_support",
        )
        .setEnabledByDefault(true)

    val UNSUPPORTED_ORIENTATIONS =
      setOf(
        "SCREEN_ORIENTATION_PORTRAIT",
        "SCREEN_ORIENTATION_REVERSE_PORTRAIT",
        "SCREEN_ORIENTATION_SENSOR_PORTRAIT",
        "SCREEN_ORIENTATION_USER_PORTRAIT",
      )

    const val UNSPECIFIED_ORIENTATION_VALUE = "SCREEN_ORIENTATION_UNSPECIFIED"

    const val FEATURE_CAMERA_ANY_STRING = "FEATURE_CAMERA_ANY"

    const val FEATURE_CAMERA_STRING = "FEATURE_CAMERA"
  }
}
