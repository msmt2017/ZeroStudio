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
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_LHS
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_RHS
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_CALL_PARAMETER
import com.android.tools.lint.detector.api.AnnotationUsageType.METHOD_RETURN
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isBelow
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UResolvable
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer

class RangeDetector : AbstractAnnotationDetector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> =
    listOf(
      INT_RANGE_ANNOTATION.oldName(),
      INT_RANGE_ANNOTATION.newName(),
      FLOAT_RANGE_ANNOTATION.oldName(),
      FLOAT_RANGE_ANNOTATION.newName(),
      SIZE_ANNOTATION.oldName(),
      SIZE_ANNOTATION.newName(),

      // Such that the annotation is considered relevant by the annotation handler
      // even if the typedef check itself is disabled
      INT_DEF_ANNOTATION.oldName(),
      INT_DEF_ANNOTATION.newName(),
      LONG_DEF_ANNOTATION.oldName(),
      LONG_DEF_ANNOTATION.newName(),

      // Consider including org.jetbrains.annotations.Range here, but be careful
      // such that we don't end up with a double set of warnings in the IDE (one
      // from Lint, one from IntelliJ's support for this annotation)
    )

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType) =
    when (type) {
      METHOD_CALL_PARAMETER,
      ASSIGNMENT_RHS,
      METHOD_RETURN -> true
      else -> false
    }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val annotation = annotationInfo.annotation
    when (annotationInfo.qualifiedName) {
      INT_RANGE_ANNOTATION.oldName(),
      INT_RANGE_ANNOTATION.newName() -> {
        checkIntRange(context, annotation, element, usageInfo)
      }
      FLOAT_RANGE_ANNOTATION.oldName(),
      FLOAT_RANGE_ANNOTATION.newName() -> {
        checkFloatRange(context, annotation, element, usageInfo)
      }
      SIZE_ANNOTATION.oldName(),
      SIZE_ANNOTATION.newName() -> {
        checkSize(context, annotation, element, usageInfo)
      }
      INT_DEF_ANNOTATION.oldName(),
      INT_DEF_ANNOTATION.newName(),
      LONG_DEF_ANNOTATION.oldName(),
      LONG_DEF_ANNOTATION.newName() -> {}
    }
  }

  private fun checkIntRange(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (argument is UIfExpression) {
      argument.thenExpression?.let { thenExpression ->
        checkIntRange(context, annotation, thenExpression, usageInfo)
      }
      argument.elseExpression?.let { elseExpression ->
        checkIntRange(context, annotation, elseExpression, usageInfo)
      }
      return
    } else if (argument is UParenthesizedExpression) {
      checkIntRange(context, annotation, argument.expression, usageInfo)
      return
    }

    val message = getIntRangeError(context, annotation, argument, usageInfo)
    if (message != null) {
      if (usageInfo.anySameScope { TypedefDetector.isTypeDef(it.qualifiedName) }) {
        // Don't flag int range errors if there is an int def annotation there too;
        // there could be a valid @IntDef constant. (The @IntDef check will
        // perform range validation by calling getIntRange.)
        return
      }

      report(context, RANGE, argument, context.getLocation(argument), message)
    }
  }

  private fun checkFloatRange(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement,
    usageInfo: AnnotationUsageInfo,
  ) {
    if (argument is UIfExpression) {
      argument.thenExpression?.let { thenExpression ->
        checkFloatRange(context, annotation, thenExpression, usageInfo)
      }
      argument.elseExpression?.let { elseExpression ->
        checkFloatRange(context, annotation, elseExpression, usageInfo)
      }
      return
    } else if (argument is UParenthesizedExpression) {
      checkFloatRange(context, annotation, argument.expression, usageInfo)
      return
    }

    if (argument.isNewArrayWithDimensions()) {
      return
    }

    val constraint = FloatRangeConstraint.create(annotation)

    val constant = ConstantEvaluator.evaluate(context, argument)
    if (constant !is Number) {
      // Number arrays
      if (
        constant is FloatArray ||
          constant is DoubleArray ||
          constant is IntArray ||
          constant is LongArray
      ) {
        if (constant is FloatArray) {
          for (value in constant) {
            if (!constraint.isValid(value.toDouble())) {
              val message = constraint.describe(value.toDouble())
              report(context, RANGE, argument, context.getLocation(argument), message)
              return
            }
          }
        }
        // Kinda repetitive but primitive arrays are not related by subtyping
        if (constant is DoubleArray) {
          for (value in constant) {
            if (!constraint.isValid(value)) {
              val message = constraint.describe(value)
              report(context, RANGE, argument, context.getLocation(argument), message)
              return
            }
          }
        }
        if (constant is IntArray) {
          for (value in constant) {
            if (!constraint.isValid(value.toDouble())) {
              val message = constraint.describe(value.toDouble())
              report(context, RANGE, argument, context.getLocation(argument), message)
              return
            }
          }
        }
        if (constant is LongArray) {
          for (value in constant) {
            if (!constraint.isValid(value.toDouble())) {
              val message = constraint.describe(value.toDouble())
              report(context, RANGE, argument, context.getLocation(argument), message)
              return
            }
          }
        }
      }

      // Try to resolve it; see if there's an annotation on the variable/parameter/field
      if (argument is UResolvable) {
        val referenceConstraint = getRangeConstraint(context, argument)
        if (referenceConstraint != null) {
          val here = RangeConstraint.create(annotation)
          val error = getNonOverlapMessage(here, referenceConstraint, argument, usageInfo)
          if (error != null) {
            report(context, RANGE, argument, context.getLocation(argument), error)
          }
        }
      }

      return
    }

    val value = constant.toDouble()
    if (!constraint.isValid(value)) {
      val message = constraint.describe(argument as? UExpression, value)
      report(context, RANGE, argument, context.getLocation(argument), message)
    }
  }

  private fun checkSize(
    context: JavaContext,
    annotation: UAnnotation,
    argument: UElement,
    usageInfo: AnnotationUsageInfo,
  ) {
    val actual: Long
    var isString = false

    // TODO: Collections syntax, e.g. Arrays.asList ⇒ param count, emptyList=0, singleton=1, etc
    // TODO: Flow analysis

    if (argument.isNewArrayWithInitializer()) {
      actual = (argument as UCallExpression).valueArgumentCount.toLong()
    } else if (argument is UIfExpression) {
      argument.thenExpression?.let { thenExpression ->
        checkSize(context, annotation, thenExpression, usageInfo)
      }
      argument.elseExpression?.let { elseExpression ->
        checkSize(context, annotation, elseExpression, usageInfo)
      }
      return
    } else if (argument is UParenthesizedExpression) {
      checkSize(context, annotation, argument.expression, usageInfo)
      return
    } else {
      val `object` = ConstantEvaluator.evaluate(context, argument)
      // Check string length
      if (`object` is String) {
        actual = `object`.length.toLong()
        isString = true
      } else {
        actual = ConstantEvaluator.getArraySize(`object`).toLong()
        if (actual == -1L) {
          // Try to resolve it; see if there's an annotation on the variable/parameter/field
          if (argument is UResolvable) {
            val constraint = getRangeConstraint(context, argument)
            if (constraint != null) {
              val here = RangeConstraint.create(annotation)
              val error = getNonOverlapMessage(here, constraint, argument, usageInfo)
              if (error != null) {
                report(context, RANGE, argument, context.getLocation(argument), error)
              }
            }
          }

          return
        }
      }
    }

    val constraint = SizeConstraint.create(annotation)
    if (!constraint.isValid(actual)) {
      val unit =
        if (isString) {
          "length"
        } else {
          "size"
        }

      val message = constraint.describe(argument as? UExpression, unit, actual)
      report(context, RANGE, argument, context.getLocation(argument), message)
    }
  }

  companion object {
    private val IMPLEMENTATION = Implementation(RangeDetector::class.java, Scope.JAVA_FILE_SCOPE)

    private const val AOSP_INT_RANGE_ANNOTATION = "android.annotation.IntRange"

    /** Returns true if the given [qualifiedName] is a range annotation */
    fun isIntRange(qualifiedName: String?): Boolean {
      if (
        INT_RANGE_ANNOTATION.isEquals(qualifiedName) || AOSP_INT_RANGE_ANNOTATION == qualifiedName
      ) {
        return true
      }
      return false
    }

    fun getIntRangeError(
      context: JavaContext,
      annotation: UAnnotation,
      argument: UElement,
      usageInfo: AnnotationUsageInfo,
    ): String? {
      if (argument.isNewArrayWithInitializer()) {
        val newExpression = argument as UCallExpression
        for (topExpression in newExpression.valueArguments) {
          val expression = topExpression.skipParenthesizedExprDown() ?: continue
          val error = getIntRangeError(context, annotation, expression, usageInfo)
          if (error != null) {
            return error
          }
        }
      } else if (argument.isNewArrayWithDimensions()) {
        return null
      }

      val constraint = IntRangeConstraint.create(annotation)

      val o = ConstantEvaluator.evaluate(context, argument)
      if (o !is Number) {
        // Number arrays
        if (o is IntArray || o is LongArray) {
          if (o is IntArray) {
            for (value in o) {
              if (!constraint.isValid(value.toLong())) {
                return constraint.describe(value.toLong())
              }
            }
          }
          if (o is LongArray) {
            for (value in o) {
              if (!constraint.isValid(value)) {
                return constraint.describe(value)
              }
            }
          }
        }

        // Try to resolve it; see if there's an annotation on the variable/parameter/field
        if (argument is UResolvable) {
          val referenceConstraint = getRangeConstraint(context, argument)
          if (referenceConstraint != null && usageInfo.type != ASSIGNMENT_LHS) {
            val here = RangeConstraint.create(annotation)
            val error = getNonOverlapMessage(here, referenceConstraint, argument, usageInfo)
            if (error != null) {
              return error
            }
          }
        }

        return null
      }

      val value = o.toLong()
      return if (!constraint.isValid(value)) {
        constraint.describe(value)
      } else null
    }

    private fun getRangeConstraint(
      context: JavaContext,
      resolvable: UResolvable?,
    ): RangeConstraint? {
      val resolved = resolvable?.resolve() ?: return null
      // TODO: What about parameters or local variables here?
      // UAST-wise we could look for UDeclaration but it turns out
      // UDeclaration also extends PsiModifierListOwner!
      val constraint =
        (resolved.toUElement() as? UAnnotated)?.let {
          RangeConstraint.create(it, context.evaluator)
        }
          ?: if (resolved is PsiModifierListOwner)
            RangeConstraint.create(resolved, context.evaluator)
          else null

      if (resolvable is USimpleNameReferenceExpression) {
        val surroundingIf = resolvable.getParentOfType<UIfExpression>(true)
        if (surroundingIf != null) {
          val condition = surroundingIf.condition.skipParenthesizedExprDown()
          val newConstraint =
            getRangeConstraints(resolvable, condition, constraint) ?: return constraint
          newConstraint.inferred = true
          val elseExpression = surroundingIf.elseExpression
          if (elseExpression != null && resolvable.isBelow(elseExpression)) {
            if (constraint != null) {
              return constraint.remove(newConstraint)
            }
            return null
          }
          return if (constraint != null) {
            constraint and newConstraint
          } else {
            newConstraint
          }
        }
      }

      return constraint
    }

    private fun getRangeConstraints(
      resolvable: USimpleNameReferenceExpression,
      condition: UExpression?,
      previousConstraint: RangeConstraint?,
    ): RangeConstraint? {
      if (condition !is UBinaryExpression) return null
      val operator = condition.operator
      if (operator == UastBinaryOperator.LOGICAL_AND) {
        val left =
          getRangeConstraints(
            resolvable,
            condition.leftOperand.skipParenthesizedExprDown(),
            previousConstraint,
          )
        val right =
          getRangeConstraints(
            resolvable,
            condition.rightOperand.skipParenthesizedExprDown(),
            previousConstraint,
          )
        return when {
          left == null && right == null -> null
          left != null && right != null -> right and left
          else -> left ?: right
        }
      } else if (operator == UastBinaryOperator.LOGICAL_OR) {
        return null
      }
      val lhs = condition.leftOperand

      //noinspection LintImplPsiEquals
      if (lhs.tryResolve() != resolvable.resolve()) {
        return null
      }
      val value = condition.rightOperand.evaluate() as? Number ?: return null

      if (value is Float || value is Double) {
        val number = value.toDouble()
        if (number == Double.POSITIVE_INFINITY || number == Double.NEGATIVE_INFINITY) {
          return null
        }
        return when (operator) {
          UastBinaryOperator.GREATER -> FloatRangeConstraint.greaterThan(number)
          UastBinaryOperator.GREATER_OR_EQUALS -> FloatRangeConstraint.atLeast(number)
          UastBinaryOperator.LESS -> FloatRangeConstraint.lessThan(number)
          UastBinaryOperator.LESS_OR_EQUALS -> FloatRangeConstraint.atMost(number)
          UastBinaryOperator.EQUALS,
          UastBinaryOperator.IDENTITY_EQUALS -> FloatRangeConstraint.range(number, number)
          // We can't handle NOT_EQUALS / IDENTITY_NOT_EQUALS since we only support single segment
          // continuous ranges for now
          else -> return null
        }
      } else {
        val number = value.toLong()
        if (number == Long.MIN_VALUE || number == Long.MAX_VALUE) {
          return null
        }
        return when (operator) {
          UastBinaryOperator.GREATER -> IntRangeConstraint.atLeast(number)
          UastBinaryOperator.GREATER_OR_EQUALS -> IntRangeConstraint.atLeast(number)
          UastBinaryOperator.LESS -> IntRangeConstraint.atMost(number)
          UastBinaryOperator.LESS_OR_EQUALS -> IntRangeConstraint.atMost(number)
          UastBinaryOperator.EQUALS,
          UastBinaryOperator.IDENTITY_EQUALS -> IntRangeConstraint.range(number, number)
          UastBinaryOperator.NOT_EQUALS,
          UastBinaryOperator.IDENTITY_NOT_EQUALS -> {
            // Currently ranges aren't allowed to be disjoint so we special-case this to just adjust
            // contiguous
            // ranges.
            if (previousConstraint is IntRangeConstraint) {
              if (previousConstraint.from == number) {
                IntRangeConstraint.range(previousConstraint.from + 1, previousConstraint.to)
              } else if (previousConstraint.to == number) {
                IntRangeConstraint.range(previousConstraint.from, previousConstraint.to - 1)
              } else {
                null
              }
            } else {
              null
            }
          }
          else -> return null
        }
      }
    }

    private fun getNonOverlapMessage(
      allowed: RangeConstraint?,
      actual: RangeConstraint,
      argument: UElement,
      usageInfo: AnnotationUsageInfo,
    ): String? {
      allowed ?: return null
      val contains = allowed.contains(actual) ?: return null
      if (!contains) {
        if (actual.infinite && actual.inferred) {
          // If it's an inferred range (for example, it's
          // surrounded by an "if (x > 10)"), and it's open
          // ended, we may not have the full picture; it could
          // have been constrained previously. In this case we
          // don't have enough confidence to assert that the
          // range is not fully constrained.
          return null
        }

        // Describe the parts
        var actualLabel = ""
        var allowedLabel = ""

        val argumentSource =
          when (val selector = argument.findSelector()) {
            is UCallExpression -> selector.methodName ?: ""
            is USimpleNameReferenceExpression -> selector.identifier
            else -> argument.sourcePsi?.text?.takeIf { it.length < 40 } ?: ""
          }

        if (argumentSource.isNotBlank()) {
          actualLabel = "`$argumentSource`"
        } else if (usageInfo.type == METHOD_CALL_PARAMETER) {
          allowedLabel = "the parameter "
          actualLabel = "the argument "
        }

        return allowed.describeDelta(actual, actualLabel, allowedLabel)
      }

      return null
    }

    /** Makes sure values are within the allowed range. */
    @JvmField
    val RANGE =
      Issue.create(
        id = "Range",
        briefDescription = "Outside Range",
        explanation =
          """
                Some parameters are required to be in a particular numerical range; this check \
                makes sure that arguments passed fall within the range. For arrays, Strings \
                and collections this refers to the size or length.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )
  }
}
