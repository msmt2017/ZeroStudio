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
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod

/** Flags misleading nullability annotations. */
class KotlinNullnessAnnotationDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(KotlinNullnessAnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Incorrect nullability annotation */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "KotlinNullnessAnnotation",
        briefDescription = "Kotlin nullability annotation",
        explanation =
          """
                In Kotlin, nullness is part of the type system; `s: String` is **never** null \
                and `s: String?` is sometimes null, whether or not you add in additional annotations \
                stating `@NonNull` or `@Nullable`. These are likely copy/paste mistakes, and are \
                misleading.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
        enabledByDefault = true,
      )

    const val IDEA_NULLABLE = "org.jetbrains.annotations.Nullable"
    const val IDEA_NOTNULL = "org.jetbrains.annotations.NotNull"
  }

  override fun applicableAnnotations(): List<String> =
    listOf(
      "Nullable", // everybody
      "NonNull", // androidx
      "NotNull", // jetbrains
      "Nonnull", // jsr305
    )

  override fun inheritAnnotation(annotation: String): Boolean {
    // Require restriction annotations to be annotated everywhere
    return false
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.DEFINITION
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // Only applies for Kotlin annotations
    // with source, i.e., skip synthetic nullness annotations added by UAST
    element.sourcePsi ?: return
    if (!isKotlin(element.lang)) {
      return
    }
    val annotated = element.uastParent as? UAnnotated ?: return

    // What is the nullness implied by the Kotlin type? This will return "Nullable" if it's
    // nullable (as in `Any?`) and NotNull if it's not (as in `Any`).
    val actualTypeAnnotation = findKotlinTypeAnnotation(annotated, annotationInfo) ?: return
    val annotationName =
      annotationInfo.qualifiedName.substringAfterLast('.', annotationInfo.qualifiedName)
    val declaredNullable = annotationName.endsWith("Nullable")
    val isNullable = actualTypeAnnotation == "Nullable"
    val annotationContradictsKotlinType = declaredNullable != isNullable
    if (
      !annotationContradictsKotlinType &&
        annotationInfo.qualifiedName.startsWith("javax.annotation")
    ) {
      // Don't flag redundant annotations in javax since they have runtime retention and
      // theoretically could
      // be placed there for some sort of introspection
      return
    }

    val message =
      with(StringBuilder("Do not use `@$annotationName` in Kotlin; ")) {
        val typeString = findKotlinTypeString(annotated)
        if (annotationContradictsKotlinType) {
          append("the nullability is determined by the Kotlin type ")
          if (typeString != null) {
            append("`").append(typeString).append("` ")
            assert(isNullable == typeString.endsWith("?"))
          }
          if (isNullable) {
            append("ending with `?` which declares it nullable")
          } else {
            append("**not** ending with `?` which declares it not nullable")
          }
          append(", contradicting the annotation")
        } else {
          append("the nullability is already implied by the Kotlin type ")
          if (typeString != null) {
            append("`").append(typeString).append("` ")
            assert(isNullable == typeString.endsWith("?"))
          }
          if (declaredNullable) {
            append("ending with `?`")
          } else {
            append("**not** ending with `?`")
          }
        }
        toString()
      }

    val location = context.getLocation(element)
    val fixLocation = locationWithNextSpace(location, context, element)
    val fix =
      fix().replace().name("Delete `@$annotationName`").all().with("").range(fixLocation).build()

    val incident =
      Incident(ISSUE, element, location, message, fix).apply {
        if (!annotationContradictsKotlinType) {
          // The annotation is consistent with Kotlin type (but isn't necessary). We'll just make
          // this a warning.
          overrideSeverity(Severity.WARNING)
          // We can also safely apply these fixes in batch mode. Contradictions should probably
          // be examined manually.
          fix.autoFix()
        }
      }
    context.report(incident)
  }

  private fun findKotlinTypeString(annotated: UAnnotated): String? {
    val typeReference =
      when (val sourcePsi = annotated.sourcePsi) {
        is KtParameter -> sourcePsi.typeReference
        is KtProperty -> sourcePsi.typeReference
        is KtNamedFunction -> sourcePsi.typeReference
        else -> null
      }
    return typeReference?.text?.trim()
  }

  private fun findKotlinTypeAnnotation(
    annotated: UAnnotated,
    annotationInfo: AnnotationInfo,
  ): String? {
    //noinspection ExternalAnnotations
    val directAnnotations = annotated.uAnnotations

    // To determine whether a Kotlin type is nullable, we could try to do this at the Kotlin PSI
    // level
    // but UAST already does the heavy lifting here (see
    // org/jetbrains/uast/kotlin/internal/kotlinInternalUastUtils.kt)
    // and maps this to specific JetBrains nullability annotations. We'll just look for these
    // directly on the UAST elements.
    val kotlinNullnessAnnotation: String? =
      directAnnotations
        .filter { it !== annotationInfo.annotation }
        .mapNotNull { it.qualifiedName }
        .firstOrNull { qualifiedName ->
          qualifiedName == IDEA_NOTNULL || qualifiedName == IDEA_NULLABLE
        }
        ?: run {
          if (annotated is UMethod) { // specifically, KotlinUMethod
            // Workaround: KotlinUMethod seems to omit nullness annotations!
            @Suppress("UElementAsPsi", "ExternalAnnotations")
            annotated.annotations
              .mapNotNull { it.qualifiedName }
              .firstOrNull { qualifiedName ->
                qualifiedName == IDEA_NOTNULL || qualifiedName == IDEA_NULLABLE
              }
          } else {
            null
          }
        }
    return kotlinNullnessAnnotation?.substringAfterLast('.', kotlinNullnessAnnotation)
  }

  /**
   * Given a location, returns a location which also consumes the next character if it's whitespace
   * (but not a newline character). This is used such that if we delete the annotation in "@Nullable
   * type"", we end up with "type", not " type".
   */
  private fun locationWithNextSpace(
    location: Location,
    context: JavaContext,
    element: UElement,
  ): Location {
    var fixLocation = location

    // Include whitespace next to annotation
    val doc = context.getContents()
    val end = location.end
    if (doc != null && end != null && end.offset < doc.length) {
      val nextChar = doc[end.offset]
      if (nextChar.isWhitespace() && nextChar != '\n') {
        fixLocation = context.getRangeLocation(element, 0, element, 1)
      }
    }
    return fixLocation
  }
}
