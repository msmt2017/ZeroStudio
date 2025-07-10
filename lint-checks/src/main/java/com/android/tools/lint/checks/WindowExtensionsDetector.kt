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

import com.android.SdkConstants.CONSTRUCTOR_NAME
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.LintBaseline.Companion.symbolsMatch
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.ApiConstraint.Companion.above
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atLeast
import com.android.tools.lint.detector.api.ApiConstraint.Companion.atMost
import com.android.tools.lint.detector.api.ApiConstraint.Companion.below
import com.android.tools.lint.detector.api.ApiConstraint.Companion.exactly
import com.android.tools.lint.detector.api.ApiConstraint.Companion.not
import com.android.tools.lint.detector.api.ApiLevel
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getLongAttribute
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.isKotlin
import com.android.utils.usLocaleCapitalize
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastCallKind
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement

/**
 * Looks for calls to APIs annotated with `@RequiresWindowSdkExtension` without first checking for a
 * compatible `WindowSdkExtensions.extensionVersion` level.
 */
class WindowExtensionsDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(WindowExtensionsDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Accessing an `@RequiresWindowSdkExtension` annotated API without a version check. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "RequiresWindowSdk",
        explanation =
          """
          Some methods in the window library require explicit checks of the \
          `extensionVersion` level:
          ```kotlin
          if (WindowSdkExtensions.getInstance().extensionVersion >= n) {
              val supportedPostures = windowInfoTracker.supportedPostures
              ...
          ```
          This lint check looks for scenarios where you're calling these methods \
          without checking the extension version level, or annotating the calling \
          method with a sufficient `@RequiresWindowSdkExtension` annotation.

          (This lint check does not tackle more advanced ways of version checks, \
          such as extracting the checks into utility methods or constants. Use \
          a direct `if` check as shown above.)
          """,
        briefDescription = "API requires a `WindowSdkExtensions.extensionVersion` check",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val REQUIRES_WINDOW_SDK_EXTENSION = "androidx.window.RequiresWindowSdkExtension"
    private const val WINDOW_SDK_EXTENSIONS_CLASS = "androidx.window.WindowSdkExtensions"
    private const val GET_EXTENSION_VERSION = "getExtensionVersion"
    private const val ATTR_VERSION = "version"
    // Not a real SDK extension, but reusing machinery that can treat it as one
    private const val WINDOWS_SDK_ID = -3
  }

  override fun applicableAnnotations(): List<String> {
    return listOf(REQUIRES_WINDOW_SDK_EXTENSION)
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return when (type) {
      AnnotationUsageType.METHOD_CALL,
      AnnotationUsageType.METHOD_REFERENCE,
      AnnotationUsageType.FIELD_REFERENCE,
      AnnotationUsageType.CLASS_REFERENCE,
      AnnotationUsageType.ANNOTATION_REFERENCE,
      AnnotationUsageType.EXTENDS,
      AnnotationUsageType.DEFINITION -> true
      else -> {
        false
      }
    }
  }

  override fun inheritAnnotation(annotation: String): Boolean {
    return false
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (annotationInfo.origin == AnnotationOrigin.SELF) {
      return
    }

    val annotation = annotationInfo.annotation
    val api = annotation.getSdkLevel() ?: return

    val currentLevel = currentLevel(context.evaluator, element)
    if (currentLevel != null && currentLevel.isAtLeast(api)) {
      return
    }

    val member = usageInfo.referenced as? PsiMember
    val location: Location
    val qualifiedName: String = getQualifiedName(member, element)
    if (
      element is UCallExpression &&
        element.kind != UastCallKind.METHOD_CALL &&
        element.classReference != null
    ) {
      val classReference = element.classReference!!
      location = context.getRangeLocation(element, 0, classReference, 0)
    } else {
      location = context.getNameLocation(element)
    }
    val typeString = getUsageTypePrefix(usageInfo, qualifiedName)

    val currentDesc = if (currentLevel != null) " (current is ${currentLevel.minString()})" else ""
    val message =
      "$typeString requires window SDK extension level ${api.minString()}$currentDesc: `$qualifiedName`"
    context.report(ISSUE, element, context.getLocation(element), message)
  }

  private fun getUsageTypePrefix(usageInfo: AnnotationUsageInfo, qualifiedName: String): String {
    val type =
      when (usageInfo.type) {
        AnnotationUsageType.EXTENDS -> "Extending $qualifiedName"
        AnnotationUsageType.ANNOTATION_REFERENCE,
        AnnotationUsageType.CLASS_REFERENCE -> "Class"
        AnnotationUsageType.METHOD_RETURN,
        AnnotationUsageType.METHOD_OVERRIDE -> "Method"
        AnnotationUsageType.VARIABLE_REFERENCE,
        AnnotationUsageType.FIELD_REFERENCE -> "Field"
        else -> "Call"
      }
    val typeString = type.usLocaleCapitalize()
    return typeString
  }

  private fun getQualifiedName(member: PsiMember?, reference: UElement): String {
    if (member is PsiClass) {
      return member.qualifiedName ?: member.name ?: ""
    } else {
      val containing = member?.containingClass

      val name = member?.name
      if (containing != null) {
        val containingClassName = containing.qualifiedName ?: containing.name ?: ""
        if (name == CONSTRUCTOR_NAME) {
          if (isKotlin(reference.lang)) "$containingClassName()" else "new $containingClassName"
        }
        return "$containingClassName#$name"
      } else {
        return name ?: ""
      }
    }
  }

  private fun UAnnotation.getSdkLevel(): ApiConstraint? {
    return if (qualifiedName == REQUIRES_WINDOW_SDK_EXTENSION) {
      val from = getLongAttribute(this, ATTR_VERSION, -1).toInt()
      atLeast(from, WINDOWS_SDK_ID)
    } else {
      null
    }
  }

  /**
   * Returns the currently inferred SDK level (from things like @RequiresWindowSdkExtension
   * annotations)
   */
  private fun currentLevel(evaluator: JavaEvaluator, element: UElement?): ApiConstraint? {
    var prev = element
    var curr = element
    while (curr != null) {
      if (curr is UAnnotated) {
        //noinspection AndroidLintExternalAnnotations
        for (annotation in curr.uAnnotations) {
          if (annotation.qualifiedName == REQUIRES_WINDOW_SDK_EXTENSION) {
            annotation.getSdkLevel()?.let {
              return it
            }
          }
        }
      }
      if (curr is UFile) {
        // Also consult any package annotations
        val pkg = evaluator.getPackage(curr.javaPsi ?: curr.sourcePsi)
        if (pkg != null) {
          for (psiAnnotation in pkg.annotations) {
            val annotation =
              UastFacade.convertElement(psiAnnotation, null) as? UAnnotation ?: continue
            if (annotation.qualifiedName == REQUIRES_WINDOW_SDK_EXTENSION) {
              annotation.getSdkLevel()?.let {
                return it
              }
            }
          }
        }

        break
      }

      if (curr is UIfExpression) {
        if (prev !== curr.condition) {
          val fromThen = prev == curr.thenExpression
          val condition = curr.condition.skipParenthesizedExprDown()
          if (condition is UBinaryExpression) {
            val constraint = getWindowsExtensionConstraint(condition, evaluator)
            if (constraint != null) {
              return if (fromThen) constraint else constraint.not()
            }
          }
        }
      } else if (curr is UPolyadicExpression) {
        if (curr.operator == UastBinaryOperator.LOGICAL_AND) {
          for (operand in curr.operands) {
            if (operand === curr) {
              break
            } else {
              val operand = operand.skipParenthesizedExprDown()
              if (operand is UBinaryExpression) {
                val constraint = getWindowsExtensionConstraint(operand, evaluator)
                if (constraint != null) {
                  return constraint
                }
              }
            }
          }
        } else if (curr.operator == UastBinaryOperator.LOGICAL_OR) {
          for (operand in curr.operands) {
            if (operand === curr) {
              break
            } else {
              val operand = operand.skipParenthesizedExprDown()
              if (operand is UBinaryExpression) {
                val constraint = getWindowsExtensionConstraint(operand, evaluator)
                if (constraint != null) {
                  return constraint.not()
                } else if (operand.operator == UastBinaryOperator.LOGICAL_OR) {
                  (operand.leftOperand.skipParenthesizedExprDown() as? UBinaryExpression)?.let {
                    getWindowsExtensionConstraint(it, evaluator)?.let { constraint ->
                      return constraint.not()
                    }
                  }
                  (operand.rightOperand.skipParenthesizedExprDown() as? UBinaryExpression)?.let {
                    getWindowsExtensionConstraint(it, evaluator)?.let { constraint ->
                      return constraint.not()
                    }
                  }
                }
              }
            }
          }
        }
      }
      prev = curr
      curr = curr.uastParent ?: break
    }

    return null
  }

  // From "X op Y" to "Y op X" -- e.g. "a > b" = "b < a" and "a >= b" = "b <= a"
  private fun UastBinaryOperator.flip(): UastBinaryOperator? {
    return when (this) {
      UastBinaryOperator.GREATER -> UastBinaryOperator.LESS
      UastBinaryOperator.GREATER_OR_EQUALS -> UastBinaryOperator.LESS_OR_EQUALS
      UastBinaryOperator.LESS_OR_EQUALS -> UastBinaryOperator.GREATER_OR_EQUALS
      UastBinaryOperator.LESS -> UastBinaryOperator.GREATER
      else -> null
    }
  }

  /** Returns the actual API constraint enforced by the given SDK_INT comparison. */
  private fun getWindowsExtensionConstraint(
    binary: UBinaryExpression,
    evaluator: JavaEvaluator,
  ): ApiConstraint? {
    var tokenType = binary.operator
    if (
      tokenType === UastBinaryOperator.GREATER ||
        tokenType === UastBinaryOperator.GREATER_OR_EQUALS ||
        tokenType === UastBinaryOperator.LESS_OR_EQUALS ||
        tokenType === UastBinaryOperator.LESS ||
        tokenType === UastBinaryOperator.EQUALS ||
        tokenType === UastBinaryOperator.IDENTITY_EQUALS ||
        tokenType === UastBinaryOperator.NOT_EQUALS ||
        tokenType === UastBinaryOperator.IDENTITY_NOT_EQUALS
    ) {
      val left = binary.leftOperand
      val level: ApiLevel
      val right: UExpression
      if (!isWindowsExtensionLookup(left, evaluator)) {
        right = binary.rightOperand
        if (!isWindowsExtensionLookup(right, evaluator)) {
          return null
        }
        tokenType = tokenType.flip() ?: tokenType
        level = getApiLevel(left)
      } else {
        right = binary.rightOperand
        level = getApiLevel(right)
      }

      if (level.isValid()) {
        val sdkId = WINDOWS_SDK_ID
        when (tokenType) {
          UastBinaryOperator.GREATER_OR_EQUALS -> return atLeast(level, sdkId)
          UastBinaryOperator.GREATER -> return above(level, sdkId)
          UastBinaryOperator.LESS_OR_EQUALS -> return atMost(level, sdkId)
          UastBinaryOperator.LESS -> return below(level, sdkId)
          UastBinaryOperator.EQUALS,
          UastBinaryOperator.IDENTITY_EQUALS -> return exactly(level, sdkId)
          UastBinaryOperator.NOT_EQUALS,
          UastBinaryOperator.IDENTITY_NOT_EQUALS -> return not(level, sdkId)
          else -> assert(false) { tokenType }
        }
      }
    } else if (tokenType == UastBinaryOperator.LOGICAL_AND) {
      for (operand in binary.operands) {
        val operand = operand.skipParenthesizedExprDown()
        if (operand is UBinaryExpression) {
          val constraint = getWindowsExtensionConstraint(operand, evaluator)
          if (constraint != null) {
            return constraint
          }
        }
      }
    }

    return null
  }

  private fun getApiLevel(element: UExpression?): ApiLevel {
    var level = ApiLevel.NONE
    if (element is UReferenceExpression) {
      val codeName = element.resolvedName
      if (codeName != null) {
        level = ApiLevel.get(codeName, false)
      }
      if (level.isMissing()) {
        val constant = ConstantEvaluator.evaluate(null, element)
        if (constant is Number) {
          level = ApiLevel.get(constant.toInt())
        }
      }
    } else if (element is ULiteralExpression) {
      val value = element.value
      if (value is Int) {
        level = ApiLevel.get(value)
      }
    } else if (element is UParenthesizedExpression) {
      return getApiLevel(element.expression)
    }
    return level
  }

  @Suppress("LintImplUseUast")
  private fun isWindowsExtensionLookup(element: UElement?, evaluator: JavaEvaluator): Boolean {
    if (element is UReferenceExpression) {
      val resolvedName = element.resolvedName
      if (
        GET_EXTENSION_VERSION == resolvedName &&
          (element.resolve() as? PsiMethod)?.containingClass?.qualifiedName ==
            WINDOW_SDK_EXTENSIONS_CLASS
      ) {
        return true
      }
      val selector = element.findSelector()
      if (selector !== element) {
        return isWindowsExtensionLookup(selector, evaluator)
      }
      val resolved = element.resolve()
      if (resolved is ULocalVariable) {
        val initializer = resolved.uastInitializer
        if (initializer != null) {
          return isWindowsExtensionLookup(initializer, evaluator)
        }
      } else if (resolved is PsiVariable) {
        val initializer = resolved.initializer
        initializer?.toUElement()?.let {
          return isWindowsExtensionLookup(it, evaluator)
        }
      }
    } else if (element is UCallExpression) {
      val methodName = getMethodName(element)
      if (
        GET_EXTENSION_VERSION == methodName &&
          element.resolve()?.containingClass?.qualifiedName == WINDOW_SDK_EXTENSIONS_CLASS
      ) {
        return true
      }
    } else if (element is UParenthesizedExpression) {
      return isWindowsExtensionLookup(element.expression, evaluator)
    }
    return false
  }

  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    return symbolsMatch(old, new)
  }
}
