/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_MESSAGE
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_OVERRIDE
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_REFERENCE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.minSdkAtLeast
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.Locale
import org.jetbrains.uast.UElement
import org.jetbrains.uast.evaluateString

/** Flags calls to library APIs deprecated as of a particular API level. */
class DeprecatedSinceApiDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(DeprecatedSinceApiDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Calling a deprecated API */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "DeprecatedSinceApi",
        briefDescription = "Using a method deprecated in earlier SDK",
        explanation =
          """
          Some backport methods are only necessary until a specific version of Android. These have been \
          annotated with `@DeprecatedSinceApi`, specifying the relevant API level and replacement suggestions. \
          Calling these methods when the `minSdkVersion` is already at the deprecated API level or above is unnecessary.
          """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    private const val DEPRECATED_SDK_VERSION_ANNOTATION = "androidx.annotation.DeprecatedSinceApi"
    private const val ATTR_API = "api"
  }

  override fun applicableAnnotations(): List<String> = listOf(DEPRECATED_SDK_VERSION_ANNOTATION)

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    when (type) {
      METHOD_CALL,
      CLASS_REFERENCE,
      METHOD_REFERENCE,
      METHOD_OVERRIDE -> true
      else -> false
    }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val apiLevel =
      annotationInfo.annotation.findAttributeValue(ATTR_API)?.evaluate() as? Int ?: return
    val details = annotationInfo.annotation.findAttributeValue(ATTR_MESSAGE)?.evaluateString()
    val elementType =
      when (annotationInfo.annotated) {
        is PsiMethod -> "method"
        is PsiField -> "field"
        is PsiClass -> "class"
        else -> "element"
      }
    val message =
      "This $elementType is deprecated as of API level $apiLevel${
        if (details.isNullOrBlank()) "" else "; ${details.capitalize(Locale.US)}"
        }"
    context.report(
      Incident(ISSUE, message, context.getLocation(element), element, null),
      minSdkAtLeast(apiLevel),
    )
  }
}
