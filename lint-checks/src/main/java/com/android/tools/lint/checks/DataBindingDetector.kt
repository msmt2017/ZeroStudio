/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.isDataBindingExpression
import java.lang.Integer.min
import org.w3c.dom.Attr

class DataBindingDetector : LayoutDetector() {
  override fun getApplicableAttributes(): Collection<String> {
    return ALL
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val elementName = attribute.ownerElement.tagName
    val attributeName = attribute.name

    if (isDataBindingExpression(attribute.value) || isVariableType(elementName, attributeName)) {
      val rawText = context.getContents() ?: return
      val start = context.parser.getNodeStartOffset(context, attribute)
      if (start == -1) {
        return
      }
      val end = min(rawText.length, context.parser.getNodeEndOffset(context, attribute))
      var isContained = false
      for (index in start until end) {
        if (rawText[index] == '<') {
          isContained = true
          break
        }
      }

      if (isContained) {
        val fix = fix().name("Change '<' to '&lt;'").replace().text("<").with("&lt;").build()
        context.report(
          ESCAPE_XML,
          attribute,
          context.getValueLocation(attribute),
          "`<` must be escaped (as `&lt;`) in attribute values",
          fix,
        )
      }
    }
  }

  private fun isVariableType(elementName: String, attributeName: String): Boolean {
    return elementName == SdkConstants.TAG_VARIABLE && attributeName == SdkConstants.ATTR_TYPE
  }

  companion object {
    /** The main issue discovered by this detector. */
    @JvmField
    val ESCAPE_XML =
      Issue.create(
        id = "XmlEscapeNeeded",
        briefDescription = "Missing XML Escape",
        explanation =
          """
              When a string contains characters that have special usage in XML, \
              you must escape the characters.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(DataBindingDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }
}
