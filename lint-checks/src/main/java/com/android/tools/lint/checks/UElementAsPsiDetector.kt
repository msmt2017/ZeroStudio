/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Context
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
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.TypeConversionUtil.isAssignable
import com.intellij.psi.util.TypeConversionUtil.isNullType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassInitializer
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.resolveToUElementOfType

/**
 * A special check that detects uses of [UElement] as [PsiElement]
 *
 * A Lint check version of [UElementAsPsiInspection] in IntelliJ devkit
 */
class UElementAsPsiDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION =
      Implementation(UElementAsPsiDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "UElementAsPsi",
        briefDescription = "Avoid using UElement as PsiElement",
        explanation =
          """
            Avoid using UAST element as PSI element. \
            If you need to utilize PSI APIs, retrieve the underlying `javaPsi` explicitly. \
            If you need to analyze language-specific information, access `sourcePsi`.
          """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    private const val MSG = "Do not use `UElement` as `PsiElement`"

    private const val NOT_UELEMENT = -2
    private const val NOT_PSI_ELEMENT = -1

    private val ALLOWED_REDEFINITION =
      setOf<String?>(
        UClass::class.java.name,
        UMethod::class.java.name,
        UVariable::class.java.name,
        UClassInitializer::class.java.name,
      )

    private val ALLOWED_PSI_TYPE =
      setOf<String?>(
        // Kotlin modifiers (e.g., inline, suspend, etc.) are quite unique.
        // You may end up with passing common interface and then behave differently.
        // One example is [JavaEvaluator#hasModifier], all of its usages in the evaluator,
        // followed by all the usages in the detectors that check modifiers.
        PsiModifierListOwner::class.java.name
      )
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      UBinaryExpression::class.java,
      UBinaryExpressionWithType::class.java,
      UCallExpression::class.java,
      UVariable::class.java,
      UReturnExpression::class.java,
    )

  private var uElementType: PsiType = PsiTypes.voidType()
  private var psiElementType: PsiType = PsiTypes.voidType()

  override fun beforeCheckRootProject(context: Context) {
    val scope = GlobalSearchScope.projectScope(context.project.ideaProject ?: return)
    psiClassType(UElement::class.java.name, scope)?.let { uElementType = it }
    psiClassType(PsiElement::class.java.name, scope)?.let { psiElementType = it }
  }

  private fun psiClassType(fqn: String, searchScope: GlobalSearchScope): PsiClassType? {
    val project = searchScope.project ?: return null
    return PsiType.getTypeByName(fqn, project, searchScope)
  }

  private val reportedElements = mutableSetOf<PsiElement>()

  override fun afterCheckFile(context: Context) {
    reportedElements.clear()
  }

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitBinaryExpression(node: UBinaryExpression) {
        if (node.operator != UastBinaryOperator.ASSIGN) return
        if (
          getDimIfUElementType(node.rightOperand.getExpressionType()) ==
            getDimIfPsiElementType(node.leftOperand.getExpressionType())
        ) {
          reportUsage(node.rightOperand)
        }
      }

      override fun visitBinaryExpressionWithType(node: UBinaryExpressionWithType) {
        if (
          getDimIfUElementType(node.operand.getExpressionType()) ==
            getDimIfPsiElementType(node.typeReference?.type)
        ) {
          reportUsage(node.operand)
        }
      }

      override fun visitCallExpression(node: UCallExpression) {
        checkReceiver(node)
        checkArguments(node)
      }

      private fun checkReceiver(node: UCallExpression) {
        if (getDimIfUElementType(node.receiverType) == NOT_UELEMENT) return
        val psiMethod = node.resolve() ?: return
        val containingClass = psiMethod.containingClass ?: return
        if (containingClass.qualifiedName in ALLOWED_REDEFINITION) return
        val superMethods = psiMethod.findSuperMethods()
        if (
          (isPsiElementClass(containingClass) ||
            superMethods.any { isPsiElementClass(it.containingClass) }) &&
            superMethods.none { it.containingClass?.qualifiedName in ALLOWED_REDEFINITION }
        ) {
          reportUsage(node)
          return
        }
        val uMethod = node.resolveToUElementOfType<UMethod>() ?: return
        val receiverType = uMethod.getReceiverType() ?: return
        if (
          !isAssignable(uElementType, receiverType) && isAssignable(psiElementType, receiverType)
        ) {
          reportUsage(node.receiver)
        }
      }

      private fun UMethod.getReceiverType(): PsiType? {
        val receiver = this.uastParameters.firstOrNull() ?: return null
        return receiver.typeReference?.type
      }

      private fun checkArguments(node: UCallExpression) {
        for (valueArgument in node.valueArguments) {
          val param = node.getParameterForArgument(valueArgument)
          if (
            getDimIfPsiElementType(param?.type) ==
              getDimIfUElementType(valueArgument.getExpressionType())
          ) {
            reportUsage(valueArgument)
          }
        }
      }

      override fun visitVariable(node: UVariable) {
        if (
          getDimIfUElementType(node.uastInitializer?.getExpressionType()) ==
            getDimIfPsiElementType(node.typeReference?.type)
        ) {
          reportUsage(node.uastInitializer)
        }
      }

      override fun visitReturnExpression(node: UReturnExpression) {
        val expected =
          when (val jt = node.jumpTarget) {
            is UMethod -> jt.returnType
            is ULambdaExpression -> jt.getExpressionType()
            else -> return
          }
        if (
          getDimIfUElementType(node.returnExpression?.getExpressionType()) ==
            getDimIfPsiElementType(expected)
        ) {
          reportUsage(node.returnExpression)
        }
      }

      private fun getDimIfPsiElementType(type: PsiType?): Int {
        val dim = type?.arrayDimensions ?: return NOT_PSI_ELEMENT
        val componentType = type.deepComponentType
        if (componentType.canonicalText in ALLOWED_PSI_TYPE) {
          return NOT_PSI_ELEMENT
        }
        return if (
          !isNullType(type) &&
            isAssignable(psiElementType, componentType) &&
            getDimIfUElementType(componentType) == NOT_UELEMENT
        )
          dim
        else NOT_PSI_ELEMENT
      }

      private fun getDimIfUElementType(type: PsiType?): Int {
        val dim = type?.arrayDimensions ?: return NOT_UELEMENT
        val componentType = type.deepComponentType
        if (componentType.canonicalText in ALLOWED_PSI_TYPE) {
          return NOT_UELEMENT
        }
        return if (!isNullType(type) && isAssignable(uElementType, componentType)) dim
        else NOT_UELEMENT
      }

      private fun isPsiElementClass(cls: PsiClass?): Boolean {
        if (cls == null) return false
        val qualifiedName = cls.qualifiedName ?: return false
        return getDimIfPsiElementType(
          PsiType.getTypeByName(qualifiedName, cls.project, cls.resolveScope)
        ) != NOT_PSI_ELEMENT
      }

      private fun reportUsage(node: UElement?) {
        val sourcePsi = node?.sourcePsi ?: return
        if (!reportedElements.add(sourcePsi)) {
          return
        }
        context.report(ISSUE, node, context.getLocation(node), MSG)
      }
    }
}
