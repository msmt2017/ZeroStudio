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

import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_FROM
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_FROM_INCLUSIVE
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO_INCLUSIVE
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationBooleanValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationDoubleValue
import kotlin.Double.Companion.NEGATIVE_INFINITY
import kotlin.Double.Companion.POSITIVE_INFINITY
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression

class FloatRangeConstraint
private constructor(
  val from: Double,
  val to: Double,
  val fromInclusive: Boolean,
  val toInclusive: Boolean,
) : RangeConstraint() {

  constructor(
    range: IntRangeConstraint
  ) : this(
    if (range.from == Long.MIN_VALUE) NEGATIVE_INFINITY else range.from.toDouble(),
    if (range.to == Long.MAX_VALUE) POSITIVE_INFINITY else range.to.toDouble(),
    true,
    true,
  )

  fun isValid(value: Double): Boolean {
    return (fromInclusive && value >= from || !fromInclusive && value > from) &&
      (toInclusive && value <= to || !toInclusive && value < to)
  }

  override val infinite: Boolean
    get() = from == NEGATIVE_INFINITY || to == POSITIVE_INFINITY

  fun describe(argument: Double): String {
    return describe(null, argument)
  }

  @JvmOverloads
  fun describe(
    argument: UExpression? = null,
    actualValue: Double? = null,
    prefix: String = "Value must be ",
  ): String {
    val sb = StringBuilder(20)
    sb.append(prefix)
    val valueString =
      if (argument is ULiteralExpression) {
        // Use source text instead to avoid rounding errors involved in conversion, e.g
        //    Error: Value must be > 2.5 (was 2.490000009536743) [Range]
        //    printAtLeastExclusive(2.49f); // ERROR
        //                          ~~~~~
        var str = argument.asSourceString()
        if (str.endsWith("f") || str.endsWith("F")) {
          str = str.substring(0, str.length - 1)
        }
        str
      } else actualValue?.toString()

    // If we have an actual value, don't describe the full range, only describe
    // the parts that are outside the range
    if (actualValue != null && !isValid(actualValue)) {
      val value: Double = actualValue
      if (from != NEGATIVE_INFINITY) {
        if (to != POSITIVE_INFINITY) {
          if (fromInclusive && value < from || !fromInclusive && value <= from) {
            if (fromInclusive) {
              sb.append('\u2265') // >= sign
            } else {
              sb.append('>')
            }
            sb.append(' ')
            sb.append(from.toString())
          } else {
            assert(toInclusive && value > to || !toInclusive && value >= to)
            if (toInclusive) {
              sb.append('\u2264') // <= sign
            } else {
              sb.append('<')
            }
            sb.append(' ')
            sb.append(to.toString())
          }
        } else {
          if (fromInclusive) {
            sb.append('\u2265') // >= sign
          } else {
            sb.append('>')
          }
          sb.append(' ')
          sb.append(from.toString())
        }
      } else if (to != POSITIVE_INFINITY) {
        if (toInclusive) {
          sb.append('\u2264') // <= sign
        } else {
          sb.append('<')
        }
        sb.append(' ')
        sb.append(to.toString())
      }
      sb.append(" (was ").append(valueString).append(")")
      return sb.toString()
    }
    if (from != NEGATIVE_INFINITY) {
      if (to != POSITIVE_INFINITY) {
        if (from == to && fromInclusive && toInclusive) {
          sb.append(from.toString())
        } else {
          if (fromInclusive) {
            sb.append('\u2265') // >= sign
          } else {
            sb.append('>')
          }
          sb.append(' ')
          sb.append(from.toString())
          sb.append(" and ")
          if (toInclusive) {
            sb.append('\u2264') // <= sign
          } else {
            sb.append('<')
          }
          sb.append(' ')
          sb.append(to.toString())
        }

        if (from > to || from == to && fromInclusive && !toInclusive) {
          sb.append(" (not possible)")
        }
      } else {
        if (fromInclusive) {
          sb.append('\u2265') // >= sign
        } else {
          sb.append('>')
        }
        sb.append(' ')
        sb.append(from.toString())
      }
    } else if (to != POSITIVE_INFINITY) {
      if (toInclusive) {
        sb.append('\u2264') // <= sign
      } else {
        sb.append('<')
      }
      sb.append(' ')
      sb.append(to.toString())
    }
    if (valueString != null) {
      sb.append(" (is ").append(valueString).append(')')
    }
    return sb.toString()
  }

  override fun describeDelta(
    actual: RangeConstraint,
    actualLabel: String,
    allowedLabel: String,
  ): String {
    if (actual !is FloatRangeConstraint) {
      return if (actual is IntRangeConstraint) {
        describeDelta(FloatRangeConstraint(actual), actualLabel, allowedLabel)
      } else {
        describe()
      }
    }

    val sb = StringBuilder()
    if (allowedLabel.isNotEmpty()) {
      sb.append(describe(null, null, "$allowedLabel must be "))
    } else {
      sb.append(describe(null))
    }
    sb.append(" but ")
    if (actualLabel.isNotEmpty()) {
      sb.append(actualLabel).append(' ')
    }

    // No overlap? If so just display both ranges
    if (
      this.to <= actual.from && actual.to != POSITIVE_INFINITY ||
        this.from >= actual.to && actual.from != NEGATIVE_INFINITY
    ) {
      sb.append("can be " + actual.describe(null, null, ""))
      return sb.toString()
    }

    if (
      actual.from < this.from || actual.from == this.from && !fromInclusive && actual.fromInclusive
    ) {
      if (actual.from == NEGATIVE_INFINITY) {
        sb.append("can be < ${this.from}")
      } else {
        // works even for range, e.g. "x must be >= 0, but can be -5" can represent -4, -3, etc
        // instead of
        // "x must be >= 0, but can be -5..0" isn't a lot more readable
        sb.append("can be ${actual.from}")
      }
    } else if (actual.to > this.to || actual.to == to && !toInclusive && actual.toInclusive) {
      if (actual.to == POSITIVE_INFINITY) {
        sb.append("can be > ${this.to}")
      } else {
        sb.append("can be ${actual.to}")
      }
    } else {
      error("There's no delta")
    }

    return sb.toString()
  }

  /** Intersect two ranges */
  override infix fun and(other: RangeConstraint?): FloatRangeConstraint {
    other ?: return this

    val range: FloatRangeConstraint =
      when (other) {
        is FloatRangeConstraint -> other
        is IntRangeConstraint -> FloatRangeConstraint(other)
        else -> error(other.javaClass.name)
      }

    val start: Double
    val startInclusive: Boolean
    val end: Double
    val endInclusive: Boolean

    if (from == range.from) {
      start = from
      startInclusive = fromInclusive && range.fromInclusive
    } else if (from > range.from) {
      start = from
      startInclusive = fromInclusive
    } else {
      start = range.from
      startInclusive = range.fromInclusive
    }

    if (to == range.to) {
      end = to
      endInclusive = toInclusive && range.toInclusive
    } else if (to < range.to) {
      end = to
      endInclusive = toInclusive
    } else {
      end = range.to
      endInclusive = range.toInclusive
    }
    return FloatRangeConstraint(start, end, startInclusive, endInclusive)
  }

  override fun remove(other: RangeConstraint): RangeConstraint? {
    if (other is FloatRangeConstraint) {
      if (from <= other.to) {
        if (other.to != POSITIVE_INFINITY && to > other.to + 1) {
          return FloatRangeConstraint(other.to, to, false, toInclusive)
        } else if (other.from != NEGATIVE_INFINITY && from < other.from - 1) {
          return FloatRangeConstraint(from, other.from, fromInclusive, false)
        }
      }
    } else if (other is IntRangeConstraint) {
      return remove(FloatRangeConstraint(other))
    }
    return super.remove(other)
  }

  override fun contains(other: RangeConstraint): Boolean? {
    if (other is FloatRangeConstraint) {
      return !(other.from < from || other.to > to) &&
        !(!fromInclusive && other.fromInclusive && other.from == from) &&
        !(!toInclusive && other.toInclusive && other.to == to)
    } else if (other is IntRangeConstraint) {
      return !(other.from < from || other.to > to) &&
        !(!fromInclusive && other.from.toDouble() == from) &&
        !(!toInclusive && other.to.toDouble() == to)
    }
    return null
  }

  override fun toString(): String {
    return describe(null, null)
  }

  companion object {
    @JvmStatic
    fun create(annotation: UAnnotation): FloatRangeConstraint {
      assert(FLOAT_RANGE_ANNOTATION.isEquals(annotation.qualifiedName))
      val from = getAnnotationDoubleValue(annotation, ATTR_FROM, NEGATIVE_INFINITY)
      val to = getAnnotationDoubleValue(annotation, ATTR_TO, POSITIVE_INFINITY)
      val fromInclusive = getAnnotationBooleanValue(annotation, ATTR_FROM_INCLUSIVE, true)
      val toInclusive = getAnnotationBooleanValue(annotation, ATTR_TO_INCLUSIVE, true)
      return FloatRangeConstraint(from, to, fromInclusive, toInclusive)
    }

    @JvmStatic
    fun range(from: Double, to: Double): FloatRangeConstraint {
      return FloatRangeConstraint(from, to, true, true)
    }

    @JvmStatic
    fun range(
      from: Double,
      fromInclusive: Boolean,
      to: Double,
      toInclusive: Boolean,
    ): FloatRangeConstraint {
      return FloatRangeConstraint(from, to, fromInclusive, toInclusive)
    }

    @JvmStatic
    fun atLeast(from: Double): FloatRangeConstraint {
      return FloatRangeConstraint(from, POSITIVE_INFINITY, true, true)
    }

    @JvmStatic
    fun atMost(to: Double): FloatRangeConstraint {
      return FloatRangeConstraint(NEGATIVE_INFINITY, to, true, true)
    }

    @JvmStatic
    fun greaterThan(from: Double): FloatRangeConstraint {
      return FloatRangeConstraint(from, POSITIVE_INFINITY, false, true)
    }

    @JvmStatic
    fun lessThan(to: Double): FloatRangeConstraint {
      return FloatRangeConstraint(NEGATIVE_INFINITY, to, true, false)
    }
  }
}
