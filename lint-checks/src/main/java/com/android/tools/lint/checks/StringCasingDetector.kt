/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_TRANSLATABLE
import com.android.SdkConstants.TAG_STRING
import com.android.SdkConstants.VALUE_FALSE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.formatList
import com.android.tools.lint.detector.api.getLocale
import com.android.utils.Pair
import java.util.ArrayList
import java.util.HashMap
import java.util.Locale
import org.w3c.dom.Element
import org.w3c.dom.Node

/** String duplication when case conversion is more efficient. */
class StringCasingDetector : ResourceXmlDetector() {

  companion object {
    private val IMPLEMENTATION_XML =
      Implementation(StringCasingDetector::class.java, Scope.ALL_RESOURCES_SCOPE)

    /** Whether there are any duplicate strings, including capitalization adjustments. */
    @JvmField
    val DUPLICATE_STRINGS =
      Issue.create(
        id = "DuplicateStrings",
        briefDescription = "Duplicate Strings",
        explanation =
          """
                Duplicate strings can make applications larger unnecessarily.

                This lint check looks for duplicate strings, including differences for strings \
                where the only difference is in capitalization. Title casing and all uppercase can \
                all be adjusted in the layout or in code.
                """,
        implementation = IMPLEMENTATION_XML,
        moreInfo =
          "https://developer.android.com/reference/android/widget/TextView.html#attr_android:inputType",
        category = Category.APP_SIZE,
        priority = 2,
        severity = Severity.WARNING,
        enabledByDefault = false,
      )
  }

  /*
   * Map of all locale,strings in lower case, to their raw elements to ensure that there are no
   * duplicate strings.
   */
  private val allStrings = HashMap<Pair<String, String>, MutableList<StringDeclaration>>()

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_STRING)
  }

  private fun Element.containsElements(): Boolean {
    var curr = firstChild
    while (curr != null) {
      if (curr.nodeType == Node.ELEMENT_NODE) {
        return true
      }
      curr = curr.nextSibling
    }
    return false
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val text = element.textContent
    if (text.isNotEmpty()) {
      // Ignore strings that contain markup
      if (element.containsElements()) {
        return
      }

      checkTextNode(context, element, StringFormatDetector.stripQuotes(text))
    }
  }

  private fun checkTextNode(context: XmlContext, element: Element, text: String) {
    if (VALUE_FALSE == element.getAttribute(ATTR_TRANSLATABLE)) {
      return
    }

    val locale = getLocale(context)
    val key =
      if (locale != null) Pair.of(locale.full, text.lowercase(Locale.forLanguageTag(locale.tag)))
      else Pair.of("default", text.lowercase(Locale.US))
    val handle = context.createLocationHandle(element)
    handle.clientData = element
    val handleList = allStrings.getOrDefault(key, ArrayList())
    handleList.add(StringDeclaration(element.getAttribute(ATTR_NAME), text, handle))
    allStrings[key] = handleList
  }

  data class StringDeclaration(val name: String, val text: String, val location: Location.Handle)

  override fun afterCheckRootProject(context: Context) {
    for ((_, duplicates) in allStrings) {
      if (duplicates.size > 1) {
        var firstLocation: Location? = null
        var prevLocation: Location? = null
        var prevString = ""
        var caseVaries = false
        val names = mutableListOf<String>()
        for (duplicate in duplicates) {
          names.add(duplicate.name)
          val string = duplicate.text
          val location = duplicate.location.resolve()
          if (prevLocation == null) {
            firstLocation = location
          } else {
            prevLocation.secondary = location
            location.message = "Duplicates value in `${names[0]}`"
            location.setSelfExplanatory(false)
            if (string != prevString) {
              caseVaries = true
              location.message +=
                " (case varies, but you can use " +
                  "`android:inputType` or `android:capitalize` in the " +
                  "presentation)"
            }
          }

          prevLocation = location
          prevString = string
        }

        firstLocation ?: continue

        val nameList = formatList(names.map { "`$it`" }, useConjunction = true)
        var message = "Duplicate string value `$prevString`, used in $nameList"

        if (caseVaries) {
          message +=
            ". Use `android:inputType` or `android:capitalize` " +
              "to treat these as the same and avoid string duplication."
        }
        context.report(DUPLICATE_STRINGS, firstLocation, message)
      }
    }
  }
}
