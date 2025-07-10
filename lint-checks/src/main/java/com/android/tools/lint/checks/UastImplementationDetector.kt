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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Platform.Companion.JDK_SET
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMember
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UImportStatement
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UTypeReferenceExpression

/** A special check that detects uses of UAST implementations */
class UastImplementationDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(UastImplementationDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Use UAST interface, in lieu of UAST implementation */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UastImplementation",
        briefDescription = "Avoid using UAST implementation",
        explanation =
          """
                Use UAST interface whenever possible, and do not rely on UAST implementation, \
                which is subject to change. If language-specific information is needed, \
                the next option is to use PSI directly (though these APIs are less stable and \
                can depend on compiler internals, especially in the case of Kotlin).
            """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    private const val UAST_PREFIX = "org.jetbrains.uast."
    private const val UAST_JAVA_PREFIX = UAST_PREFIX + "java."
    private const val UAST_KT_PREFIX = UAST_PREFIX + "kotlin."

    private fun isUastImplementation(fqName: String): Boolean {
      return fqName.startsWith(UAST_JAVA_PREFIX) || fqName.startsWith(UAST_KT_PREFIX)
    }

    private fun isNotAllowedUastImplementation(fqName: String): Boolean {
      return isUastImplementation(fqName) && !isAllowedUastImplementation(fqName)
    }

    private fun isAllowedUastImplementation(fqName: String): Boolean =
      when (fqName) {
        "org.jetbrains.uast.java.JavaUDeclarationsExpression", // no plugin API to create this
        "org.jetbrains.uast.java.JavaUastLanguagePlugin", // plugin
        "org.jetbrains.uast.kotlin.BaseKotlinUastResolveProviderService", // service
        "org.jetbrains.uast.kotlin.KotlinBinaryExpressionWithTypeKinds", // See below
        "org.jetbrains.uast.kotlin.KotlinBinaryOperators", // no API to retrieve lang-specific op
        "org.jetbrains.uast.kotlin.KotlinPostfixOperators", // we need a consolidated place :\
        "org.jetbrains.uast.kotlin.KotlinQualifiedExpressionAccessTypes", // again
        "org.jetbrains.uast.kotlin.kinds.KotlinSpecialExpressionKinds" // and again
        -> true
        else -> false
      }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      UClassLiteralExpression::class.java,
      UImportStatement::class.java,
      UTypeReferenceExpression::class.java,
    )

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression) {
        val fqName = node.type?.canonicalText ?: return
        if (isNotAllowedUastImplementation(fqName)) {
          reportUastImplementation(node, fqName, (node.type as? PsiClassType)?.resolve())
        }
      }

      override fun visitImportStatement(node: UImportStatement) {
        checkUastImplementation(node)
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        checkUastImplementation(node)
      }

      private fun <T> checkUastImplementation(uElement: T) where T : UResolvable, T : UElement {
        when (val resolvedElement = uElement.resolve()) {
          is PsiClass -> {
            val fqName = resolvedElement.qualifiedName ?: return
            if (isNotAllowedUastImplementation(fqName)) {
              reportUastImplementation(uElement, fqName, resolvedElement)
            }
          }
          is PsiMember -> {
            val containingClass = resolvedElement.containingClass ?: return
            val fqName = containingClass.qualifiedName ?: return
            if (isNotAllowedUastImplementation(fqName)) {
              reportUastImplementation(uElement, fqName, containingClass)
            }
          }
        }
      }

      private fun checkUastImplementation(node: UTypeReferenceExpression) {
        val fqName = node.getQualifiedName() ?: return
        if (isNotAllowedUastImplementation(fqName)) {
          reportUastImplementation(node, fqName, PsiTypesUtil.getPsiClass(node.type))
        }
      }

      private fun reportUastImplementation(node: UElement, fqName: String, psiClass: PsiClass?) {
        val superClasses: Set<PsiClass> =
          psiClass?.let { InheritanceUtil.getSuperClasses(it) } ?: emptySet<PsiClass>()
        val filtered =
          superClasses
            .filter { it.isInterface }
            .filter {
              val qualified = it.qualifiedName ?: ""
              qualified.startsWith(UAST_PREFIX) && !isNotAllowedUastImplementation(qualified)
            }
            .map { it.name }
            .filter { it != "UElement" && it != "UMultiResolvable" }
            .toList()
            .let { LinkedHashSet(it) }
            .toList() // Remove duplicates while preserving order
            .joinToString(", ") { "`$it`" }
        val message =
          "$fqName is UAST implementation. Consider using one of its corresponding UAST interfaces${
            if (filtered.isEmpty()) "." else ": $filtered"
            }"
        context.report(ISSUE, node, context.getLocation(node), message)
      }
    }
}
