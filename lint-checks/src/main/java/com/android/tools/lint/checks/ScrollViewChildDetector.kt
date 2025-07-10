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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_LAYOUT_HEIGHT
import com.android.SdkConstants.ATTR_LAYOUT_WIDTH
import com.android.SdkConstants.HORIZONTAL_SCROLL_VIEW
import com.android.SdkConstants.SCROLL_VIEW
import com.android.SdkConstants.VALUE_FILL_PARENT
import com.android.SdkConstants.VALUE_MATCH_PARENT
import com.android.SdkConstants.WEB_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.childrenIterator
import com.android.utils.iterator
import org.w3c.dom.Element

/**
 * Check which looks at the children of ScrollViews and ensures that they fill/match the parent
 * width instead of setting wrap_content.
 */
class ScrollViewChildDetector : LayoutDetector() {
  override fun getApplicableElements(): Collection<String> {
    return listOf(SCROLL_VIEW, HORIZONTAL_SCROLL_VIEW)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val isHorizontal = HORIZONTAL_SCROLL_VIEW == element.tagName
    val attributeName = if (isHorizontal) ATTR_LAYOUT_WIDTH else ATTR_LAYOUT_HEIGHT
    for (child in element) {
      val sizeNode = child.getAttributeNodeNS(ANDROID_URI, attributeName) ?: return
      val value = sizeNode.value
      if (
        (VALUE_FILL_PARENT == value || VALUE_MATCH_PARENT == value) &&
          // If there is a child WebView, don't report, since that would immediately
          // trigger [WebViewDetector.ISSUE] !
          child.childrenIterator().asSequence().none { it.nodeName == WEB_VIEW }
      ) {
        val msg = "This ${child.tagName} should use `android:$attributeName=\"wrap_content\"`"
        context.report(ISSUE, sizeNode, context.getLocation(sizeNode), msg)
      }
    }
  }

  companion object {
    /** The main issue discovered by this detector */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "ScrollViewSize",
        briefDescription = "ScrollView size validation",
        // TODO add a better explanation here!
        explanation =
          """
          ScrollView children must set their `layout_width` or `layout_height` attributes \
          to `wrap_content` rather than `fill_parent` or `match_parent` in the scrolling \
          dimension.
          """,
        category = Category.CORRECTNESS,
        priority = 7,
        severity = Severity.WARNING,
        implementation =
          Implementation(ScrollViewChildDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }
}
