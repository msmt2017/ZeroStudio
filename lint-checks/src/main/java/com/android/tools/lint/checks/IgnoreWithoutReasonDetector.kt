/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import java.util.EnumSet
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.uast.UElement

/** It checks that there is a reason defined when using the @Ignored annotation from JUnit. */
class IgnoreWithoutReasonDetector : Detector(), Detector.UastScanner {
  companion object {
    val ALLOW_COMMENT =
      BooleanOption(
        "allow-comments",
        "Whether to allow a comment next to the @Ignore tag to be considered providing a reason",
        true,
        """
                Normally you have to specify an annotation argument to the `@Ignore` \
                annotation, but with this option you can configure whether it should \
                also allow ignore reasons to specified by a comment adjacent to \
                the ignore tag.
                """,
      )

    @JvmField
    val ISSUE =
      Issue.create(
          id = "IgnoreWithoutReason",
          briefDescription = "@Ignore without Reason",
          explanation =
            """
            Ignoring a test without a reason makes it difficult to figure out the problem later. \
            Please define an explicit reason why it is ignored, and when it can be resolved.""",
          category = Category.TESTING,
          priority = 2,
          severity = Severity.WARNING,
          implementation =
            Implementation(
              IgnoreWithoutReasonDetector::class.java,
              EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
            ),
        )
        .setOptions(listOf(ALLOW_COMMENT))
  }

  override fun applicableAnnotations(): List<String> = listOf("org.junit.Ignore")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type == AnnotationUsageType.DEFINITION

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val node = annotationInfo.annotation
    val parent = node.uastParent ?: return
    val source = parent.sourcePsi
    if (source is KtObjectDeclaration && source.isCompanion()) {
      return
    }

    val attribute = node.findAttributeValue(ATTR_VALUE)
    val hasDescription =
      attribute != null &&
        run {
          val value = ConstantEvaluator.evaluate(context, attribute) as? String
          value != null && value.isNotBlank() && value != "TODO"
        }
    if (!hasDescription) {
      if (ALLOW_COMMENT.getValue(context.configuration) && hasComment(node.sourcePsi)) {
        return
      }

      val fix =
        if (attribute == null || node.attributeValues.isEmpty()) {
          fix().name("Give reason").replace().end().with("(\"TODO\")").select("TODO").build()
        } else {
          null
        }
      context.report(
        ISSUE,
        parent,
        context.getLocation(node),
        "Test is ignored without giving any explanation",
        fix,
      )
    }
  }

  /**
   * Returns true if the given annotation element is adjacent (modulo whitespace, as long as the
   * whitespace does not contain a blank line) to a comment
   */
  private fun hasComment(element: PsiElement?): Boolean {
    element ?: return false
    var curr = element.nextSibling
    while (curr is PsiWhiteSpace) {
      if (curr.text.contains("\n")) {
        break
      }
      curr = curr.nextSibling ?: break
    }
    if (curr is PsiComment) {
      return true
    }
    curr = element.prevSibling
    if (curr == null) {
      curr = element.parent.prevSibling
    }
    while (curr is PsiWhiteSpace) {
      if (curr.text.contains("\n\n")) {
        break
      }
      curr = curr.prevSibling ?: break
    }
    return curr is PsiComment
  }
}
