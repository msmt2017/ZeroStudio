/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_QUANTITY
import com.android.SdkConstants.TAG_ITEM
import com.android.SdkConstants.TAG_PLURALS
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.VALUES
import com.android.tools.lint.checks.PluralsDatabase.Quantity
import com.android.tools.lint.checks.TranslationDetector.Companion.getLanguageDescription
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getChildCount
import com.android.tools.lint.detector.api.getLocale
import java.util.EnumSet
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Checks for issues with quantity strings
 *
 * https://code.google.com/p/android/issues/detail?id=53015 53015: lint could report incorrect usage
 * of Resource.getQuantityString
 */
class PluralsDetector : ResourceXmlDetector() {
  override fun appliesTo(folderType: ResourceFolderType) = folderType == VALUES

  override fun getApplicableElements() = listOf(TAG_PLURALS)

  override fun visitElement(context: XmlContext, element: Element) {
    val count = getChildCount(element)
    if (count == 0) {
      context.report(
        MISSING,
        element,
        context.getLocation(element),
        "There should be at least one quantity string in this `<plural>` definition",
      )
      return
    }
    val locale = getLocale(context)
    if (locale == null || !locale.hasLanguage()) {
      return
    }
    // checked hasLanguage().
    val language = locale.language!!
    val plurals = PluralsDatabase[context.project.targetSdk]
    val relevant = plurals.getRelevant(language) ?: return
    val defined = EnumSet.noneOf(Quantity::class.java)
    val children = element.childNodes
    var i = 0
    val n = children.length
    while (i < n) {
      val node = children.item(i)
      if (node.nodeType != Node.ELEMENT_NODE) {
        i++
        continue
      }
      val child = node as Element
      if (TAG_ITEM != child.tagName) {
        i++
        continue
      }
      val quantityString = child.getAttribute(ATTR_QUANTITY)
      if (quantityString.isEmpty()) {
        i++
        continue
      }
      val quantity = Quantity[quantityString]
      if (quantity == null || quantity == Quantity.other) { // Not stored in the database
        i++
        continue
      }
      defined.add(quantity)
      if (
        plurals.hasMultipleValuesForQuantity(language, quantity) &&
          !haveFormattingParameter(child) &&
          context.isEnabled(IMPLIED_QUANTITY)
      ) {
        val example = plurals.findIntegerExamples(language, quantity)
        val append =
          if (example == null) {
            ""
          } else {
            " ($example)"
          }
        val message =
          """
                    The quantity `'$quantity'` matches more than one specific number in this locale$append, but the message did not \
                    include a formatting argument (such as `%d`). This is usually an internationalization error. See full issue \
                    explanation for more.
                    """
            .trimIndent()
        context.report(IMPLIED_QUANTITY, child, context.getLocation(child), message)
      }
      i++
    }
    if (relevant == defined) {
      return
    }

    // Look for missing
    val missing = relevant.clone()
    missing.removeAll(defined)
    if (!missing.isEmpty()) {
      val examplesLookup = PluralExamplesLookup.getInstance()
      val withExamples =
        missing.map { form ->
          val example = examplesLookup.findExample(language, form.name)?.formattedWithNumber()
          if (example != null) {
            "`${form.name}` (e.g. \"$example\")"
          } else {
            "`${form.name}`"
          }
        }
      val languageDescription = getLanguageDescription(language)
      val message =
        if (withExamples.size == 1) {
          "For locale $languageDescription the following quantity should also be defined: ${withExamples.single()}"
        } else {
          "For locale $languageDescription the following quantities should also be defined: ${withExamples.joinToString(", ")}"
        }
      val incident = Incident(MISSING, element, context.getLocation(element), message)
      val baselineDatabase = PluralsDatabase.OLDEST.getRelevant(language)
      if (baselineDatabase != null && missing.all { !baselineDatabase.contains(it) }) {
        incident.overrideSeverity(Severity.WARNING)
      }
      context.report(incident)
    }

    // Look for irrelevant
    val extra = defined.clone()
    extra.removeAll(relevant)
    if (extra.isNotEmpty()) {
      val message =
        String.format(
          "For language %1\$s the following quantities are not relevant: %2\$s",
          getLanguageDescription(language),
          Quantity.formatSet(extra),
        )
      context.report(EXTRA, element, context.getLocation(element), message)
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(PluralsDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    /** This locale should define a quantity string for the given quantity */
    @JvmField
    val MISSING =
      create(
        id = "MissingQuantity",
        briefDescription = "Missing quantity translation",
        explanation =
          """
                Different languages have different rules for grammatical agreement with quantity. In English, for example, the quantity 1 \
                is a special case. We write "1 book", but for any other quantity we'd write "n books". This distinction between singular \
                and plural is very common, but other languages make finer distinctions.

                This lint check looks at each translation of a `<plural>` and makes sure that all the quantity strings considered by the \
                given language are provided by this translation.

                For example, an English translation must provide a string for `quantity="one"`. Similarly, a Czech translation must \
                provide a string for `quantity="few"`.
            """,
        category = Category.MESSAGES,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        moreInfo =
          "https://developer.android.com/guide/topics/resources/string-resource.html#Plurals",
      )

    /** This translation is not needed in this locale */
    @JvmField
    val EXTRA =
      create(
        id = "UnusedQuantity",
        briefDescription = "Unused quantity translations",
        explanation =
          """
                Android defines a number of different quantity strings, such as `zero`, `one`, `few` and `many`. However, many languages \
                do not distinguish grammatically between all these different quantities.

                This lint check looks at the quantity strings defined for each translation and flags any quantity strings that are unused \
                (because the language does not make that quantity distinction, and Android will therefore not look it up).

                For example, in Chinese, only the `other` quantity is used, so even if you provide translations for `zero` and `one`, \
                these strings will **not** be returned when `getQuantityString()` is called, even with `0` or `1`.
            """,
        category = Category.MESSAGES,
        priority = 3,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        moreInfo =
          "https://developer.android.com/guide/topics/resources/string-resource.html#Plurals",
      )

    /** This plural does not use the quantity value */
    @JvmField
    val IMPLIED_QUANTITY =
      create(
        id = "ImpliedQuantity",
        briefDescription = "Implied Quantities",
        explanation =
          """
                Plural strings should generally include a `%s` or `%d` formatting argument. In locales like English, the `one` quantity \
                only applies to a single value, 1, but that's not true everywhere. For example, in Slovene, the `one` quantity will apply \
                to 1, 101, 201, 301, and so on. Similarly, there are locales where multiple values match the `zero` and `two` quantities.

                In these locales, it is usually an error to have a message which does not include a formatting argument (such as '%d'), \
                since it will not be clear from the grammar what quantity the quantity string is describing.
                """,
        category = Category.MESSAGES,
        priority = 5,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        moreInfo =
          "https://developer.android.com/guide/topics/resources/string-resource.html#Plurals",
      )

    /**
     * Returns true if the given string/plurals item element contains a formatting parameter,
     * possibly within HTML markup or xliff metadata tags
     */
    private fun haveFormattingParameter(element: Element): Boolean {
      val children = element.childNodes
      var i = 0
      val n = children.length
      while (i < n) {
        val child = children.item(i)
        val nodeType = child.nodeType
        if (nodeType == Node.ELEMENT_NODE) {
          if (haveFormattingParameter(child as Element)) {
            return true
          }
        } else if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
          val text = child.nodeValue
          if (containsExpandTemplate(text)) {
            return true
          }
          if (text.indexOf('%') == -1) {
            i++
            continue
          }
          if (StringFormatDetector.getFormatArgumentCount(text, null) >= 1) {
            return true
          }
        }
        i++
      }
      return false
    }

    private fun containsExpandTemplate(text: String): Boolean {
      // Checks to see if the string has a template parameter
      // processed by android.text.TextUtils#expandTemplate
      var index = 0
      while (true) {
        index = text.indexOf('^', index)
        if (index == -1 || index == text.length - 1) {
          return false
        }
        if (text[index + 1].isDigit()) {
          return true
        }
        index++
      }
    }
  }
}
