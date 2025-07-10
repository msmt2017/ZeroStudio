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
import com.android.tools.lint.checks.AnnotationDetector.Companion.ATTR_TO
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationLongValue
import kotlin.Long.Companion.MAX_VALUE
import kotlin.Long.Companion.MIN_VALUE
import kotlin.math.max
import kotlin.math.min
import org.jetbrains.uast.UAnnotation

class IntRangeConstraint private constructor(val from: Long, val to: Long) : RangeConstraint() {

  constructor(
    range: FloatRangeConstraint
  ) : this(
    if (range.from == Double.NEGATIVE_INFINITY) MIN_VALUE
    else if (!range.fromInclusive) range.from.toLong() + 1 else range.from.toLong(),
    if (range.to == Double.POSITIVE_INFINITY) MAX_VALUE
    else if (!range.toInclusive) range.to.toLong() - 1 else range.to.toLong(),
  )

  fun isValid(value: Long): Boolean {
    return value in from..to
  }

  override val infinite: Boolean
    get() = from == MIN_VALUE || to == MAX_VALUE

  fun describe(): String {
    return describe(null)
  }

  fun describe(actualValue: Long?): String {
    return describe(actualValue, "Value must be ")
  }

  fun describe(actualValue: Long?, prefix: String): String {
    val sb = StringBuilder(20)
    sb.append(prefix)

    // If we have an actual value, don't describe the full range, only describe
    // the parts that are outside the range
    if (actualValue != null && !isValid(actualValue)) {
      val value: Long = actualValue
      if (value < from) {
        sb.append("\u2265 ") // >= sign
        sb.append(from.toString())
      } else {
        assert(value > to)
        sb.append("\u2264 ") // <= sign
        sb.append(to.toString())
      }
      sb.append(" (was ").append(value).append(')')
      return sb.toString()
    }
    if (to == MAX_VALUE) {
      sb.append("\u2265 ") // >= sign
      sb.append(from.toString())
    } else if (from == MIN_VALUE) {
      sb.append("\u2264 ") // <= sign
      sb.append(to.toString())
    } else if (from == to) {
      sb.append(from.toString())
    } else {
      sb.append("\u2265 ") // >= sign
      sb.append(from.toString())
      sb.append(" and \u2264 ") // <= sign
      sb.append(to.toString())

      if (from > to) {
        sb.append(" (not possible)")
      }
    }
    if (actualValue != null) {
      sb.append(" (is ").append(actualValue).append(')')
    }
    return sb.toString()
  }

  override fun describeDelta(
    actual: RangeConstraint,
    actualLabel: String,
    allowedLabel: String,
  ): String {
    if (actual !is IntRangeConstraint) {
      return if (actual is FloatRangeConstraint) {
        describeDelta(IntRangeConstraint(actual), actualLabel, allowedLabel)
      } else {
        describe()
      }
    }

    val sb = StringBuilder()
    if (allowedLabel.isNotEmpty()) {
      sb.append(describe(null, "$allowedLabel must be "))
    } else {
      sb.append(describe(null))
    }
    sb.append(" but ")
    if (actualLabel.isNotEmpty()) {
      sb.append(actualLabel).append(' ')
    }

    if (actual.from < this.from) {
      if (actual.from == MIN_VALUE) {
        sb.append("can be < ${this.from}")
      } else {
        // works even for range, e.g. "x must be >= 0, but can be -5" can represent -4, -3, etc
        // instead of
        // "x must be >= 0, but can be -5..0" isn't a lot more readable
        sb.append("can be ${actual.from}")
      }
    } else if (actual.to > this.to) {
      if (actual.to == MAX_VALUE) {
        sb.append("can be > ${this.to}")
      } else {
        sb.append("can be ${actual.to}")
      }
    } else {
      error("There's no delta")
    }

    return sb.toString()
  }

  override fun and(other: RangeConstraint?): RangeConstraint {
    other ?: return this

    val range: IntRangeConstraint =
      when (other) {
        is IntRangeConstraint -> other
        is FloatRangeConstraint -> IntRangeConstraint(other)
        else -> error(other.javaClass.name)
      }

    val start = max(from, range.from)
    val end = min(to, range.to)
    return IntRangeConstraint(start, end)
  }

  override fun remove(other: RangeConstraint): RangeConstraint? {
    if (other is IntRangeConstraint) {
      if (from <= other.to) {
        if (other.to != MAX_VALUE && to > other.to + 1) {
          return IntRangeConstraint(other.to + 1, to)
        } else if (other.from != MAX_VALUE && from < other.from - 1) {
          return IntRangeConstraint(from, other.from - 1)
        }
      }
    } else if (other is FloatRangeConstraint) {
      return FloatRangeConstraint(this).remove(other)
    }
    return super.remove(other)
  }

  override fun toString(): String {
    return describe(null)
  }

  override fun contains(other: RangeConstraint): Boolean? {
    if (other is IntRangeConstraint) {
      return other.from >= from && other.to <= to
    } else if (other is FloatRangeConstraint) {
      if (
        !other.fromInclusive && other.from == from.toDouble() ||
          !other.toInclusive && other.to == to.toDouble()
      ) {
        return false
      }

      // Both represent infinity
      if (other.to > to && !(java.lang.Double.isInfinite(other.to) && to == MAX_VALUE)) {
        return false
      }
      return !(other.from < from && !(java.lang.Double.isInfinite(other.from) && from == MIN_VALUE))
    }
    return null
  }

  companion object {
    @JvmStatic
    fun create(annotation: UAnnotation): IntRangeConstraint {
      assert(INT_RANGE_ANNOTATION.isEquals(annotation.qualifiedName))
      val from = getAnnotationLongValue(annotation, ATTR_FROM, MIN_VALUE)
      val to = getAnnotationLongValue(annotation, ATTR_TO, MAX_VALUE)
      return IntRangeConstraint(from, to)
    }

    @JvmStatic
    fun atLeast(value: Long): IntRangeConstraint {
      return IntRangeConstraint(value, MAX_VALUE)
    }

    @JvmStatic
    fun atMost(value: Long): IntRangeConstraint {
      return IntRangeConstraint(MIN_VALUE, value)
    }

    @JvmStatic
    fun range(from: Long, to: Long): IntRangeConstraint {
      return IntRangeConstraint(from, to)
    }
  }
}
