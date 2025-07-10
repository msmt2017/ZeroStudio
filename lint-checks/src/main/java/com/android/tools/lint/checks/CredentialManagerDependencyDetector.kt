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

import com.android.SdkConstants.TAG_APPLICATION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.subtag

/**
 * Reports the `<application>` tag in the merged manifest if the following conditions all hold:
 * - the app supports Android 13 (minSDK <= 33)
 * - the app is not a Wear OS app
 * - the app depends on `:credentials` and does not depend on `:credentials-play-services-auth`
 *
 * This used to be implemented in [GradleDetector] and the warning was reported on a dependency
 * declaration for `:credentials`. However, the dependency could come from some prebuilt .aar
 * dependency, not from a Gradle declaration in the developer's codebase, and we would fail to
 * report the warning in this case. Also, a dependency declaration might affect multiple app modules
 * (especially in a wear app + mobile app project, with a common library module), and yet the
 * warning may only apply to a subset of the app modules. In particular, at the time of writing (mid
 * 2024), `:credentials-play-services-auth` does not support Wear OS, and so the dependency should
 * not be added to a wear app module. Thus, we report the incident on the `<application>` tag of
 * each app module where it causes a problem. This approach also allows reporting the problem for
 * build systems other than Gradle (such as Bazel), although this requires an implementation of
 * `project.dependsOn` that works for the build system.
 */
class CredentialManagerDependencyDetector : Detector(), XmlScanner {

  override fun checkMergedProject(context: Context) {
    checkAuthPlayServicesDependency(context)
  }

  private fun checkAuthPlayServicesDependency(context: Context) {
    val project = context.project
    if (project.isLibrary) return
    // Only applicable for Android 13 and below.
    if (project.minSdk > 33) return

    val manifest = project.mergedManifest?.documentElement ?: return
    // Not applicable on Wear OS.
    if (WearDetector.containsWearFeature(manifest)) return
    // We report the incident on the <application> tag.
    val application = manifest.subtag(TAG_APPLICATION) ?: return
    // Returns null if we are not sure.
    if (project.dependsOn("androidx.credentials:credentials") != true) return

    // project.dependsOn typically only returns true or null (for "not sure"). Since we have seen
    // dependsOn return true above, we assume not true (i.e. null) means the dependency is missing.
    if (project.dependsOn("androidx.credentials:credentials-play-services-auth") == true) return

    context.report(
      CREDENTIAL_DEP,
      context.getLocation(application),
      "This app supports Android 13 and depends on `androidx.credentials:credentials`, " +
        "and so should also depend on `androidx.credentials:credentials-play-services-auth`",
    )
  }

  companion object {

    private val IMPLEMENTATION =
      Implementation(CredentialManagerDependencyDetector::class.java, Scope.MANIFEST_SCOPE)

    @JvmField
    val CREDENTIAL_DEP =
      Issue.create(
        id = "CredentialDependency",
        briefDescription = "`credentials-play-services-auth` is Required",
        explanation =
          """
          The dependency `androidx.credentials:credentials-play-services-auth` is required \
          for Android 13 and below \
          to get support from Play services for the Credential Manager API \
          (`androidx.credentials:credentials`) to work. \
          For Android 14 and above, this is optional. Please check release notes for the \
          latest version.
          """,
        moreInfo = "https://developer.android.com/jetpack/androidx/releases/credentials",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}
