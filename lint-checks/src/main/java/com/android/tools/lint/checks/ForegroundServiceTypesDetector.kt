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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE
import com.android.SdkConstants.CLASS_SERVICE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.subtag
import com.android.utils.subtags
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.w3c.dom.Element

/**
 * Scan app's source code, looking for calls to Service.startForeground(), if targetSdkVersion >=
 * 34, the <service> element in the manifest file must have the foregroundServiceType attribute
 * specified.
 */
class ForegroundServiceTypesDetector : Detector(), XmlScanner, SourceCodeScanner {
  override fun getApplicableMethodNames() = listOf(START_FOREGROUND)

  override fun getApplicableUastTypes() = listOf(USimpleNameReferenceExpression::class.java)

  private fun manifestHasServiceTag(manifest: Element): Boolean {
    return manifest.subtag(TAG_APPLICATION)?.subtag(TAG_SERVICE) != null
  }

  private fun manifestHasForegroundServiceType(manifest: Element): Boolean {
    val applicationTag = manifest.subtag(TAG_APPLICATION) ?: return false
    for (serviceTag in applicationTag.subtags(TAG_SERVICE)) {
      if (serviceTag.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE).isNotEmpty()) {
        return true
      }
    }
    return false
  }

  override fun visitMethodCall(context: JavaContext, call: UCallExpression, method: PsiMethod) {
    if (START_FOREGROUND != method.name) {
      return
    }
    val evaluator = context.evaluator
    // startForeground() needs to be a member of subclass of "android.app.Service"[CLASS_SERVICE]
    // or a member of "androidx.core.app.ServiceCompat"[CLASS_SERVICE_COMPAT].
    if (
      !evaluator.isMemberInSubClassOf(method, CLASS_SERVICE, false) &&
        !evaluator.isMemberInClass(method, CLASS_SERVICE_COMPAT)
    ) {
      return
    }

    // check if manifest file has "foregroundServiceType" under <service> element.
    // It is difficult for lint to figure out which Service subclass the startForeground() is
    // called from, so we don't know which
    // <service> element is missing attribute foregroundServiceType. We just iterate through all
    // <service> elements, if none of them has attribute foregroundServiceType, report error.
    val manifest = context.project.manifestDom?.documentElement ?: return

    if (manifestHasServiceTag(manifest) && !manifestHasForegroundServiceType(manifest)) {
      val incident =
        Incident(
          ISSUE_TYPE,
          call,
          context.getNameLocation(call),
          "To call `Service.startForeground()`, the `<service>` element of manifest file must " +
            "have the `foregroundServiceType` attribute specified",
        )
      context.report(incident, targetSdkAtLeast(34))
    }
  }

  companion object {
    private const val START_FOREGROUND = "startForeground"
    private const val CLASS_SERVICE_COMPAT = "androidx.core.app.ServiceCompat"
    val IMPLEMENTATION =
      Implementation(
        ForegroundServiceTypesDetector::class.java,
        EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE,
      )

    /** Foreground service type related issues */
    val ISSUE_TYPE =
      create(
        id = "ForegroundServiceType",
        briefDescription = "Missing `foregroundServiceType` attribute in manifest",
        explanation =
          """
              For `targetSdkVersion` >= 34, to call `Service.startForeground()`, the <service> element in the \
              manifest file must have the `foregroundServiceType` attribute specified.
        """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR, // It is an error, missing permission causes SecurityException.
        implementation = IMPLEMENTATION,
      )
  }
}
