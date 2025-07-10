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

import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks
import com.android.tools.lint.detector.api.minSdkLessThan
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Shows warnings on calls to `androidx.credentials.CreatePublicKeyCredentialRequest` in projects
 * that depend on `androidx.credentials:credentials-play-services-auth` and have a minimum SDK level
 * below 28 (Android 9). This is a standalone lint check rather than an `@RequiresApi` annotation at
 * the definition of `CreatePublicKeyCredentialRequest` because if an app uses a custom passkey
 * implementation the SDK requirement may be different. As of January 2024 there are no other
 * implementations, so this is a forward-looking constraint.
 */
class PublicKeyCredentialDetector : Detector(), SourceCodeScanner {

  companion object {
    private val IMPLEMENTATION =
      Implementation(PublicKeyCredentialDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "PublicKeyCredential",
        briefDescription = "Creating public key credential",
        explanation =
          """
Credential Manager API supports creating public key credential (Passkeys) starting Android 9 or higher. \
Please check for the Android version before calling the method.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    const val PUBLIC_KEY_CREDENTIAL_CLASS_FQNAME =
      "androidx.credentials.CreatePublicKeyCredentialRequest"
    const val MIN_SDK_FOR_PUBLIC_KEY_CREDENTIAL = 28
    const val PLAY_SERVICES_DEPENDENCY = "androidx.credentials:credentials-play-services-auth"
  }

  override fun getApplicableConstructorTypes() = listOf(PUBLIC_KEY_CREDENTIAL_CLASS_FQNAME)

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    if (context.project.dependsOn(PLAY_SERVICES_DEPENDENCY) == true) {
      val api = ApiConstraint.atLeast(MIN_SDK_FOR_PUBLIC_KEY_CREDENTIAL)
      if (
        VersionChecks.isWithinVersionCheckConditional(context, node, api) ||
          VersionChecks.isPrecededByVersionCheckExit(context, node, api)
      ) {
        return
      }

      val incident =
        Incident(context)
          .issue(ISSUE)
          .location(context.getLocation(node))
          .message("PublicKeyCredential is only supported from Android 9 (API level 28) and higher")
      context.report(incident, minSdkLessThan(MIN_SDK_FOR_PUBLIC_KEY_CREDENTIAL))
    }
  }
}
