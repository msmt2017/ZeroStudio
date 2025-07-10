/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_TRANSLATABLE
import com.android.SdkConstants.TAG_PLURALS
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.TAG_STRING_ARRAY
import com.android.resources.ResourceFolderType
import com.android.tools.lint.checks.TypoLookup.Companion.isLetter
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.RESOURCE_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getLocale
import com.android.utils.childrenIterator
import com.android.utils.usLocaleCapitalize
import kotlin.text.Charsets
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Check which looks for likely typos in Strings.
 *
 * TODO:
 * * Add check of Java String literals too!
 * * Add support for **additional** languages. The typo detector is now multilingual and looks for
 *   typos-*locale*.txt files to use. However, we need to seed it with additional typo databases. I
 *   did some searching and came up with some alternatives. Here's the strategy I used: Used Google
 *   Translate to translate "Wikipedia Common Misspellings", and then I went to google.no, google.fr
 *   etc searching with that translation, and came up with what looks like wikipedia language local
 *   lists of typos. This is how I found the Norwegian one for example:
 *   http://no.wikipedia.org/wiki/Wikipedia:Liste_over_alminnelige_stavefeil/Maskinform
 *
 *   Here are some additional possibilities not yet processed:
 * * French: http://fr.wikipedia.org/wiki/Wikip%C3%A9dia:Liste_de_fautes_d'orthographe_courantes
 *   (couldn't find a machine-readable version there?)
 * * Swedish: http://sv.wikipedia.org/wiki/Wikipedia:Lista_%C3%B6ver_vanliga_spr%C3%A5kfel (couldn't
 *   find a machine-readable version there?)
 * * Consider also digesting files like http://sv.wikipedia.org/wiki/Wikipedia:AutoWikiBrowser/Typos
 *   http://en.wikipedia.org/wiki/Wikipedia:AutoWikiBrowser/User_manual
 */
class TypoDetector : ResourceXmlDetector() {
  private var lookup: TypoLookup? = null
  private var lastLanguage: String? = null
  private var lastRegion: String? = null
  private var language: String? = null
  private var region: String? = null

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  /**
   * Look up the locale and region from the given parent folder name and store it in [language] and
   * [region]
   */
  private fun initLocale(context: XmlContext) {
    val locale = getLocale(context)
    if (locale?.hasLanguage() == true) {
      language = locale.language
      region = if (locale.hasRegion()) locale.region else null
    }
  }

  override fun beforeCheckFile(context: Context) {
    initLocale(context as XmlContext)
    language = language ?: "en"
    if (lastLanguage == language || (region == null || lastRegion != region)) {
      lookup = TypoLookup[context.client, language!!, region]
      lastLanguage = language
      lastRegion = region
    }
  }

  override fun getApplicableElements() = listOf(TAG_STRING, TAG_STRING_ARRAY, TAG_PLURALS)

  override fun visitElement(context: XmlContext, element: Element) {
    if (lookup == null) {
      return
    }
    visit(context, element, element)
  }

  private fun visit(context: XmlContext, parent: Element, node: Node) {
    val nodeType = node.nodeType
    if (nodeType == Node.TEXT_NODE || nodeType == Node.CDATA_SECTION_NODE) {
      // TODO: Figure out how to deal with entities
      check(context, parent, node, node.nodeValue)
    } else {
      for (child in node.childrenIterator()) {
        visit(context, parent, child)
      }
    }
  }

  private fun check(context: XmlContext, element: Element, node: Node, text: String) {
    if (!isTranslatable(element)) return

    val max = text.length
    var lastWordBegin = -1
    var lastWordEnd = -1
    var checkedTypos = false
    val wordStart = text.indexOfFirst { !it.isWhitespace() }
    if (wordStart == -1) return
    // Don't look for typos in resource references; they are not
    // user visible anyway
    if (text[wordStart].let { it == '@' || it == '?' }) return

    var index = wordStart
    while (index < max) {
      // Move index to next letter
      while (index < max) {
        val c = text[index]
        if (c == '\\') {
          index += 2 // Skip escaped character (including the backslash)
        } else if (c.isLetter()) {
          break
        } else {
          index++
        }
      }
      if (index >= max) {
        return
      }

      val begin = index

      // Move index to end of word
      while (index < max) {
        val c = text[index]
        if (c == '\\') {
          index++
          break
        } else if (!c.isLetter() && c != '_') {
          if ((c == '1' || c == '!') && index > begin + 1) {
            checkForExclamation(context, node, text, index, begin)
          }
          break
        } else if (text[index].toInt() >= 0x80) {
          // Switch to UTF-8 handling for this string
          if (checkedTypos) {
            // If we've already checked words we may have reported typos
            // so create a substring from the current word and on.
            val utf8Text = text.substring(begin).toByteArray(Charsets.UTF_8)
            checkUtf8Text(context, element, node, utf8Text, 0, utf8Text.size, text, begin)
          } else {
            // If all we've done so far is skip whitespace (common scenario)
            // then no need to substring the text, just re-search with the
            // UTF-8 routines
            val utf8Text = text.toByteArray(Charsets.UTF_8)
            checkUtf8Text(context, element, node, utf8Text, 0, utf8Text.size, text, 0)
          }
          return
        }
        index++
      }
      val end = index
      checkedTypos = true
      val replacements = lookup!!.getTypos(text, begin, end)
      if (replacements != null) {
        reportTypo(context, node, text, begin, replacements)
      }
      checkRepeatedWords(context, element, node, text, lastWordBegin, lastWordEnd, begin, end)
      lastWordBegin = begin
      lastWordEnd = end
      index = end + 1
    }
  }

