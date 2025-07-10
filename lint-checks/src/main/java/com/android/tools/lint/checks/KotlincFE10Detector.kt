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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Platform.Companion.JDK_SET
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import java.io.File
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression

class KotlincFE10Detector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(KotlincFE10Detector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "KotlincFE10",
        briefDescription = "Avoid using old K1 Kotlin compiler APIs",
        explanation =
          """
                K2, the new version of Kotlin compiler, which encompasses the new frontend, is coming. \
                Try to avoid using internal APIs from the old frontend if possible.
            """,
        category = Category.CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
        enabledByDefault = false,
      )

    // NB: non-trivial to determine classes' containing module
    private fun isAllowed(fqName: String): Boolean =
      when (fqName) {
        // :core:compiler.common
        "org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget" -> true
        "org.jetbrains.kotlin.descriptors.Modality" -> true
        "org.jetbrains.kotlin.descriptors.Visibility" -> true
        // :compiler:frontend.common
        "org.jetbrains.kotlin.resolve.ImportPath" -> true
        // :compiler:backend.common.jvm
        "org.jetbrains.kotlin.resolve.jvm.checkers.DalvikIdentifierUtils" -> true
        else -> {
          when {
            // :core:compiler.common
            fqName.startsWith("org.jetbrains.kotlin.descriptors.EffectiveVisibility") -> true
            fqName.startsWith("org.jetbrains.kotlin.descriptors.Visibilities") -> true
            else -> false
          }
        }
      }

    private fun isFE10Specific(fqName: String): Boolean =
      when {
        isAllowed(fqName) -> false
        fqName.startsWith("org.jetbrains.kotlin.descriptors") -> true
        fqName.startsWith("org.jetbrains.kotlin.load.java.descriptors") -> true
        fqName.startsWith("org.jetbrains.kotlin.resolve") -> true
        fqName.startsWith("org.jetbrains.kotlin.types") -> true
        else -> false
      }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      UCallExpression::class.java,
      UCallableReferenceExpression::class.java,
      UClassLiteralExpression::class.java,
      UParameter::class.java,
      USimpleNameReferenceExpression::class.java,
      UTypeReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      // E.g., BindingClass::class
      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        val fqName = node.type?.canonicalText ?: return
        if (isFE10Specific(fqName)) {
          reportFE10Usage(node, fqName)
        }
      }

      // E.g., ::someUtil
      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        checkFE10Usage(node)
      }

      // E.g., fun foo(p : BindingContext)
      override fun visitParameter(node: UParameter) {
        node.typeReference?.let { visitTypeReferenceExpression(it) }
      }

      // E.g., desc as? <expr>DeclarationDescriptorWithVisibility<expr>
      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkFE10Usage(node)
      }

      // E.g., val bindingContext = ... \n <expr>bindingContext</expr>.get(...)
      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        // Ignore generated/desugared/fake nodes
        if (node.sourcePsi == null) return
        checkFE10Usage(node)
      }

      // E.g., val bindingContext = ... \n bindingContext.<expr>get</expr>(...)
      override fun visitCallExpression(node: UCallExpression) {
        checkFE10Usage(node)
      }

      private fun <T> checkFE10Usage(uElement: T) where T : UResolvable, T : UElement {
        uElement.resolve()?.let { checkFE10Usage(uElement, it) }
      }

      private fun <T : UElement> checkFE10Usage(uElement: T, resolvedElement: PsiElement) {
        when (resolvedElement) {
          is PsiClass -> {
            val fqName = resolvedElement.qualifiedName ?: return
            if (isFE10Specific(fqName)) {
              reportFE10Usage(uElement, fqName)
            }
          }
          is PsiMember -> {
            val containingClass = resolvedElement.containingClass ?: return
            val fqName = containingClass.qualifiedName ?: return
            if (isFE10Specific(fqName)) {
              reportFE10Usage(uElement, fqName)
              return
            }
            if (resolvedElement is PsiMethod) {
              val returnType = resolvedElement.returnType?.canonicalText ?: return
              if (isFE10Specific(returnType)) {
                reportFE10Usage(uElement, returnType)
              }
            }
          }
          is PsiVariable -> {
            val fqName = resolvedElement.type.canonicalText
            if (isFE10Specific(fqName)) {
              reportFE10Usage(uElement, fqName)
            }
          }
        }
      }

      private fun checkFE10Usage(node: UTypeReferenceExpression) {
        val fqName = node.getQualifiedName() ?: return
        if (isFE10Specific(fqName)) {
          reportFE10Usage(node, fqName)
        }
      }

      private fun reportFE10Usage(node: UElement, fqName: String) {
        val location = context.getLocation(node)
        if (isAlreadyReported(location, fqName)) {
          return
        }
        recordReport(location, fqName)
        val message =
          "$fqName appears to be part of the old K1 Kotlin compiler. Avoid using it if possible; K1 will be going away soon."
        context.report(ISSUE, node, location, message)
      }

      // NB: Lint baseline is distinguished by detector ID, message, and location (of file and
      // line).
      // In this detector, message contains FE1.0 entities. So, to avoid reporting a warning that
      // mentions the same kind of FE1.0 entities on the same line, we maintain the history:
      // File, Int (line) -> FqName (type)
      private val reportHistory: MutableMap<ReportKey, MutableSet<String>> = mutableMapOf()

      private fun Location.toReportKey(): ReportKey = file to (start?.line ?: 0)

      private fun isAlreadyReported(location: Location, name: String): Boolean {
        val reportHistoryInFile = reportHistory.getOrPut(location.toReportKey()) { mutableSetOf() }
        return name in reportHistoryInFile
      }

      private fun recordReport(location: Location, name: String) {
        val reportHistoryInFile = reportHistory.getOrPut(location.toReportKey()) { mutableSetOf() }
        reportHistoryInFile.add(name)
      }
    }
}

private typealias ReportKey = Pair<File, Int>
