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

class PluralExamplesLookup {
  private val exampleMap: Map<String, Map<String, PluralExample>>

  init {
    val lines =
      PluralExamplesLookup::class.java.getResourceAsStream(filename)?.buffered()?.reader()?.use {
        stream ->
        stream.readLines()
      } ?: error("Could not load plural-examples.txt")
    exampleMap = lines.map(Companion::parseLine).associate { it }
  }

  fun findExample(language: String, keyword: String): PluralExample? {
    return exampleMap[language]?.get(keyword)
  }

  companion object {
    private const val filename = "/plural-examples.txt"
    private var instance: PluralExamplesLookup? = null

    fun getInstance(): PluralExamplesLookup =
      instance ?: PluralExamplesLookup().also { instance = it }

    private fun parseLine(line: String): Pair<String, Map<String, PluralExample>> {
      val lineParts = line.split('/')
      val language = lineParts[0]
      val examples = lineParts[1]
      val exampleParts = examples.split('|')
      return language to
        exampleParts
          .map { examplePart ->
            val parts = examplePart.split(':')
            val keyword = parts[0]
            val number = parts[1].substringBefore('~')
            val example = parts[2].takeIf(String::isNotEmpty)
            PluralExample(language, keyword, number, example)
          }
          .associateBy { it.keyword }
    }
  }
}

data class PluralExample(
  val language: String,
  val keyword: String,
  val number: String,
  val example: String?,
) {
  fun formattedWithNumber() = example?.replace("{0}", number) ?: number
}

// The code below is used to generate the plurals.txt file used in the lookup. Note that it imports
// ICU4J classes, some of which need to
// be modified for the code to successfully execute; namely, the original PluralRules#getSample
// implementation is broken (as it will report
// 1E6 as 1.0), so the code below will work around that bug by directly accessing the lower-level
// data in the ICU data, but this requires
// making some private fields of the IUC classes public.
//
// import com.ibm.icu.impl.ICUData
// import com.ibm.icu.impl.ICUResourceBundle
// import com.ibm.icu.text.PluralRules
// import com.ibm.icu.util.ULocale
// import java.io.File
// import java.util.*
//
// data class PluralEntry(val keyword: String, val number: String, val example: String?) {
//    override fun toString() = "$keyword:$number:${example ?: ""}"
// }
//
// data class PluralSet(val language: String, val entries: List<PluralEntry>) {
//    override fun toString() = "$language/${entries.joinToString("|")}"
// }
//
// fun main() {
//    val sets = mutableListOf<PluralSet>()
//    val processedLanguages = mutableSetOf<String>()
//    for (locale in Locale.getAvailableLocales()) {
//        if (processedLanguages.contains(locale.language)) {
//            continue
//        }
//        val ulocale = ULocale(locale.language)
//        val rules = PluralRules.forLocale(ulocale)
//        val rb = ICUResourceBundle.getBundleInstance(ICUData.ICU_BASE_NAME, locale) as
// ICUResourceBundle
//
//        val entries = mutableListOf<PluralEntry>()
//        for (keyword in rules.keywords) {
//            val integerSamples = rules.rules.getDecimalSamples(keyword,
// PluralRules.SampleType.INTEGER)
//            if (integerSamples != null) {
//                val number = if (keyword == "zero") {
//                    integerSamples.samples.first()
//                } else {
//                    integerSamples.samples.first { it.start.integerValue != 0L &&
// it.end.integerValue != 0L }
//                }
//                val example = try {
//                    val ne =
// rb.getWithFallback("NumberElements").get("minimalPairs").get("plural")
//                    ne.get(keyword).string
//                } catch (e: MissingResourceException) {
//                    null
//                }
//                entries.add(PluralEntry(keyword, number.toString(), example?.replace('\u00A0', '
// ')))
//            }
//            val decimalSamples = rules.rules.getDecimalSamples(keyword,
// PluralRules.SampleType.DECIMAL)
//            if (decimalSamples != null) {
//                val example = try {
//                    val ne =
// rb.getWithFallback("NumberElements").get("minimalPairs").get("plural")
//                    ne.get(keyword).string
//                } catch (e: MissingResourceException) {
//                    null
//                }
//                val number = decimalSamples.samples.firstOrNull { it.start.integerValue != 0L &&
// it.end.integerValue != 0L }
//                if (number != null && entries.none { it.keyword == keyword }) {
//                    entries.add(PluralEntry(keyword, number.toString(), example?.replace('\u00A0',
// ' ')))
//                }
//            }
//        }
//        if (entries.isNotEmpty()) {
//            sets.add(PluralSet(locale.language, entries))
//        }
//        processedLanguages.add(locale.language)
//    }
//
//    File("plurals.txt").bufferedWriter().use { writer ->
//        for (set in sets.sortedBy { it.language }) {
//            writer.write(set.toString())
//            writer.newLine()
//        }
//    }
// }