  private fun checkForExclamation(
    context: XmlContext,
    node: Node,
    text: String,
    index: Int,
    begin: Int,
  ) {
    // Peek ahead: if we find punctuation or lower case letter don't flag it
    var problem = true
    var found1 = text[index] == '1'
    var end = index + 1
    for (ch in text.subSequence(index + 1, text.length)) {
      if (ch == '\n') {
        break
      } else if (ch == '1') {
        // Allow repeated 1's
        found1 = true
      } else if (ch == '!') {
        // Allow repeated 1's or !'s
      } else if (!ch.isWhitespace()) {
        if (ch.isLowerCase() || ch.isDigit()) {
          problem = false
        } else if (ch == ',' || ch == '.' || ch == ':' || ch == ';') {
          problem = false
        }
        break
      }
      end++
    }
    if (problem && found1) {
      val actual = text.substring(begin, end)
      val intended = actual.replace('1', '!')
      val fix =
        fix()
          .name("Replace with \"$intended\"")
          .replace()
          .text(actual)
          .with(intended)
          .range(context.getLocation(node))
          .build()
      context.report(
        ISSUE,
        node,
        context.getLocation(node, begin, index),
        "Did you mean \"$intended\" instead of \"$actual\"?",
        fix,
      )
    }
  }

  private fun checkRepeatedWords(
    context: XmlContext,
    element: Element,
    node: Node,
    text: String,
    lastWordBegin: Int,
    lastWordEnd: Int,
    begin: Int,
    end: Int,
  ) {
    if (lastWordBegin != -1 && end - begin == lastWordEnd - lastWordBegin && end - begin > 1) {
      // See whether we have a repeated word
      var different = false
      var i = lastWordBegin
      var j = begin
      while (i < lastWordEnd) {
        if (text[i] != text[j]) {
          different = true
          break
        }
        i++
        j++
      }
      if (!different && onlySpace(text, lastWordEnd, begin) && isTranslatable(element)) {
        reportRepeatedWord(context, node, text, lastWordBegin, begin, end)
      }
    }
  }

  private fun checkUtf8Text(
    context: XmlContext,
    element: Element,
    node: Node,
    utf8Text: ByteArray,
    byteStart: Int,
    byteEnd: Int,
    text: String,
    charStart: Int,
  ) {
    var charStart = charStart
    var lastWordBegin = -1
    var lastWordEnd = -1
    var index = byteStart
    while (index < byteEnd) {
      // Find beginning of word
      while (index < byteEnd) {
        var b = utf8Text[index].toInt()
        if (b == '\\'.toInt()) {
          index++
          charStart++
          if (index < byteEnd) {
            b = utf8Text[index].toInt()
          }
        } else if (isLetter(b.toByte())) {
          break
        }
        index++
        if (b and 0x80 == 0 || b and 0xC0 == 0xC0) {
          // First characters in UTF-8 are always ASCII (0 high bit) or 11XXXXXX
          charStart++
        }
      }
      if (index >= byteEnd) {
        return
      }
      var charEnd = charStart
      val begin = index

      // Find end of word. Unicode has the nice property that even 2nd, 3rd and 4th
      // bytes won't match these ASCII characters (because the high bit must be set there)
      while (index < byteEnd) {
        var b = utf8Text[index].toInt()
        if (b == '\\'.toInt()) {
          index++
          charEnd++
          if (index < byteEnd) {
            b = utf8Text[index++].toInt()
            if (b and 0x80 == 0 || b and 0xC0 == 0xC0) {
              charEnd++
            }
          }
          break
        } else if (!isLetter(b.toByte())) {
          break
        }
        index++
        if (b and 0x80 == 0 || b and 0xC0 == 0xC0) {
          // First characters in UTF-8 are always ASCII (0 high bit) or 11XXXXXX
          charEnd++
        }
      }
      val end = index
      val replacements = lookup!!.getTypos(utf8Text, begin, end)
      if (replacements != null && isTranslatable(element)) {
        reportTypo(context, node, text, charStart, replacements)
      }
      checkRepeatedWords(
        context,
        element,
        node,
        text,
        lastWordBegin,
        lastWordEnd,
        charStart,
        charEnd,
      )
      lastWordBegin = charStart
      lastWordEnd = charEnd
      charStart = charEnd
    }
  }

