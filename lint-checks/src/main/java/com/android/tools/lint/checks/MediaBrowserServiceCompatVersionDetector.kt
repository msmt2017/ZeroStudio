/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.SdkConstants.SUPPORT_LIB_ARTIFACT
import com.android.ide.common.gradle.Version
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.model.LintModelExternalLibrary
import org.jetbrains.uast.UClass

/** Constructs a new [MediaBrowserServiceCompatVersionDetector] check. */
class MediaBrowserServiceCompatVersionDetector : Detector(), SourceCodeScanner {

  companion object Issues {

    @JvmField
    val ISSUE =
      Issue.create(
        id = "IncompatibleMediaBrowserServiceCompatVersion",
        briefDescription = "Obsolete version of MediaBrowserServiceCompat",
        explanation =
          """
            `MediaBrowserServiceCompat` from version 23.2.0 to 23.4.0 of the Support v4 Library \
            used private APIs and will not be compatible with future versions of Android beyond Android N. \
            Please upgrade to version 24.0.0 or higher of the Support Library.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(
            MediaBrowserServiceCompatVersionDetector::class.java,
            Scope.JAVA_FILE_SCOPE,
          ),
      )

    /**
     * Minimum recommended support library version that has the necessary fixes to ensure that
     * MediaBrowserServiceCompat is forward compatible with N.
     */
    val MIN_SUPPORT_V4_VERSION = Version.parse("24.0.0")

    const val MEDIA_BROWSER_SERVICE_COMPAT = "android.support.v4.media.MediaBrowserServiceCompat"
  }

  override fun applicableSuperClasses(): List<String> {
    return listOf(MEDIA_BROWSER_SERVICE_COMPAT)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    if (!context.evaluator.extendsClass(declaration.javaPsi, MEDIA_BROWSER_SERVICE_COMPAT, true)) {
      return
    }

    val library =
      context.project.buildVariant?.mainArtifact?.findCompileDependency(SUPPORT_LIB_ARTIFACT)
        as? LintModelExternalLibrary ?: return
    val mc = library.resolvedCoordinates
    if (mc.version.isNotBlank()) {
      val libVersion = Version.parse(mc.version)
      if (libVersion < MIN_SUPPORT_V4_VERSION) {
        val location = GradleDetector.getDependencyLocation(context, mc)
        val message = "Using a version of the class that is not forward compatible"
        context.report(ISSUE, location, message)
      }
    }
  }
}
