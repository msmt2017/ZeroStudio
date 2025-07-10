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

import com.android.SdkConstants.TAG_PLURALS
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.TAG_STRING_ARRAY
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element
import org.w3c.dom.Node

class StringEscapeDetector : ResourceXmlDetector() {

  companion object Issues {
    /** Invalid XML escaping */
    @JvmField
    val STRING_ESCAPING =
      create(
        id = "StringEscaping",
        briefDescription = "Invalid string escapes",
        explanation =
          """
                Apostrophes (') must always be escaped (with a \\\\), unless they appear \
                in a string which is itself escaped in double quotes (\").
                """,
        category = Category.MESSAGES,
        priority = 9,
        severity = Severity.ERROR,
        implementation = Implementation(StringEscapeDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }

  override fun getApplicableElements() = listOf(TAG_STRING, TAG_STRING_ARRAY, TAG_PLURALS)

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      TAG_STRING -> {
        findTextNode(context, element)
      }
      TAG_PLURALS,
      TAG_STRING_ARRAY -> {
        for (index in 0 until element.childNodes.length) {
          val child = element.childNodes.item(index)
          if (child.nodeType == Node.ELEMENT_NODE) {
            findTextNode(context, child as Element)
          }
        }
      }
    }
  }

  private fun findTextNode(context: XmlContext, parent: Element) {
    for (index in 0 until parent.childNodes.length) {
      val child = parent.childNodes.item(index)
      if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
        checkXmlEscapes(context, child, parent, child.nodeValue)
        return
      }
    }
  }

  /**
   * Check the XML for the string format. This is a port of portions of the code in
   * frameworks/base/libs/androidfw/ResourceTypes.cpp (and in particular, the stringToValue and
   * collectString methods)
   */
  private fun checkXmlEscapes(
    context: XmlContext,
    textNode: Node,
    element: Element,
    string: String,
  ) {
    var s = 0
    var len = string.length
    val n = len

    // First strip leading/trailing whitespace.  Do this before handling
    // escapes, so they can be used to force whitespace into the string.
    while (len > 0 && string[s].isWhitespace()) {
      s++
      len--
    }
    while (len > 0 && string[s + len - 1].isWhitespace()) {
      len--
    }
    // If the string ends with '\', then we keep the space after it.
    if (len > 0 && string[s + len - 1] == '\\' && s + len < n) {
      len++
    }
    var quoted = 0.toChar()
    var p = s
    while (p < s + len) {
      while (p < s + len) {
        val c = string[p]
        if (c == '\\') {
          break
        }
        if (
          quoted.code == 0 &&
            c.isWhitespace() &&
            (c != ' ' || p == n - 1 || string[p + 1].isWhitespace())
        ) {
          break
        }
        if (c == '"' && (quoted.code == 0 || quoted == '"')) {
          break
        }
        if (c == '\'' && (quoted.code == 0 || quoted == '\'')) {
          // In practice, when people write ' instead of \'
          // in a string, they are doing it by accident
          // instead of really meaning to use ' as a quoting
          // character.  Warn them so they don't lose it.

          // Use a location range not just from p to p+1 but from p to len
          // such that the error is more visually prominent/evident in
          // the source editor.
          val location = context.getLocation(textNode, p, len)
          val fix =
            fix().name("Escape Apostrophe").replace().pattern("[^\\\\]?(')").with("\\'").build()
          context.report(STRING_ESCAPING, element, location, "Apostrophe not preceded by \\\\", fix)
          return
        }
        p++
      }
      if (p < s + len) {
        val cp = string[p]
        if (cp == '"' || cp == '\'') {
          quoted =
            if (quoted.code == 0) {
              cp
            } else {
              0.toChar()
            }
          p++
        } else if (cp.isWhitespace()) {
          // Space outside of a quote -- consume all spaces and
          // leave a single plain space char.
          p++
          while (p < s + len && string[p].isWhitespace()) {
            p++
          }
        } else if (cp == '\\') {
          p++
          if (p < s + len) {
            when (string[p]) {
              't',
              'n',
              '#',
              '@',
              '?',
              '"',
              '\'',
              '\\' -> {}
              'u' -> {
                var i = 0
                while (i < 4 && p + 1 < len) {
                  p++
                  i++
                  val h = string[p]
                  if ((h < '0' || h > '9') && (h < 'a' || h > 'f') && (h < 'A' || h > 'F')) {
                    val location = context.getLocation(textNode, p, p + 1)
                    context.report(
                      STRING_ESCAPING,
                      element,
                      location,
                      "Bad character in \\\\u unicode escape sequence",
                    )
                    return
                  }
                }
              }
              else -> {}
            }
            p++
          }
        }
        len -= p - s
        s = p
      }
    }
  }
}