  /** Report the typo found at the given offset and suggest the given replacements */
  private fun reportTypo(
    context: XmlContext,
    node: Node,
    text: String,
    begin: Int,
    replacements: List<String>,
  ) {
    if (replacements.size < 2) {
      return
    }
    val typo = replacements[0]
    val word = text.substring(begin, begin + typo.length)
    var first: String? = null
    val message: String
    val fixBuilder = fix().alternatives()
    val isCapitalized = word[0].isUpperCase()
    val sb = StringBuilder(40)
    var i = 1
    val n = replacements.size
    while (i < n) {
      var replacement = replacements[i]
      if (first == null) {
        first = replacement
      }
      if (sb.length > 0) {
        sb.append(" or ")
      }
      sb.append('"')
      if (isCapitalized) {
        replacement = replacement.usLocaleCapitalize()
      }
      sb.append(replacement)
      fixBuilder.add(
        fix().name("Replace with \"$replacement\"").replace().text(word).with(replacement).build()
      )
      sb.append('"')
      i++
    }
    val fix = fixBuilder.build()
    message =
      if (first != null && first.equals(word, ignoreCase = true)) {
        if (first == word) {
          return
        }
        "\"$word\" is usually capitalized as \"$first\""
      } else {
        "\"$word\" is a common misspelling; did you mean $sb?"
      }
    val end = begin + word.length
    context.report(ISSUE, node, context.getLocation(node, begin, end), message, fix)
  }

  /** Reports a repeated word */
  private fun reportRepeatedWord(
    context: XmlContext,
    node: Node,
    text: String,
    lastWordBegin: Int,
    begin: Int,
    end: Int,
  ) {
    val word = text.substring(begin, end)
    if (isAllowed(word)) {
      return
    }
    val message = "Repeated word \"$word\" in message: possible typo"
    val replace =
      if (lastWordBegin > 1 && text[lastWordBegin - 1] == ' ') {
        " $word"
      } else if (end < text.length - 1 && text[end] == ' ') {
        "$word "
      } else {
        word
      }
    val fix = fix().name("Delete repeated word").replace().text(replace).with("").build()
    val location = context.getLocation(node, lastWordBegin, end)
    context.report(ISSUE, node, location, message, fix)
  }

  companion object {
    @JvmField
    val ISSUE =
      create(
        id = "Typos",
        briefDescription = "Spelling error",
        explanation =
          """
                This check looks through the string definitions, and if it finds any words \
                that look like likely misspellings, they are flagged.""",
        category = Category.MESSAGES,
        priority = 7,
        severity = Severity.WARNING,
        implementation = Implementation(TypoDetector::class.java, RESOURCE_FILE_SCOPE),
      )

    private fun onlySpace(text: String, fromInclusive: Int, toExclusive: Int): Boolean {
      for (i in fromInclusive until toExclusive) {
        if (!text[i].isWhitespace()) {
          return false
        }
      }
      return true
    }

    private fun isTranslatable(element: Element): Boolean {
      val translatable = element.getAttributeNode(ATTR_TRANSLATABLE)
      return translatable == null || translatable.value == "true"
    }

    private fun isAllowed(word: String): Boolean {
      // See https://en.wikipedia.org/wiki/Reduplication

      // Capitalized: names or place names. There are various places
      // with repeated words, such as Pago Pago
      // https://en.wikipedia.org/wiki/List_of_reduplicated_place_names
      if (word[0].isUpperCase()) {
        return true
      }
      when (word) {
        "that",
        "yadda",
        "bye",
        "choo",
        "night",
        "dot",
        "tsk",
        "no" -> return true
      }
      return false
    }
  }
}
