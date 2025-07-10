/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.fromPlatformAnnotation
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.isPlatformAnnotation
import com.android.tools.lint.client.api.JavaEvaluator
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation

abstract class RangeConstraint {
  /**
   * Checks whether the given range is compatible with this one. We err on the side of caution. E.g.
   * if we have `method(x)` and the parameter declaration says that x is between 0 and 10, and then
   * we have a parameter which is known to be in the range 5 to 15, here we consider this a
   * compatible range; we don't flag this as an error. If however, the ranges don't overlap, *then*
   * we complain.
   */
  open fun contains(other: RangeConstraint): Boolean? {
    return null
  }

  /**
   * For a given allowed constraint, returns a string describing how the [actual] constraint is not
   * fully contained.
   */
  abstract fun describeDelta(
    actual: RangeConstraint,
    actualLabel: String,
    allowedLabel: String,
  ): String

  /** Intersect two ranges */
  abstract infix fun and(other: RangeConstraint?): RangeConstraint

  /** Remove the given [other] constraint from this constraint, if possible. */
  open fun remove(other: RangeConstraint): RangeConstraint? = null

  /** If true, this range was inferred and may not be complete */
  var inferred: Boolean = false

  /** Whether this range has infinite size (e.g. missing either upper or lower bound. */
  open val infinite: Boolean = false

  companion object {
    fun create(annotation: UAnnotation): RangeConstraint? {
      val qualifiedName = annotation.qualifiedName ?: return null
      if (annotation.attributeValues.isEmpty()) {
        return null
      }
      if (INT_RANGE_ANNOTATION.isEquals(qualifiedName)) {
        return IntRangeConstraint.create(annotation)
      } else if (FLOAT_RANGE_ANNOTATION.isEquals(qualifiedName)) {
        return FloatRangeConstraint.create(annotation)
      } else if (SIZE_ANNOTATION.isEquals(qualifiedName)) {
        return SizeConstraint.create(annotation)
      } else if (isPlatformAnnotation(qualifiedName)) {
        return create(annotation.fromPlatformAnnotation(qualifiedName))
      }
      return null
    }

    fun create(owner: PsiModifierListOwner, evaluator: JavaEvaluator): RangeConstraint? {
      for (annotation in evaluator.getAnnotations(owner, false)) {
        // Pick first; they're mutually exclusive
        create(annotation)?.let {
          return it
        }
      }
      return null
    }

    fun create(owner: UAnnotated, evaluator: JavaEvaluator): RangeConstraint? {
      for (annotation in evaluator.getAllAnnotations(owner, false)) {
        val constraint = create(annotation)
        // Pick first; they're mutually exclusive
        if (constraint != null) {
          return constraint
        }
      }
      return null
    }
  }
}
