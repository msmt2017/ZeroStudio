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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDoubleColonExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtTreeVisitor
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.ULambdaExpression

/** Makes sure that `buildList` calls actually add items */
class BuildListDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(BuildListDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Missing Add Call */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "BuildListAdds",
        briefDescription = "Missing `add` call in `buildList`",
        explanation =
          """
          The `buildList { }` standard library function is a convenient way to \
          build lists, but you need to actually call `add` on the items.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    private const val BUILD_LIST_OWNER_CLASS_PART =
      "kotlin.collections.CollectionsKt__CollectionsKt"
    private const val BUILD_LIST_OWNER_FACADE = "kotlin.collections.CollectionsKt"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("buildList")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (
      node.valueArgumentCount == 1 &&
        evaluator.isMemberInClass(method) { fqName ->
          fqName == BUILD_LIST_OWNER_CLASS_PART || fqName == BUILD_LIST_OWNER_FACADE
        }
    ) {
      val argument = node.valueArguments[0] as? ULambdaExpression ?: return
      val lambda = argument.sourcePsi as? KtLambdaExpression ?: return
      var isAdding = false
      val literal = lambda.functionLiteral
      lambda.accept(
        @Suppress("LintImplPsiEquals")
        object : KtTreeVisitor<Void>() {
          override fun visitCallExpression(expression: KtCallExpression, data: Void?): Void? {
            // .add(xyz)
            checkImplicitReceiver(expression)
            return super.visitCallExpression(expression, data)
          }

          override fun visitDoubleColonExpression(
            expression: KtDoubleColonExpression,
            data: Void?,
          ): Void? {
            // ::add, this::add, list::add, etc.
            checkImplicitReceiver(expression)
            return super.visitDoubleColonExpression(expression, data)
          }

          private fun checkImplicitReceiver(ktExpression: KtExpression) {
            analyze(ktExpression) {
              val receiverVal = getImplicitReceiverValue(ktExpression)
              val psi = receiverVal?.getImplicitReceiverPsi()
              if (psi == literal) {
                isAdding = true
              }
            }
          }

          override fun visitThisExpression(expression: KtThisExpression, data: Void?): Void? {
            analyze(expression) {
              val reference =
                expression.getTargetLabel()?.mainReference
                  ?: expression.instanceReference.mainReference
              val psi = reference.resolveToSymbol()?.psi
              if (psi == literal) {
                isAdding = true
              }
            }
            return super.visitThisExpression(expression, data)
          }
        }
      )

      if (!isAdding) {
        val message = "No `add` calls within `buildList` lambda; this is usually a mistake"
        context.report(ISSUE, node, context.getNameLocation(node), message)
      }
    }
  }
}
