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

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.DEFINITION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_API_ANNOTATION
import com.intellij.psi.PsiModifierListOwner
import java.util.EnumSet
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UVariable

/** Makes sure that in tests, `@SdkSuppress` is used instead of `@RequiresApi`. */
class SdkSuppressDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(
        SdkSuppressDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
      )

    /** Using `@RequiresApi` instead of `SdkSuppress` in tests. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UseSdkSuppress",
        briefDescription = "Using `@SdkSuppress` instead of `@RequiresApi`",
        explanation =
          """
          In tests, you should be using `@SdkSuppress` instead of `@RequiresApi`. \
          The `@RequiresApi` annotation is used to propagate a version requirement \
          out to the caller of the API -- but the testing framework is only looking \
          for `@SdkSuppress`, which it uses to skip tests that are intended for \
          newer versions.
          """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }

  override fun applicableAnnotations(): List<String> =
    listOf(
      REQUIRES_API_ANNOTATION.oldName(),
      REQUIRES_API_ANNOTATION.newName(),
      /* Not enforced yet since @SdkSuppress doesn't support extensions yet;see b/257429573
      REQUIRES_EXTENSION_ANNOTATION,
       */
    )

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean = type == DEFINITION

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (!context.isTestSource) {
      return
    }

    val annotation = annotationInfo.annotation
    val annotated = annotation.uastParent

    // We only warn about requires annotations where @SdkSuppress
    // makes sense -- classes and methods.
    if (annotated !is UClass && annotated !is UMethod) {
      return
    }

    val modifierOwner = annotated.javaPsi
    if (modifierOwner is PsiModifierListOwner && !context.evaluator.isPublic(modifierOwner)) {
      return
    }

    val source = annotation.sourcePsi?.text
    val fix =
      if (source != null) {
        val index = source.indexOf('=').let { if (it != -1) it else source.indexOf('(') }
        val valueStart = index + 1
        val value = source.substring(valueStart).trim()
        fix()
          .replace()
          .text(source)
          .with("@androidx.test.filters.SdkSuppress(minSdkVersion=$value")
          .range(context.getLocation(element))
          .reformat(true)
          .shortenNames()
          .build()
      } else {
        null
      }
    val message = StringBuilder("Don't use @RequiresApi from tests; use @SdkSuppress")

    // Include name of annotated method (or class or field) to make baseline message more unique
    val name =
      when (annotated) {
        is UMethod -> annotated.name
        is UVariable -> annotated.name
        is UClass -> @Suppress("UElementAsPsi") annotated.name
        else -> null
      }
    if (name != null) {
      message.append(" on `$name`")
    }
    message.append(" instead")

    context.report(ISSUE, element, context.getNameLocation(element), message.toString(), fix)
  }
}
