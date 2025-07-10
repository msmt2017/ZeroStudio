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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.ANDROID_SDK_ID
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.getAndroidVersionField
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks.Companion.CHECKS_SDK_INT_AT_LEAST_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT_FULL
import com.android.tools.lint.detector.api.VersionChecks.Companion.SDK_INT_VERSION_DATA
import com.android.tools.lint.detector.api.VersionChecks.Companion.getVersionCheckConditional
import com.android.tools.lint.detector.api.VersionChecks.SdkIntAnnotation.Companion.getFieldKey
import com.android.tools.lint.detector.api.VersionChecks.SdkIntAnnotation.Companion.getMethodKey
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UYieldExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isUastChildOf
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.tryResolve

/** Looks for SDK_INT checks and suggests annotating these. */
class SdkIntDetector : Detector(), SourceCodeScanner {
  override fun getApplicableReferenceNames(): List<String> = listOf(SDK_INT, SDK_INT_FULL)

  override fun getApplicableMethodNames(): List<String> =
    listOf("getBuildSdkInt", "getExtensionVersion")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    // Make sure it's android.os.Build.VERSION.SDK_INT, though that's highly likely
    val evaluator = context.evaluator
    if (evaluator.isMemberInClass(referenced as? PsiField, "android.os.Build.VERSION")) {
      val isFull = (referenced as? PsiField)?.name == SDK_INT_FULL
      checkAnnotation(context, reference, isFull = isFull)
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (method.name == "getExtensionVersion") {
      if (!context.evaluator.isMemberInClass(method, "android.os.ext.SdkExtensions")) {
        return
      }
      val first = node.valueArguments.firstOrNull() ?: return
      val sdkId = (ConstantEvaluator.evaluate(context, first) as? Number)?.toInt() ?: return
      checkAnnotation(context, node, sdkId, false)
    } else {
      checkAnnotation(context, node, isFull = false)
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    // Nothing to do here; the partial results are consumed in the VersionChecks lookup
    // along the way
  }

  companion object {
    private fun isLambdaType(context: JavaContext, type: PsiType?): Boolean {
      val rawType = (type as? PsiClassType)?.rawType() ?: return false
      val fqn = rawType.canonicalText
      if (
        fqn == "java.lang.Runnable" ||
          fqn == "java.util.function.Function" ||
          fqn.startsWith("kotlin.jvm.functions.Function")
      ) {
        return true
      }
      val clz = rawType.resolve() ?: return false
      val evaluator = context.evaluator
      return evaluator.implementsInterface(clz, "kotlin.Function", false) ||
        evaluator.implementsInterface(clz, "java.util.function.Function", false) ||
        evaluator.getAnnotation(clz, "java.lang.FunctionalInterface") != null
    }

    private val IMPLEMENTATION = Implementation(SdkIntDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** SDK_INT without @ChecksSdkIntAtLeast. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AnnotateVersionCheck",
        briefDescription = "Annotate SDK_INT checks",
        explanation =
          """
                Methods which perform `SDK_INT` version checks (or field constants which reflect \
                the result of a version check) in libraries should be annotated with \
                `@ChecksSdkIntAtLeast`. This makes it possible for lint to correctly \
                check calls into the library later to correctly understand that problematic \
                code which is wrapped within a call into this library is safe after all.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    fun checkAnnotation(
      context: JavaContext,
      sdkInt: UElement,
      sdkId: Int = ANDROID_SDK_ID,
      isFull: Boolean,
    ) {
      // In app module analysis we always have source access to the
      // check method bodies; don't nag users to annotate these.
      val project = context.project
      if (!project.isLibrary || !project.isAndroidProject) {
        return
      }
      val comparison = sdkInt.getParentOfType(UBinaryExpression::class.java, true)
      if (comparison == null) {
        var parent = skipParenthesizedExprUp(sdkInt.uastParent)
        while (parent is UQualifiedReferenceExpression) {
          parent = skipParenthesizedExprUp(parent.uastParent)
        }
        if (parent is UField) {
          checkFieldAlias(context, parent, sdkId)
        }
        return
      }

      val tokenType = comparison.operator
      if (
        tokenType !== UastBinaryOperator.GREATER &&
          tokenType !== UastBinaryOperator.GREATER_OR_EQUALS
      ) {
        return
      }
      val isGreaterOrEquals = tokenType === UastBinaryOperator.GREATER_OR_EQUALS
      var parent = skipParenthesizedExprUp(comparison.uastParent)
      if (
        sdkId != ANDROID_SDK_ID &&
          parent != null &&
          isRhsInSdkIntComparison(context, comparison, parent)
      ) {
        // Allow SDK_INT > R && getExtensionVersion(...) combination, since getExtensionVersion
        // requires R.
        parent = skipParenthesizedExprUp(parent.uastParent)
      } else if (
        sdkId == ANDROID_SDK_ID &&
          parent is UBinaryExpression &&
          parent.operator == UastBinaryOperator.LOGICAL_AND
      ) {
        val left = parent.leftOperand.skipParenthesizedExprDown()
        val right = parent.rightOperand.skipParenthesizedExprDown()
        if (
          left === comparison &&
            parent.rightOperand.sourcePsi?.text?.contains("SDK_INT_FULL") == true
        ) {
          return
        } else if (right === comparison) {
          // Allow SDK_INT > 36 && SDK_INT_FULL >= combination, since SDK_INT_FULL requires 36.
          parent = skipParenthesizedExprUp(parent.uastParent)
        } else {
          return
        }
      }

      if (parent is UField) {
        checkField(comparison, context, isGreaterOrEquals, parent, sdkId)
        return
      } else if (parent is UReturnExpression) {
        val parentParent = skipParenthesizedExprUp(parent.uastParent)
        if (parentParent is UBlockExpression && parentParent.uastParent is UMethod) {
          val size = parentParent.expressions.size
          if (size == 1) {
            val method = parentParent.uastParent as UMethod
            checkMethod(
              comparison,
              context,
              isGreaterOrEquals,
              method,
              sdkId = sdkId,
              isFull = isFull,
            )
          }
        }
      } else if (parent is UIfExpression) {
        val then =
          (parent.thenExpression as? UBlockExpression)
            ?.expressions
            ?.firstOrNull()
            ?.skipParenthesizedExprDown()
            ?: parent.thenExpression?.skipParenthesizedExprDown()
            ?: return
        val receiver =
          when (then) {
            is UQualifiedReferenceExpression -> then.receiver.skipParenthesizedExprDown()
            is UCallExpression -> then.receiver?.skipParenthesizedExprDown() ?: return
            else -> return
          }

        val parentParent = skipParenthesizedExprUp(parent.uastParent) ?: return
        checkMethod(parentParent, context, receiver, comparison, isGreaterOrEquals, sdkId, isFull)
      } else if (parent is USwitchClauseExpressionWithBody && parent.body.expressions.size == 1) {
        var then = parent.body.expressions[0].skipParenthesizedExprDown()
        @Suppress("UnstableApiUsage")
        if (then is UYieldExpression) {
          // used by UAST to handle some when statements:
          then = then.expression?.skipParenthesizedExprDown() ?: return
        }
        val receiver =
          when (then) {
            is UQualifiedReferenceExpression -> then.receiver.skipParenthesizedExprDown()
            is UCallExpression -> then.receiver?.skipParenthesizedExprDown() ?: return
            else -> return
          }
        val switchExpression = parent.getParentOfType(USwitchExpression::class.java)
        val parentParent = skipParenthesizedExprUp(switchExpression?.uastParent) ?: return
        if (!parent.caseValues.any { it.isUastChildOf(comparison) }) {
          return
        }
        checkMethod(parentParent, context, receiver, comparison, isGreaterOrEquals, sdkId, isFull)
      }
    }

    /**
     * For a given binary operator node, checks whether it's on the right hand side of another
     * binary operator, &&, with the left hand side being an SDK_INT check.
     */
    private fun isRhsInSdkIntComparison(
      context: JavaContext,
      comparison: UBinaryExpression,
      parent: UElement,
    ): Boolean {
      val outer = parent as? UBinaryExpression ?: return false
      if (outer.operator != UastBinaryOperator.LOGICAL_AND) return false
      if (parent.rightOperand.skipParenthesizedExprDown() !== comparison) return false
      val leftOperand = parent.leftOperand.skipParenthesizedExprDown()
      val leftComparison = leftOperand as? UBinaryExpression ?: return false
      val client = context.client
      val evaluator = context.evaluator
      val project = context.project
      return getVersionCheckConditional(leftComparison, client, evaluator, project, null) != null
    }

    private fun checkMethod(
      parentParent: UElement,
      context: JavaContext,
      receiver: UExpression,
      comparison: UBinaryExpression,
      isGreaterOrEquals: Boolean,
      sdkId: Int,
      isFull: Boolean,
    ) {
      val method: UMethod =
        if (parentParent is UReturnExpression) {
          parentParent.uastParent as? UMethod
            ?: parentParent.uastParent?.uastParent as? UMethod
            ?: return
        } else if (parentParent is UBlockExpression && parentParent.uastParent is UMethod) {
          val expressions = parentParent.expressions
          if (
            expressions.size == 1 ||
              expressions.size == 2 &&
                expressions[1].skipParenthesizedExprDown() is UReturnExpression
          ) {
            parentParent.uastParent as? UMethod
              ?: parentParent.uastParent?.uastParent as? UMethod
              ?: return
          } else {
            return
          }
        } else {
          return
        }

      checkMethod(context, method, receiver, comparison, isGreaterOrEquals, sdkId, isFull)
    }

    private fun checkMethod(
      context: JavaContext,
      method: UMethod,
      receiver: UExpression,
      comparison: UBinaryExpression,
      isGreaterOrEquals: Boolean,
      sdkId: Int,
      isFull: Boolean,
    ) {
      val parameter = receiver.tryResolve() as? PsiParameter ?: return
      val index = getParameterIndex(parameter)
      if (index != -1 && isLambdaType(context, parameter.type)) {
        checkMethod(comparison, context, isGreaterOrEquals, method, sdkId, index, isFull)
      }
    }

    private fun getParameterIndex(parameter: PsiParameter): Int {
      val parameterList = parameter.parent as? PsiParameterList ?: return -1
      return parameterList.getParameterIndex(parameter)
    }

    private fun checkMethod(
      comparison: UBinaryExpression,
      context: JavaContext,
      isGreaterOrEquals: Boolean,
      method: UMethod,
      sdkId: Int,
      lambda: Int = -1,
      isFull: Boolean,
    ) {
      if (!context.evaluator.isPublic(method)) {
        return
      }
      val apiOperand = comparison.rightOperand.skipParenthesizedExprDown()
      val apiValue = apiOperand.evaluate() ?: ConstantEvaluator.evaluate(context, apiOperand)
      val api = apiValue as? Int
      if (api != null) {
        val apiAtLeast = if (isGreaterOrEquals) api else api + 1
        if (!annotated(context, method, apiAtLeast)) {
          val buildCode =
            getBuildCode(apiAtLeast, sdkId, if (isGreaterOrEquals) apiOperand else null)
          val location = context.getNameLocation(method).withOriginalSource(method)
          val args =
            "api=$buildCode${if (lambda != -1) ", lambda=$lambda" else ""}${if (sdkId != ANDROID_SDK_ID)", extension=${getSdkConstant(context, sdkId)}" else ""}"
          val message = "This method should be annotated with `@ChecksSdkIntAtLeast($args)`"
          val fix = createAnnotationFix(context, method, args)
          context.report(ISSUE, method, location, message, fix)

          if (!context.isGlobalAnalysis()) {
            // Store data for VersionChecks used by for example ApiDetector
            val methodDesc = getMethodKey(context.evaluator, method)
            val map = context.getPartialResults(SDK_INT_VERSION_DATA).map()
            // See VersionChecks#isKnownVersionCheck
            map.put(
              methodDesc,
              "api=$apiAtLeast${if (lambda != -1) ",lambda=$lambda" else ""}${if (sdkId != ANDROID_SDK_ID)", extension=$sdkId" else ""}",
            )
          }
        }
      } else if (apiOperand is UReferenceExpression) {
        if (isFull) {
          // For plain SDK_INT checks we suggest using @ChecksSdkIntAtLeast(parameter=0),
          // but if the API level is passed in via a lambda, we need to indicate to the
          // caller whether it's a plain API level or a packed (full) API level; we can't
          // express that today.
          return
        }
        val parameter = apiOperand.resolve()
        if (parameter is PsiParameter) {
          val index = getParameterIndex(parameter)
          if (index != -1 && !annotated(context, method, -1)) {
            val args =
              "parameter=$index${if (lambda != -1) ", lambda=$lambda" else ""}${if (sdkId != ANDROID_SDK_ID)", extension=${getSdkConstant(context, sdkId)}" else ""}"
            val message = "This method should be annotated with `@ChecksSdkIntAtLeast($args)`"
            val location = context.getNameLocation(method).withOriginalSource(method)
            val fix = createAnnotationFix(context, method, args)
            context.report(ISSUE, method, location, message, fix)

            if (!context.isGlobalAnalysis()) {
              // Store data for the Version Check, used from ApiDetector etc
              val methodDesc = getMethodKey(context.evaluator, method)
              val map = context.getPartialResults(SDK_INT_VERSION_DATA).map()
              map.put(
                methodDesc,
                "parameter=$index${if (lambda != -1) ",lambda=$lambda" else ""}${if (sdkId != ANDROID_SDK_ID)", extension=$sdkId" else ""}",
              )
            }
          }
        }
      }
    }

    private fun createAnnotationFix(
      context: JavaContext,
      element: UElement,
      args: String,
    ): LintFix? {
      // if not on classpath (older annotation library) don't suggest annotating
      if (context.evaluator.findClass(CHECKS_SDK_INT_AT_LEAST_ANNOTATION) == null) return null

      return LintFix.create()
        .annotate(
          "$CHECKS_SDK_INT_AT_LEAST_ANNOTATION($args)",
          context = context,
          element = element.sourcePsi,
        )
        .build()
    }

    private fun getSdkConstant(context: JavaContext, sdkId: Int): String {
      return ApiLookup.getSdkExtensionField(context, sdkId, true)
    }

    private fun getBuildCode(api: Int, sdkId: Int, constant: UElement?): String {
      val text = (constant as? UReferenceExpression)?.sourcePsi?.text
      if (text != null) {
        return text
      }
      if (sdkId != ANDROID_SDK_ID) {
        return api.toString()
      }
      return getAndroidVersionField(api, true)
    }

    private fun checkField(
      comparison: UBinaryExpression,
      context: JavaContext,
      isGreaterOrEquals: Boolean,
      field: UField,
      sdkId: Int,
    ) {
      if (!context.evaluator.isPublic(field)) {
        return
      }
      val apiOperand = comparison.rightOperand.skipParenthesizedExprDown()
      val value = apiOperand.evaluate() ?: ConstantEvaluator.evaluate(context, apiOperand)
      val api = value as? Int ?: return
      val atLeast = if (isGreaterOrEquals) api else api + 1
      if (!annotated(context, field, atLeast)) {
        val buildCode = getBuildCode(atLeast, sdkId, if (isGreaterOrEquals) apiOperand else null)
        val args =
          "api=$buildCode${if (sdkId != ANDROID_SDK_ID)", extension=${getSdkConstant(context, sdkId)}" else ""}"
        val message = "This field should be annotated with `ChecksSdkIntAtLeast($args)`"
        val location = context.getNameLocation(field).withOriginalSource(field)
        val fix = createAnnotationFix(context, field, args)
        context.report(ISSUE, field, location, message, fix)

        if (!context.isGlobalAnalysis()) {
          // Store data for VersionChecks used by for example ApiDetector
          val fieldDesc = getFieldKey(context.evaluator, field)
          val map = context.getPartialResults(SDK_INT_VERSION_DATA).map()
          map.put(
            fieldDesc,
            "api=$atLeast${if (sdkId != ANDROID_SDK_ID)", extension=$sdkId" else ""}",
          )
        }
      }
    }

    private fun checkFieldAlias(context: JavaContext, field: UField, sdkId: Int) {
      if (context.evaluator.isPublic(field) && !annotated(context, field, -1)) {
        val args = "extension=${getSdkConstant(context, sdkId)}"
        val message = "This field should be annotated with `ChecksSdkIntAtLeast($args)`"
        val location = context.getNameLocation(field).withOriginalSource(field)
        val fix = createAnnotationFix(context, field, args)
        context.report(ISSUE, field, location, message, fix)

        if (!context.isGlobalAnalysis()) {
          // Store data for VersionChecks used by for example ApiDetector
          val fieldDesc = getFieldKey(context.evaluator, field)
          val map = context.getPartialResults(SDK_INT_VERSION_DATA).map()
          map.put(fieldDesc, "extension=$sdkId")
        }
      }
    }

    private fun annotated(context: JavaContext, annotated: UAnnotated, api: Int): Boolean {
      // TODO: If annotated, warn if it's not set to the correct API level
      return context.evaluator.getAllAnnotations(annotated, false).any {
        it.qualifiedName == CHECKS_SDK_INT_AT_LEAST_ANNOTATION
      }
    }
  }
}
