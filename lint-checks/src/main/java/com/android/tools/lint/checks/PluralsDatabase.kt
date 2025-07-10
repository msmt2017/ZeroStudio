/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.checks.plurals.CLDR36Database
import com.android.tools.lint.checks.plurals.CLDR38Database
import com.android.tools.lint.checks.plurals.CLDR41Database
import com.android.tools.lint.checks.plurals.CLDR42Database
import com.android.tools.lint.detector.api.formatList
import java.util.Arrays
import java.util.EnumSet

abstract class PluralsDatabase(
  private val languageCodes: Array<String>,
  private val languageFlags: IntArray,
  internal val apiLevel: Int,
) {
  private val plurals = mutableMapOf<String, EnumSet<Quantity>>()

  init {
    check(languageCodes.size == languageFlags.size) {
      "Language code list and flag list have different lengths"
    }
  }

  fun getRelevant(language: String): EnumSet<Quantity>? {
    var set = plurals[language]
    if (set == null) {
      val index = getLanguageIndex(language)
      if (index == -1) {
        plurals[language] = EMPTY_SET
        return null
      }

      // Process each item and look for relevance
      val flag = languageFlags[index]
      set = EnumSet.noneOf(Quantity::class.java)
      if (flag and FLAG_ZERO != 0) {
        set.add(Quantity.zero)
      }
      if (flag and FLAG_ONE != 0) {
        set.add(Quantity.one)
      }
      if (flag and FLAG_TWO != 0) {
        set.add(Quantity.two)
      }
      if (flag and FLAG_FEW != 0) {
        set.add(Quantity.few)
      }
      if (flag and FLAG_MANY != 0) {
        set.add(Quantity.many)
      }
      plurals[language] = set
    }
    return if (set === EMPTY_SET) null else set
  }

  fun hasMultipleValuesForQuantity(language: String, quantity: Quantity): Boolean {
    return when (quantity) {
      Quantity.one -> getFlags(language) and FLAG_MULTIPLE_ONE != 0
      Quantity.two -> getFlags(language) and FLAG_MULTIPLE_TWO != 0
      else -> quantity == Quantity.zero && getFlags(language) and FLAG_MULTIPLE_ZERO != 0
    }
  }

  fun findIntegerExamples(language: String, quantity: Quantity): String? {
    return when (quantity) {
      Quantity.one -> getExampleForQuantityOne(language)
      Quantity.two -> getExampleForQuantityTwo(language)
      Quantity.zero -> getExampleForQuantityZero(language)
      else -> {
        null
      }
    }
  }

  protected abstract fun getExampleForQuantityZero(language: String): String?

  protected abstract fun getExampleForQuantityOne(language: String): String?

  protected abstract fun getExampleForQuantityTwo(language: String): String?

  private fun getFlags(language: String): Int {
    val index = getLanguageIndex(language)
    return if (index != -1) {
      languageFlags[index]
    } else 0
  }

  protected fun getLanguageIndex(language: String): Int {
    val index = Arrays.binarySearch(languageCodes, language)
    return if (index >= 0) {
      check(languageCodes[index] == language)
      index
    } else {
      -1
    }
  }

  companion object {
    private val EMPTY_SET = EnumSet.noneOf(Quantity::class.java)

    /** Bit set if this language uses quantity zero */
    const val FLAG_ZERO = 1 shl 0

    /** Bit set if this language uses quantity one */
    const val FLAG_ONE = 1 shl 1

    /** Bit set if this language uses quantity two */
    const val FLAG_TWO = 1 shl 2

    /** Bit set if this language uses quantity few */
    const val FLAG_FEW = 1 shl 3

    /** Bit set if this language uses quantity many */
    const val FLAG_MANY = 1 shl 4

    /** Bit set if this language has multiple values that match quantity zero */
    const val FLAG_MULTIPLE_ZERO = 1 shl 5

    /** Bit set if this language has multiple values that match quantity one */
    const val FLAG_MULTIPLE_ONE = 1 shl 6

    /** Bit set if this language has multiple values that match quantity two */
    const val FLAG_MULTIPLE_TWO = 1 shl 7

    private val datasets =
      mapOf(
        VersionCodes.R to CLDR36Database,
        VersionCodes.S to CLDR38Database,
        VersionCodes.S_V2 to CLDR38Database,
        VersionCodes.TIRAMISU to CLDR41Database,
      )

    val OLDEST
      get() = CLDR36Database

    val LATEST
      get() = CLDR42Database

    operator fun get(version: Int): PluralsDatabase {
      return datasets[version] ?: if (version < OLDEST.apiLevel && version != -1) OLDEST else LATEST
    }
  }

  enum class Quantity {
    // deliberately lower case to match attribute names
    few,
    many,
    one,
    two,
    zero,
    other;

    companion object {
      operator fun get(name: String): Quantity? {
        for (quantity in values()) {
          if (name == quantity.name) {
            return quantity
          }
        }
        return null
      }

      fun formatSet(set: EnumSet<Quantity>): String {
        val list: MutableList<String> = ArrayList(set.size)
        for (quantity in set) {
          list.add('`'.toString() + quantity.name + '`')
        }
        return formatList(list, Int.MAX_VALUE)
      }
    }
  }
}
