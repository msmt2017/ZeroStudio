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

import com.android.SdkConstants.ATTR_NAME
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValue
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.openapi.util.Ref
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiParameterList
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USwitchClauseExpressionWithBody
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

class RequiresFeatureDetector : AbstractAnnotationDetector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> =
    listOf(REQUIRES_FEATURE_ANNOTATION.oldName(), REQUIRES_FEATURE_ANNOTATION.newName())

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val method = usageInfo.referenced as? PsiMethod ?: return

    val type = usageInfo.type
    if (
      type != AnnotationUsageType.METHOD_CALL &&
        type != AnnotationUsageType.METHOD_CALL_CLASS &&
        type != AnnotationUsageType.METHOD_CALL_PACKAGE
    ) {
      return
    }

    val annotation = annotationInfo.annotation
    val name = getAnnotationStringValue(annotation, ATTR_NAME) ?: return
    val reference = getAnnotationStringValue(annotation, ATTR_ENFORCEMENT) ?: return

    val checker = EnforcementChecker(name, reference)
    if (
      !checker.isWithinNameCheckConditional(context.evaluator, element) &&
        !checker.isPrecededByFeatureCheck(element)
    ) {
      context.report(
        REQUIRES_FEATURE,
        element,
        context.getLocation(element),
        "`${method.name}` should only be called if the feature `$name` is " +
          "present; to check call `$reference`",
      )
    }
  }

  class NameLookup(val arguments: List<UExpression>) {
    fun getName(argument: UElement): String? {
      if (argument is UReferenceExpression) {
        val resolved = argument.resolve()
        if (resolved is PsiParameter) {
          val parameterList = PsiTreeUtil.getParentOfType(resolved, PsiParameterList::class.java)
          if (parameterList != null) {
            val index = parameterList.getParameterIndex(resolved)
            if (index != -1 && index < arguments.size) {
              return ConstantEvaluator.evaluateString(null, arguments[index], false)
            }
          }
        }
      }

      return null
    }
  }

  class EnforcementChecker(
    /** The name of the feature to check. */
    private val featureName: String,
    /**
     * javadoc-syntax reference to the checker method; the first string parameter should be the
     * feature name parameter
     */
    enforcement: String,
  ) {
    private val className: String?
    private val methodName: String

    init {
      val paren = enforcement.indexOf('(')
      val argBegin = if (paren != -1) paren else enforcement.length
      val hash = enforcement.indexOf('#')
      val classEnd =
        if (hash != -1) {
          hash
        } else {
          enforcement.lastIndexOf('.', argBegin)
        }
      if (classEnd != -1) {
        className = enforcement.substring(0, classEnd)
        methodName = enforcement.substring(classEnd + 1, argBegin)
      } else {
        className = null
        methodName = enforcement.substring(0, argBegin)
      }
    }

    fun isPrecededByFeatureCheck(element: UElement): Boolean {
      var current = element

      var currentExpression =
        current.getParentOfType<UExpression>(
          UExpression::class.java,
          true,
          UMethod::class.java,
          UClass::class.java,
        )

      while (currentExpression != null) {
        val visitor = FeatureCheckExitFinder(this, current)
        currentExpression.accept(visitor)

        if (visitor.found()) {
          return true
        }

        current = currentExpression

        currentExpression =
          currentExpression.getParentOfType(
            UExpression::class.java,
            true,
            UMethod::class.java,
            UClass::class.java,
          )
      }

      return false
    }

    private class FeatureCheckExitFinder(
      private val enforcement: EnforcementChecker,
      private val endElement: UElement,
    ) : AbstractUastVisitor() {

      private var found = false
      private var done = false

      override fun visitElement(node: UElement): Boolean {
        if (done) {
          return true
        }

        if (node == endElement) {
          done = true
        }

        return done
      }

      override fun visitIfExpression(node: UIfExpression): Boolean {

        if (done) {
          return true
        }

        val thenBranch = node.thenExpression
        val elseBranch = node.elseExpression

        if (thenBranch != null) {
          val level = enforcement.isNameCheckConditional(node.condition, false, null, null)

          if (level != null && level) {
            // See if the body does an immediate return
            if (isUnconditionalReturn(thenBranch)) {
              found = true
              done = true
            }
          }
        }

        if (elseBranch != null) {
          val level = enforcement.isNameCheckConditional(node.condition, true, null, null)

          if (level != null && level) {
            if (isUnconditionalReturn(elseBranch)) {
              found = true
              done = true
            }
          }
        }

        return true
      }

      private fun isUnconditionalReturn(statement: UExpression): Boolean {
        if (statement is UBlockExpression) {
          val expressions = statement.expressions
          if (expressions.size == 1 && expressions[0] is UReturnExpression) {
            return true
          }
        }
        return statement is UReturnExpression
      }

      fun found(): Boolean {
        return found
      }
    }

    @JvmOverloads
    fun isWithinNameCheckConditional(
      evaluator: JavaEvaluator,
      element: UElement,
      nameLookup: NameLookup? = null,
    ): Boolean {
      var current = skipParenthesizedExprUp(element.uastParent)
      var prev = element
      while (current != null) {
        if (current is UIfExpression) {
          val condition = current.condition
          if (prev !== condition) {
            val fromThen = prev == current.thenExpression
            val ok = isNameCheckConditional(condition, fromThen, prev, nameLookup)
            if (ok != null && ok) {
              return true
            }
          }
        } else if (
          current is UPolyadicExpression &&
            (isAndedWithConditional(current, prev) || isOredWithConditional(current, prev))
        ) {
          return true
        } else if (current is USwitchClauseExpressionWithBody) {
          for (condition in current.caseValues) {
            val ok = isNameCheckConditional(condition, true, prev, nameLookup)
            if (ok != null && ok) {
              return true
            }
          }
        } else if (current is UCallExpression && prev is ULambdaExpression) {
          // If the feature method is in a lambda that is passed to a method,
          // see if the lambda parameter is invoked inside that method, wrapped within
          // a suitable feature check conditional.
          //
          // Optionally also see if we're passing in the feature name as a parameter
          // to the function.
          //
          // Algorithm:
          //  (1) Figure out which parameter we're mapping the lambda argument to.
          //  (2) Find that parameter invoked within the function
          //  (3) From the invocation see if it's a suitable feature check conditional
          //

          val call = current
          val method = call.resolve()
          if (method != null) {
            val mapping = evaluator.computeArgumentMapping(call, method)
            val parameter = mapping[prev]
            if (parameter != null) {
              val uMethod = method.toUElementOfType<UMethod>()
              val match = Ref<UCallExpression>()
              val parameterName = parameter.name
              uMethod?.accept(
                object : AbstractUastVisitor() {
                  override fun visitCallExpression(node: UCallExpression): Boolean {
                    val callName = getMethodName(node)
                    if (callName == parameterName) {
                      // Potentially not correct due to scopes, but these lambda
                      // utility methods tend to be short and for lambda function
                      // calls, resolve on call returns null
                      match.set(node)
                    }
                    return super.visitCallExpression(node)
                  }
                }
              )
              val lambdaInvocation = match.get()
              val newApiLookup = NameLookup(call.valueArguments)
              if (
                lambdaInvocation != null &&
                  isWithinNameCheckConditional(evaluator, lambdaInvocation, newApiLookup)
              ) {
                return true
              }
            }
          }
        } else if (current is UMethod || current is UFile) {
          return false
        }
        prev = current
        current = skipParenthesizedExprUp(current.uastParent)
      }

      return false
    }

    private fun isNameCheckConditional(
      element: UElement,
      and: Boolean,
      prev: UElement?,
      nameLookup: NameLookup?,
    ): Boolean? {
      if (element is UPolyadicExpression) {
        val tokenType = element.operator
        if (and && tokenType === UastBinaryOperator.LOGICAL_AND) {
          if (isAndedWithConditional(element, prev)) {
            return true
          }
        } else if (!and && tokenType === UastBinaryOperator.LOGICAL_OR) {
          if (isOredWithConditional(element, prev)) {
            return true
          }
        }
      } else if (element is UCallExpression) {
        return isValidFeatureCheckCall(and, element, nameLookup)
      } else if (element is UReferenceExpression) {
        // Property syntax reference to feature check utility method
        val resolved = element.resolve()
        if (
          resolved is PsiMethod &&
            element is UQualifiedReferenceExpression &&
            element.selector.skipParenthesizedExprDown() is UCallExpression
        ) {
          val call = element.selector.skipParenthesizedExprDown() as UCallExpression
          return isValidFeatureCheckCall(and, call, nameLookup)
        } else if (
          resolved is PsiMethod &&
            element is UQualifiedReferenceExpression &&
            element.receiver.skipParenthesizedExprDown() is UReferenceExpression
        ) {
          // Method call via Kotlin property syntax
          return isValidFeatureCheckCall(and, element, resolved, nameLookup)
        }
      } else if (element is UUnaryExpression) {
        if (element.operator === UastPrefixOperator.LOGICAL_NOT) {
          val operand = element.operand
          val ok = isNameCheckConditional(operand, !and, null, null)
          if (ok != null) {
            return ok
          }
        }
      } else if (element is UParenthesizedExpression) {
        return isNameCheckConditional(element.expression, and, element, nameLookup)
      }
      return null
    }

    private fun isValidFeatureCheckCall(
      and: Boolean,
      call: UCallExpression,
      nameLookup: NameLookup?,
    ): Boolean? {
      val method = call.resolve() ?: return null
      return isValidFeatureCheckCall(and, call, method, nameLookup)
    }

    private fun isValidFeatureCheckCall(
      and: Boolean,
      call: UElement,
      method: PsiMethod,
      nameLookup: NameLookup?,
    ): Boolean? {
      val name = method.name

      if (methodName == name && and) {
        if (className != null) {
          val containingClass = method.containingClass
          val qualifiedName = containingClass?.qualifiedName
          if (qualifiedName != null && !qualifiedName.contains(className)) {
            // Mismatch
            return false
          }
        }

        // Check that the actual name argument matches
        if (call is UCallExpression) {
          val valueArguments = call.valueArguments
          for (first in valueArguments) {
            // The argument position is defined to be the first string parameter
            // of the checker method.
            val expressionType = first.getExpressionType()
            if (expressionType == null || expressionType.canonicalText == JAVA_LANG_STRING) {
              val argString = ConstantEvaluator.evaluateString(null, first, false)
              if (featureName == argString) {
                return true
              } else if (argString == null && nameLookup != null) {
                val level = nameLookup.getName(first)
                return featureName == level
              }
              break
            }
          }
        }

        return false
      }

      // Unconditional feature check utility method? If so just attempt to call it
      if (!method.hasModifierProperty(PsiModifier.ABSTRACT)) {
        val body = UastFacade.getMethodBody(method) ?: return null
        val expressions: List<UExpression> =
          if (body is UBlockExpression) {
            body.expressions
          } else {
            listOf(body)
          }

        if (expressions.size == 1) {
          val statement = expressions[0].skipParenthesizedExprDown()
          var returnValue: UExpression? = null
          @Suppress("SENSELESS_COMPARISON")
          if (statement is UReturnExpression) {
            returnValue = statement.returnExpression?.skipParenthesizedExprDown()
          } else if (statement != null) {
            // Kotlin: may not have an explicit return statement
            returnValue = statement
          }
          if (returnValue != null) {
            val arguments = (call as? UCallExpression)?.valueArguments ?: emptyList()
            if (arguments.isEmpty()) {
              if (
                returnValue is UPolyadicExpression ||
                  returnValue is UCallExpression ||
                  returnValue is UQualifiedReferenceExpression
              ) {
                val isConditional = isNameCheckConditional(returnValue, and, null, null)
                if (isConditional != null) {
                  return isConditional
                }
              }
            } else if (arguments.size == 1) {
              // See if we're passing in a value to the feature check utility method
              val lookup = NameLookup(arguments)
              val ok = isNameCheckConditional(returnValue, and, null, lookup)
              if (ok != null) {
                return ok
              }
            }
          }
        }
      }
      return null
    }

    private fun isOredWithConditional(element: UElement, before: UElement?): Boolean {
      if (element is UBinaryExpression) {
        if (element.operator === UastBinaryOperator.LOGICAL_OR) {
          val left = element.leftOperand

          if (before !== left) {
            var ok = isNameCheckConditional(left, false, null, null)
            if (ok != null) {
              return ok
            }
            val right = element.rightOperand
            ok = isNameCheckConditional(right, false, null, null)
            if (ok != null) {
              return ok
            }
          }
        }
        return false
      } else if (element is UCallExpression) {
        val value = isValidFeatureCheckCall(false, element, null)
        return value != null && value
      } else if (element is UPolyadicExpression) {
        if (element.operator === UastBinaryOperator.LOGICAL_OR) {
          for (operand in element.operands) {
            if (operand == before) {
              break
            } else if (isOredWithConditional(operand, before)) {
              return true
            }
          }
        }
      }

      return false
    }

    private fun isAndedWithConditional(element: UElement, before: UElement?): Boolean {
      if (element is UBinaryExpression) {
        if (element.operator === UastBinaryOperator.LOGICAL_AND) {
          val left = element.leftOperand
          if (before !== left) {
            var ok = isNameCheckConditional(left, true, null, null)
            if (ok != null) {
              return ok
            }
            val right = element.rightOperand
            ok = isNameCheckConditional(right, true, null, null)
            if (ok != null) {
              return ok
            }
          }
        }
        return false
      } else if (element is UCallExpression) {
        val value = isValidFeatureCheckCall(true, element, null)
        return value != null && value
      } else if (element is UPolyadicExpression) {
        if (element.operator === UastBinaryOperator.LOGICAL_AND) {
          for (operand in element.operands) {
            if (operand == before) {
              break
            } else if (isAndedWithConditional(operand, before)) {
              return true
            }
          }
        }
      }

      return false
    }
  }

  companion object {
    const val ATTR_ENFORCEMENT = "enforcement"

    private val IMPLEMENTATION =
      Implementation(RequiresFeatureDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Method result should be used. */
    @JvmField
    val REQUIRES_FEATURE =
      Issue.create(
        id = "RequiresFeature",
        briefDescription = "Requires Feature",
        explanation =
          """
                Some APIs require optional features to be present. This check makes sure that \
                calls to these APIs are surrounded by a check which enforces this.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }
}
