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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator.Companion.EQUALS
import org.jetbrains.uast.UastBinaryOperator.Companion.NOT_EQUALS

/**
 * Looks for uses of the File#endsWith extension from Kotlin which may be trying to look for file
 * extensions
 */
class FileEndsWithDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(FileEndsWithDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Accidentally using File#endsWith to compare just extensions */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "FileEndsWithExt",
        briefDescription = "File endsWith on file extensions",
        explanation =
          """
                The Kotlin extension method `File.endsWith(suffix)` checks whole path components, \
                not just string suffixes. This means that `File("foo.txt").endsWith(".txt")` will return \
                false. Instead you might have intended `file.path.endsWith` or `file.extension.equals`.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableMethodNames(): List<String> = listOf("endsWith")

  override fun getApplicableReferenceNames(): List<String> = listOf("extension")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    if (isFileExtension(context, referenced)) {
      val parent = reference.uastParent ?: return
      if (
        parent is UBinaryExpression && (parent.operator == EQUALS || parent.operator != NOT_EQUALS)
      ) {
        checkExtension(context, parent.rightOperand)
      } else if (parent is UQualifiedReferenceExpression) {
        var curr: UQualifiedReferenceExpression = parent
        while (true) {
          val p = curr.uastParent as? UQualifiedReferenceExpression ?: break
          curr = p
        }
        val selector = curr.selector
        if (
          selector is UCallExpression &&
            selector.methodName == "startsWith" &&
            selector.valueArgumentCount > 0
        ) {
          checkExtension(context, selector.valueArguments.last())
        }
      }
    }
  }

  private fun checkExtension(context: JavaContext, node: UExpression) {
    val string = ConstantEvaluator.evaluateString(context, node, false) ?: return
    if (isExtension(string)) {
      Incident(context)
        .issue(ISSUE)
        .at(node)
        .message(
          "`File.extension` does not include the leading dot; did you mean \"${string.substring(1)}\" ?"
        )
        .report()
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (isFileExtension(context, method) && evaluator.getParameterCount(method) == 2) {
      val string =
        ConstantEvaluator.evaluateString(context, node.valueArguments.last(), false) ?: return
      if (isExtension(string)) {
        val file = (node.receiver as? USimpleNameReferenceExpression)?.identifier ?: "file"
        Incident(context)
          .issue(ISSUE)
          .at(node)
          .message(
            "`File.endsWith` compares whole filenames, not just file extensions; did you mean `$file.path.endsWith(\"$string\")` ?"
          )
          .report()
      }
    }
  }

  private fun isFileExtension(context: JavaContext, member: PsiElement?): Boolean =
    context.evaluator.isMemberInClass(member as? PsiMember) { fqName ->
      fqName == "kotlin.io.FilesKt__UtilsKt" || fqName == "kotlin.io.FilesKt"
    }

  private fun isExtension(s: String?): Boolean {
    return s != null &&
      s.startsWith(".") &&
      s.length <= 10 &&
      !s.startsWith("..") &&
      !s.contains(' ')
  }
}
