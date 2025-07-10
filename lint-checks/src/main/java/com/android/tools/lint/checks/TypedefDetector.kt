/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.AndroidXConstants.INT_DEF_ANNOTATION
import com.android.AndroidXConstants.LONG_DEF_ANNOTATION
import com.android.AndroidXConstants.STRING_DEF_ANNOTATION
import com.android.SdkConstants.ANDROIDX_PKG_PREFIX
import com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.isPlatformAnnotation
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.toAndroidxAnnotation
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationBooleanValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.isMinusOne
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import com.intellij.psi.impl.PsiJavaParserFacadeImpl
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSafeQualifiedExpression
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UExpressionList
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastBinaryOperator.Companion.IDENTITY_NOT_EQUALS
import org.jetbrains.uast.UastBinaryOperator.Companion.NOT_EQUALS
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.jetbrains.uast.visitor.AbstractUastVisitor

class TypedefDetector : AbstractAnnotationDetector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> =
    listOf(
      INT_DEF_ANNOTATION.oldName(),
      INT_DEF_ANNOTATION.newName(),
      LONG_DEF_ANNOTATION.oldName(),
      LONG_DEF_ANNOTATION.newName(),
      STRING_DEF_ANNOTATION.oldName(),
      STRING_DEF_ANNOTATION.newName(),

      // Such that the annotation is considered relevant by the annotation handler
      // even if the range check itself is disabled
      INT_RANGE_ANNOTATION.oldName(),
      INT_RANGE_ANNOTATION.newName(),
    )

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean =
    type != AnnotationUsageType.BINARY &&
      type != AnnotationUsageType.DEFINITION &&
      type != AnnotationUsageType.ASSIGNMENT_LHS

  /** Keeps track of which UAST nodes have already been reported by [checkDuplicateAndReport]. */
  private val visitedAnnotationUsages = mutableSetOf<PsiElement>()

  override fun afterCheckFile(context: Context) {
    visitedAnnotationUsages.clear()
  }

  private fun checkDuplicateAndReport(
    context: JavaContext,
    issue: Issue,
    scope: UElement?,
    location: Location,
    message: String,
    quickfixData: LintFix? = null,
  ) {
    // If there are multiple violations at declarations-site, e.g.,
    //
    //   int SHIFT_FLAG = NOT_ALLOWED << NOT_ALLOWED;
    //
    // as well as multiple use-sites, e.g.,
    //
    //   @FlagDef int flags1 = SHIFT_FLAG;
    //   @FlagDef int flags2 = SHIFT_FLAG;
    //
    // either choice---reporting errors on use-site or declaration-site
    // will eventually encounter duplicate reports.
    // Here, make sure we're only checking the same underlying source element once.
    val usagePsi = scope?.sourcePsi ?: return
    if (!visitedAnnotationUsages.add(usagePsi)) return
    report(context, issue, scope, location, message, quickfixData)
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val annotation = annotationInfo.annotation
    when (annotationInfo.qualifiedName) {
      INT_DEF_ANNOTATION.oldName(),
      INT_DEF_ANNOTATION.newName(),
      LONG_DEF_ANNOTATION.oldName(),
      LONG_DEF_ANNOTATION.newName() -> {
        val flagAttribute = getAnnotationBooleanValue(annotation, TYPE_DEF_FLAG_ATTRIBUTE)
        val flag = flagAttribute != null && flagAttribute
        checkTypeDefConstant(context, annotation, element, null, flag, usageInfo)
      }
      STRING_DEF_ANNOTATION.oldName(),
      STRING_DEF_ANNOTATION.newName() -> {
        checkTypeDefConstant(context, annotation, element, null, false, usageInfo)
      }
      INT_RANGE_ANNOTATION.oldName(),
      INT_RANGE_ANNOTATION.newName() -> {} // deliberate no-op
    }
  }

  private fun KaSession.isPrimitiveTypeMethod(
    functionSymbol: KaFunctionSymbol?,
    paramCount: Int,
    nameFilter: (String) -> Boolean,
  ): Boolean {
    if (functionSymbol == null) return false
    with(this) {
      if (!functionSymbol.returnType.isPrimitive) return false
      if (functionSymbol.valueParameters.size > paramCount) return false
      return nameFilter.invoke(
        functionSymbol.callableId?.callableName?.asString() ?: "<no name provided>"
      )
    }
  }

  private fun KaSession.isPrimitiveTypeConvertingMethod(
    functionSymbol: KaFunctionSymbol?
  ): Boolean =
    isPrimitiveTypeMethod(functionSymbol, 0) { name ->
      name.substring(0, 2) == "to" &&
        PsiJavaParserFacadeImpl.getPrimitiveType(name.substring(2).lowercase()) != null
    }

  private fun KaSession.isPrimitiveTypeReturningMethod(functionSymbol: KaFunctionSymbol?): Boolean =
    isPrimitiveTypeMethod(functionSymbol, 1) { name ->
      name == "inv" || name == "and" || name == "or" || name == "xor"
    }

  private fun checkTypeDefConstant(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement?,
    errorNode: UElement?,
    flag: Boolean,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (argument == null) {
      return
    }
    if (argument is ULiteralExpression) {
      val value = argument.value
      if (value == null) {
        // Accepted for @StringDef
        return
      } else if (value is String) {
        checkTypeDefConstant(context, annotation, argument, errorNode, false, value, usageInfo)
      } else if (value is Number) {
        val v = value.toLong()
        if (flag && v == 0L) {
          // Accepted for a flag @IntDef
          return
        }

        checkTypeDefConstant(context, annotation, argument, errorNode, flag, value, usageInfo)
      }
    } else if (isMinusOne(argument)) {
      // -1 is accepted unconditionally for flags
      if (!flag) {
        reportTypeDef(context, annotation, argument, errorNode, usageInfo)
      }
    } else if (argument is UPrefixExpression) {
      if (flag) {
        checkTypeDefConstant(context, annotation, argument.operand, errorNode, true, usageInfo)
      } else {
        val operator = argument.operator
        if (operator === UastPrefixOperator.BITWISE_NOT) {
          checkDuplicateAndReport(
            context,
            TYPE_DEF,
            argument,
            context.getLocation(argument),
            "Flag not allowed here",
          )
        } else if (operator === UastPrefixOperator.UNARY_MINUS) {
          reportTypeDef(context, annotation, argument, errorNode, usageInfo)
        }
      }
    } else if (argument is UParenthesizedExpression) {
      val expression = argument.expression
      checkTypeDefConstant(context, annotation, expression, errorNode, flag, usageInfo)
    } else if (argument is UExpressionList) {
      // If it's ?: then check both the if and else clauses
      for (exp in argument.expressions) {
        checkTypeDefConstant(context, annotation, exp, errorNode, flag, usageInfo)
      }
    } else if (argument is UIfExpression) {
      // Check both the if and else clauses
      argument.thenExpression?.let { thenExpression ->
        checkTypeDefConstant(context, annotation, thenExpression, errorNode, flag, usageInfo)
      }
      argument.elseExpression?.let { elseExpression ->
        checkTypeDefConstant(context, annotation, elseExpression, errorNode, flag, usageInfo)
      }
    } else if (argument is UPolyadicExpression) {
      if (flag) {
        // Allow &'ing with masks
        if (argument.operator === UastBinaryOperator.BITWISE_AND) {
          for (operand in argument.operands) {
            if (operand is UReferenceExpression) {
              val resolvedName = operand.resolvedName
              if (resolvedName != null && resolvedName.contains("mask", true)) {
                return
              }
            }
          }
        }

        for (operand in argument.operands) {
          checkTypeDefConstant(context, annotation, operand, errorNode, true, usageInfo)
        }
      } else {
        val operator = argument.operator
        if (
          operator === UastBinaryOperator.BITWISE_AND ||
            operator === UastBinaryOperator.BITWISE_OR ||
            operator === UastBinaryOperator.BITWISE_XOR
        ) {
          checkDuplicateAndReport(
            context,
            TYPE_DEF,
            argument,
            context.getLocation(argument),
            "Flag not allowed here",
          )
        }
      }
    } else if (argument is UReferenceExpression) {
      val resolved = argument.resolve()
      if (resolved is PsiVariable) {
        if (resolved.type is PsiArrayType) {
          // Allow checking the initializer here even if the field itself
          // isn't final or static; check that the individual values are okay
          checkTypeDefConstant(
            context,
            annotation,
            argument,
            errorNode ?: argument,
            flag,
            resolved,
            usageInfo,
          )
          return
        }

        // If it's a static or final constant, check that it's one of the allowed ones
        if (
          resolved.hasModifierProperty(PsiModifier.STATIC) &&
            resolved.hasModifierProperty(PsiModifier.FINAL)
        ) {
          checkTypeDefConstant(
            context,
            annotation,
            argument,
            errorNode ?: argument,
            flag,
            resolved,
            usageInfo,
          )
        } else {
          val lastAssignment = UastLintUtils.findLastAssignment(resolved, argument)

          if (lastAssignment != null) {
            checkTypeDefConstant(
              context,
              annotation,
              lastAssignment,
              errorNode ?: argument,
              flag,
              usageInfo,
            )
          } else if (
            usageInfo.type != AnnotationUsageType.VARIABLE_REFERENCE &&
              usageInfo.type != AnnotationUsageType.FIELD_REFERENCE &&
              context.evaluator.getAnnotations(resolved, true).any { isAnnotatedWithTypeDef(it) }
          ) {
            checkTypeDefConstant(
              context,
              annotation,
              argument,
              errorNode ?: argument,
              flag,
              resolved,
              usageInfo,
            )
          }
        }
      } else if (resolved is PsiMethod) {
        checkTypeDefConstant(
          context,
          annotation,
          argument,
          errorNode ?: argument,
          flag,
          resolved,
          usageInfo,
        )
      } else if (resolved == null) {
        val sourcePsi =
          when (val psi = argument.sourcePsi) {
            is KtDotQualifiedExpression -> {
              // nonNull.flags.and(...)
              psi.selectorExpression
            }
            is KtSafeQualifiedExpression -> {
              // nullable?.flags?.or(...)
              psi.selectorExpression
            }
            is KtElement -> psi
            else -> null
          }
        if (sourcePsi != null) {
          analyze(sourcePsi) {
            val calleeSymbol = sourcePsi.resolveToCall()?.singleFunctionCallOrNull()?.symbol
            if (
              isPrimitiveTypeConvertingMethod(calleeSymbol) ||
                isPrimitiveTypeReturningMethod(calleeSymbol)
            ) {
              val receiver =
                (argument as? UQualifiedReferenceExpression)?.receiver?.skipParenthesizedExprDown()
              if (receiver != null) {
                // e.g., RECEIVER.toLong(), we should check if RECEIVER is allowed instead.
                checkTypeDefConstant(context, annotation, receiver, receiver, flag, usageInfo)
                val parameterCount = calleeSymbol?.valueParameters?.size ?: -1
                // e.g., RECEIVER.xor(ARGUMENT)
                if (parameterCount == 1) {
                  val callExpression =
                    argument.selector.skipParenthesizedExprDown() as? UCallExpression
                  val callArgument =
                    callExpression?.valueArguments?.firstOrNull()?.skipParenthesizedExprDown()
                  checkTypeDefConstant(
                    context,
                    annotation,
                    callArgument,
                    callArgument,
                    flag,
                    usageInfo,
                  )
                }
              }
              // TODO: how to handle implicit receiver in general?
              // NB: we bail out early to avoid any further false positives.
              return
            }
          }
        }
      }
    } else if (argument is UCallExpression) {
      if (argument.isNewArrayWithInitializer() || argument.isArrayInitializer()) {
        var type = argument.getExpressionType()
        if (type != null) {
          type = type.deepComponentType
        }
        if (PsiTypes.intType() == type || PsiTypes.longType() == type) {
          for (expression in argument.valueArguments) {
            checkTypeDefConstant(context, annotation, expression, errorNode, flag, usageInfo)
          }
        }
      } else {
        val resolved = argument.resolve()
        if (resolved is PsiMethod) {
          checkTypeDefConstant(
            context,
            annotation,
            argument,
            errorNode ?: argument,
            flag,
            resolved,
            usageInfo,
          )
        }
      }
    }
  }

  private fun checkTypeDefConstant(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement,
    errorNode: UElement?,
    flag: Boolean,
    value: Any,
    usageInfo: AnnotationUsageInfo,
  ) {
    val rangeAnnotation = usageInfo.findSameScope { RangeDetector.isIntRange(it.qualifiedName) }
    if (rangeAnnotation != null && value !is PsiField) {
      // Allow @IntRange on this number, but only if it's a literal, not if it's some
      // other (unrelated) constant
      if (
        RangeDetector.getIntRangeError(context, rangeAnnotation.annotation, argument, usageInfo) ==
          null
      ) {
        return
      }
    }

    val allowed = getAnnotationValue(annotation)?.skipParenthesizedExprDown() ?: return

    if (allowed.isArrayInitializer()) {
      // See if we're passing in a variable which itself has been annotated with
      // a typedef annotation; if so, make sure that the typedef constants are the
      // same, or a subset of the allowed constants
      val resolvedArgument =
        when (argument) {
          is UReferenceExpression -> argument.resolve()
          is UCallExpression -> argument.resolve()
          else -> null
        }

      var unmatched: List<Any>? = null
      if (resolvedArgument is PsiModifierListOwner) {
        val evaluator = context.evaluator
        val annotations = evaluator.getAnnotations(resolvedArgument, true)
        var hadTypeDef = false
        for (a in evaluator.filterRelevantAnnotations(annotations, argument)) {
          val qualifiedName = a.qualifiedName
          if (
            INT_DEF_ANNOTATION.isEquals(qualifiedName) ||
              LONG_DEF_ANNOTATION.isEquals(qualifiedName) ||
              STRING_DEF_ANNOTATION.isEquals(qualifiedName)
          ) {
            hadTypeDef = true
            val paramValues = getAnnotationValue(a)?.skipParenthesizedExprDown()
            if (paramValues != null) {
              if (paramValues == allowed) {
                return
              }

              // Superset?
              val provided = getResolvedValues(paramValues, argument)
              val allowedValues = getResolvedValues(allowed, argument)

              // Here we just want to use provided.removeAll(allowedValues).
              // However, we want to treat some fields as
              // equivalent: Class.NAME and ClassCompat.NAME,
              // because AndroidX has duplicated a bunch of platform
              // constants for backwards compatibility purposes
              // and generally placed them in a Compat class.

              for (allowedValue in allowedValues) {
                if (!provided.remove(allowedValue) && allowedValue is PsiField) {
                  val containingClass = allowedValue.containingClass?.name ?: continue
                  val equivalentName =
                    if (containingClass.endsWith(COMPAT_SUFFIX)) {
                      containingClass.removeSuffix(COMPAT_SUFFIX)
                    } else {
                      containingClass + COMPAT_SUFFIX
                    }
                  val fieldName = allowedValue.name
                  provided.removeIf {
                    it is PsiField &&
                      it.name == fieldName &&
                      it.containingClass?.name == equivalentName &&
                      it.containingClass?.qualifiedName?.startsWith(ANDROIDX_PKG_PREFIX) !=
                        allowedValue.containingClass?.qualifiedName?.startsWith(ANDROIDX_PKG_PREFIX)
                  }
                }
              }
              if (provided.isEmpty()) {
                return
              } else if (allowedValues.size > provided.size) {
                // Some overlap: list the unexpected constants
                unmatched = provided
                if (provided.size == 1) {
                  // If there's just a difference of one constant, check to see if we have
                  // a trivial scenario where we've made sure the constant isn't exactly that
                  // value. (This is just checking the most basic scenario; there are a bunch
                  // of ways this comparison be done, by value comparisons, by early returns, by
                  // earlier switch cases etc.)
                  val condition =
                    argument
                      .getParentOfType<UIfExpression>()
                      ?.condition
                      ?.skipParenthesizedExprDown() as? UBinaryExpression
                  if (
                    (condition?.operator == IDENTITY_NOT_EQUALS ||
                      condition?.operator == NOT_EQUALS) &&
                      provided[0] == getResolvedValue(condition.rightOperand, argument)
                  ) {
                    if (condition.leftOperand.asSourceString() == argument.asSourceString()) {
                      return
                    }
                    if (condition.leftOperand.sourcePsi?.text == argument.sourcePsi?.text) {
                      return
                    }
                    //noinspection LintImplPsiEquals
                    if (condition.leftOperand.tryResolve() == value) {
                      return
                    }
                  }
                }
              }
            }
          }
        }

        if (!hadTypeDef && resolvedArgument is PsiMethod) {
          // Called some random method which has not been annotated.
          // Let's peek inside to see if we can figure out more about it; if not,
          // we don't want to flag it since it could get noisy with false
          // positives.
          val uMethod = resolvedArgument.toUElement()
          if (uMethod is UMethod) {
            val body = uMethod.uastBody
            val retValue =
              if (body is UBlockExpression) {
                if (body.expressions.size == 1) {
                  (body.expressions[0].skipParenthesizedExprDown() as? UReturnExpression)
                    ?.returnExpression
                } else {
                  null
                }
              } else {
                body
              }
            if (retValue is UReferenceExpression) {
              // Constant reference
              val const = retValue.resolve() ?: return
              if (const is PsiField) {
                checkTypeDefConstant(
                  context,
                  annotation,
                  retValue,
                  errorNode,
                  flag,
                  const,
                  usageInfo,
                )
              }
              return
            } else if (retValue !is ULiteralExpression) {
              // Not a reference and not a constant literal: some more complicated
              // logic; don't try to flag this for fear of false positives
              return
            }
          }
        }
      }

      val fieldInitialization =
        skipParenthesizedExprUp((argument as? ULiteralExpression)?.uastParent) as? UField
      val initializerExpression = allowed as UCallExpression
      val initializers = initializerExpression.valueArguments
      var psiValue: PsiElement? = null
      if (value is PsiElement) {
        psiValue = value
      }

      for (initializer in initializers) {
        val expression = initializer.skipParenthesizedExprDown()
        // Is this a literal string initialization in a field? If so,
        // see if that field is a member of the allowed constants (e.g.
        // a constant declaration intended to be used in a typedef itself)
        if (fieldInitialization != null && expression is UReferenceExpression) {
          val resolved = expression.resolve()
          if (resolved != null && resolved.isEquivalentTo(fieldInitialization.javaPsi)) {
            return
          }
        }

        if (expression is ULiteralExpression) {
          if (value == expression.value) {
            return
          }
        } else if (psiValue == null) {
          // We're checking here such that we can assume psiValue is not null below
          continue
        } else if (expression is UReferenceExpression) {
          val resolved = expression.resolve()
          if (resolved != null && resolved.isEquivalentTo(psiValue)) {
            return
          }
          val sourcePsi = expression.sourcePsi as? KtElement
          if (resolved == null && sourcePsi != null) {
            analyze(sourcePsi) {
              val calleeSymbol = sourcePsi.resolveToCall()?.singleFunctionCallOrNull()?.symbol
              // e.g., CONST.toLong(), we should compare with CONST, not the entire expression.
              if (
                isPrimitiveTypeConvertingMethod(calleeSymbol) ||
                  isPrimitiveTypeReturningMethod(calleeSymbol)
              ) {
                val receiver =
                  (expression as? UQualifiedReferenceExpression)
                    ?.receiver
                    ?.skipParenthesizedExprDown()
                val resolvedReceiver = (receiver as? UResolvable)?.resolve()
                if (resolvedReceiver != null && resolvedReceiver.isEquivalentTo(psiValue)) {
                  return
                }
              }
            }
          }
        }
      }

      // Check field initializers provided it's not a class field, in which case
      // we'd be reading out literal values which we don't want to do
      if (value is PsiField && rangeAnnotation == null) {
        val initializer = UastFacade.getInitializerBody(value)?.skipParenthesizedExprDown()
        if (
          initializer != null &&
            initializer !is ULiteralExpression &&
            initializer.sourcePsi !is PsiLiteralExpression
        ) {
          checkTypeDefConstant(context, annotation, initializer, errorNode, flag, usageInfo)
          return
        }
      }

      if (annotation.javaPsi is PsiCompiledElement) {
        // If we for some reason have a compiled annotation, don't flag the error
        // since we can't represent IntDef data on these annotations
        return
      }

      // noinspection LintImplPsiEquals
      if (
        value is PsiVariable &&
          argument is UReferenceExpression &&
          argument.resolve() == value &&
          variableIsChecked(argument, value)
      ) {
        return
      }

      reportTypeDef(
        context,
        argument,
        errorNode,
        flag,
        initializers,
        usageInfo,
        annotation,
        unmatched,
      )
    }
  }

  /**
   * For a given variable [reference] (which is declared in [variable] and has an associated typedef
   * annotation), returns true if that variable is "checked" in some way such that the broad typedef
   * may not apply (e.g. it's inside an if statement where we have checked the variable value as
   * part of the condition, or the variable has been reassigned, etc).
   */
  @Suppress("LintImplPsiEquals")
  private fun variableIsChecked(reference: UElement, variable: PsiVariable): Boolean {
    val method = reference.getParentOfType<UMethod>()
    var isChecked = false
    method?.accept(
      object : AbstractUastVisitor() {
        private var foundStart = false
        private var foundTarget = false

        override fun visitVariable(node: UVariable): Boolean {
          if (node.javaPsi == variable) {
            foundStart = true
          }
          return super.visitVariable(node)
        }

        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          if (node == reference) {
            foundTarget = true
          } else if (foundStart && !foundTarget) {
            val resolved = node.resolve()
            if (resolved == variable) {
              var parent = node.uastParent
              if (
                parent is UBinaryExpression && parent.isAssignment() && parent.leftOperand == node
              ) {
                isChecked = true
              } else {
                var prev: UElement = node
                while (parent != null) {
                  if (parent is UIfExpression) {
                    if (prev == parent.condition) {
                      isChecked = true
                      break
                    }
                  } else if (parent is USwitchExpression) {
                    if (prev == parent.expression) {
                      isChecked = true
                      break
                    }
                  }
                  prev = parent
                  parent = parent.uastParent ?: break
                }
              }
            }
          }
          return super.visitSimpleNameReferenceExpression(node)
        }
      }
    )
    return isChecked
  }

  /** Returns PsiFields or constant values (ints or Strings) */
  private fun getResolvedValues(allowed: UExpression, context: UElement): MutableList<Any> {
    if (allowed.isArrayInitializer()) {
      val initializerExpression = allowed as UCallExpression
      val initializers = initializerExpression.valueArguments
      return initializers.mapNotNull { getResolvedValue(it, context) }.toMutableList()
    }
    // TODO -- worry about other types?

    return mutableListOf()
  }

  private fun getResolvedValue(expression: UExpression, context: UElement): Any? {
    return when (expression) {
      is ULiteralExpression -> expression.value
      is UReferenceExpression -> expression.resolve()
      is UParenthesizedExpression -> getResolvedValue(expression.expression, context)
      else -> null
    }
  }

  /** If this element is a literal, return its value. */
  private fun UElement.getLiteralValue(): Any? {
    if (
      this is ULiteralExpression ||
        // -1 shows up as a UPrefixExpression(-, ULiteralExpression(1))
        this is UPrefixExpression && this.operand is ULiteralExpression
    ) {
      return (this as UExpression).evaluate()
    }
    return null
  }

  private fun reportTypeDef(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement,
    errorNode: UElement?,
    usageInfo: AnnotationUsageInfo,
  ) {
    val allowed = getAnnotationValue(annotation)?.skipParenthesizedExprDown()
    if (allowed != null && allowed.isArrayInitializer()) {
      val initializerExpression = allowed as UCallExpression
      val initializers = initializerExpression.valueArguments

      // If the API specifies specific allowed numbers, allow passing in that literal number as well
      val value = argument.getLiteralValue()
      if (value is Number && initializers.any { value == it.getLiteralValue() }) {
        return
      }

      reportTypeDef(context, argument, errorNode, false, initializers, usageInfo, annotation, null)
    }
  }

  private fun reportTypeDef(
    context: JavaContext,
    node: UElement,
    errorNode: UElement?,
    flag: Boolean,
    allowedValues: List<UExpression>,
    usageInfo: AnnotationUsageInfo,
    annotation: UAnnotation,
    unmatched: List<Any>?,
  ) {
    // Allow "0" as initial value in variable expressions
    if (UastLintUtils.isZero(node)) {
      val declaration = node.getParentOfType(UVariable::class.java, true)
      if (declaration != null && node == declaration.uastInitializer?.skipParenthesizedExprDown()) {
        return
      }
    }

    // Some typedef annotations can be specified as "open"; that means they allow
    // other values as well. The typedef is specified to help with things like
    // code completion and documentation.
    if (getAnnotationBooleanValue(annotation, ATTR_OPEN) == true) {
      return
    }

    val values = listAllowedValues(node, allowedValues)
    var message =
      if (flag) {
        "Must be one or more of: $values"
      } else {
        "Must be one of: $values"
      }

    if (
      values == "RecyclerView.HORIZONTAL, RecyclerView.VERTICAL" &&
        errorNode is UResolvable &&
        (errorNode.resolve() as? PsiField)?.containingClass?.name == "LinearLayoutManager"
    ) {
      return
    }

    if (values.startsWith("MediaMetadataCompat.METADATA_KEY_")) {
      // Workaround for 117529548: older libraries didn't ship with open=true
      return
    }

    val rangeAnnotation = usageInfo.findSameScope { RangeDetector.isIntRange(it.qualifiedName) }
    if (rangeAnnotation != null) {
      // Allow @IntRange on this number
      val rangeError =
        RangeDetector.getIntRangeError(context, rangeAnnotation.annotation, node, usageInfo)
      if (rangeError != null && rangeError.isNotEmpty()) {
        message += " or " + Character.toLowerCase(rangeError[0]) + rangeError.substring(1)
      }
    }

    if (unmatched != null && unmatched.isNotEmpty()) {
      message += ", but could be " + listAllowedValues(node, unmatched)
    }

    val locationNode = errorNode ?: node
    val fix: LintFix? = createQuickFix(locationNode, allowedValues, node)
    checkDuplicateAndReport(
      context,
      TYPE_DEF,
      locationNode,
      context.getLocation(locationNode),
      message,
      fix,
    )
  }

  private fun createQuickFix(
    node: UElement,
    values: List<UExpression>,
    context: UElement,
  ): LintFix? {
    var currentValue: Any? = null
    if (node is ULiteralExpression) {
      currentValue = node.value
    } else if (node is UReferenceExpression) {
      val field = node.resolve() as? PsiField
      if (
        field != null &&
          field.hasModifierProperty(PsiModifier.FINAL) &&
          field.hasModifierProperty(PsiModifier.STATIC)
      ) {
        currentValue = field.computeConstantValue()
      }
    }

    val fixes = mutableListOf<LintFix>()
    var foundCurrent = false
    for (value in values) {
      var resolved: PsiElement? = null
      if (value is UReferenceExpression) {
        resolved = value.resolve()
      }
      if (resolved !is PsiField) continue
      val containingClass = resolved.containingClass ?: continue
      val containingClassName = containingClass.name ?: continue
      val qualifiedName: String = containingClass.qualifiedName ?: continue
      val shortName = containingClassName + "." + resolved.name
      val fullName = qualifiedName + "." + resolved.name
      val current = !foundCurrent && value.evaluate() == currentValue
      val fix =
        fix()
          .name("Change to $shortName${if (current) " ($currentValue)" else ""}")
          .replace()
          .all()
          .with(fullName)
          .shortenNames()
          .build()
      if (current) {
        // Place the fix that matches the current value first!
        fixes.add(0, fix)
        foundCurrent = true
      } else if (values.size <= 8) {
        fixes.add(fix)
      }
    }
    if (fixes.isNotEmpty()) {
      return fix().alternatives(*fixes.toTypedArray())
    }
    return null
  }

  private fun listAllowedValues(context: UElement, allowedValues: List<Any>): String {
    val sb = StringBuilder()
    for (allowedValue in allowedValues) {
      var s: String? = null
      var resolved: PsiElement? = null
      when (allowedValue) {
        is UReferenceExpression -> resolved = allowedValue.resolve()
        is PsiField -> resolved = allowedValue
      }
      if (resolved is PsiField) {
        val containingClassName = resolved.containingClass?.name ?: continue
        s = containingClassName + "." + resolved.name
      }
      if (s == null) {
        s =
          when (allowedValue) {
            is UElement -> allowedValue.asSourceString()
            is String -> '"' + allowedValue + '"'
            else -> allowedValue.toString()
          }
      }
      if (sb.isNotEmpty()) {
        sb.append(", ")
      }
      sb.append(s)
    }
    return sb.toString()
  }

  /**
   * Match messages from this detector in the baseline. Over time, the set of constants included by
   * a typedef can change, and these are included in the error message. That makes the baseline
   * messages stop matching in the baselines.
   *
   * To deal with this we don't want to just ignore the constant list; instead, we'll match them as
   * long as the new message contains all the constants in the old message plus some extra ones.
   * This generally works because typedefs tend to add constant, not remove them. We can live with
   * the occasional mismatched baseline messages if an API ever does this since it's rare.
   */
  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    // Make sure the prefix up to ':' matches (e.g. we won't match a change from "Must be one of" to
    // "Must be one or more of", or match with a non-constant-list message like "Flag not allowed
    // here")
    val oldListStart = old.indexOf(": ")
    val newListStart = new.indexOf(": ")
    if (oldListStart != newListStart || !new.regionMatches(0, old, 0, newListStart)) {
      return false
    }

    val oldList = old.substring(oldListStart + 2).split(", ")
    val newList = new.substring(oldListStart + 2).split(", ")

    var j = 0
    for (element in oldList) {
      val oldConstant = element.trim()
      if (j == newList.size) {
        return false
      }
      while (j < newList.size) {
        val newConstant = newList[j++].trim()
        if (oldConstant == newConstant) {
          break
        }
      }
    }

    return true
  }

  companion object {
    private val IMPLEMENTATION = Implementation(TypedefDetector::class.java, Scope.JAVA_FILE_SCOPE)

    // Compat classes in AndroidX are named with this suffix
    private const val COMPAT_SUFFIX = "Compat"

    const val ATTR_OPEN = "open"

    /** Passing the wrong constant to an int or String method. */
    @JvmField
    val TYPE_DEF =
      Issue.create(
        id = "WrongConstant",
        briefDescription = "Incorrect constant",
        explanation =
          """
                Ensures that when parameter in a method only allows a specific set of \
                constants, calls obey those rules.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Returns true if the given [qualifiedName] is one of the typedef annotations. */
    fun isTypeDef(qualifiedName: String?): Boolean {
      qualifiedName ?: return false
      if (
        INT_DEF_ANNOTATION.isEquals(qualifiedName) ||
          LONG_DEF_ANNOTATION.isEquals(qualifiedName) ||
          STRING_DEF_ANNOTATION.isEquals(qualifiedName)
      ) {
        return true
      }
      if (isPlatformAnnotation(qualifiedName)) {
        return isTypeDef(toAndroidxAnnotation(qualifiedName))
      }
      return false
    }

    /** Returns true if this [annotation] is an annotation annotated with `@IntDef` et al. */
    @Suppress("ExternalAnnotations")
    fun isAnnotatedWithTypeDef(annotation: UAnnotation): Boolean {
      return annotation.resolve()?.annotations?.any { resolvedAnnotation ->
        val qualifiedName = resolvedAnnotation.qualifiedName ?: ""
        isTypeDef(qualifiedName)
      } == true
    }

    fun findTypeDef(annotations: List<UAnnotation>): UAnnotation? {
      for (annotation in annotations) {
        if (isTypeDef(annotation.qualifiedName)) {
          return annotation
        }
      }

      return null
    }
  }
}
