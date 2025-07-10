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
import com.android.SdkConstants.ATTR_BACKGROUND
import com.android.SdkConstants.ATTR_CLICKABLE
import com.android.SdkConstants.ATTR_FOCUSABLE
import com.android.SdkConstants.ATTR_LAYOUT_WEIGHT
import com.android.SdkConstants.ATTR_SCALE_TYPE
import com.android.SdkConstants.IMAGE_VIEW
import com.android.SdkConstants.LINEAR_LAYOUT
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import org.w3c.dom.Element

/** Checks whether the current node can be replaced by a TextView using compound drawables. */
class UseCompoundDrawableDetector : LayoutDetector() {

  override fun getApplicableElements(): Collection<String> {
    return listOf(LINEAR_LAYOUT)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    // Look for exactly 2 children
    val first = getFirstSubTag(element) ?: return
    val second = getNextTag(first) ?: return
    if (getNextTag(second) != null) {
      return
    }

    if (
      (first.tagName == IMAGE_VIEW && second.tagName == TEXT_VIEW && canCombineImage(first)) ||
        (second.tagName == IMAGE_VIEW && first.tagName == TEXT_VIEW && canCombineImage(second))
    ) {
      // If the layout has a background, ignore since it would disappear from
      // the TextView
      if (element.hasAttributeNS(ANDROID_URI, ATTR_BACKGROUND)) {
        return
      }

      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "This tag and its children can be replaced by one `<TextView/>` and " +
          "a compound drawable",
      )
    }
  }

  companion object {
    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "UseCompoundDrawables",
        briefDescription = "Node can be replaced by a `TextView` with compound drawables",
        explanation =
          """
                A `LinearLayout` which contains an `ImageView` and a `TextView` can be more \
                efficiently handled as a compound drawable (a single TextView, using the \
                `drawableTop`, `drawableLeft`, `drawableRight` and/or `drawableBottom` \
                attributes to draw one or more images adjacent to the text).

                If the two widgets are offset from each other with margins, this can be \
                replaced with a `drawablePadding` attribute.

                There's a lint quickfix to perform this conversion in the Eclipse plugin.
                """,
        category = Category.PERFORMANCE,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(UseCompoundDrawableDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )

    private fun canCombineImage(image: Element): Boolean {
      if (
        image.hasAttributeNS(ANDROID_URI, ATTR_LAYOUT_WEIGHT) ||
          image.hasAttributeNS(ANDROID_URI, ATTR_CLICKABLE) ||
          image.hasAttributeNS(ANDROID_URI, ATTR_FOCUSABLE)
      ) {
        return false
      }

      // Certain scale types cannot be done with compound drawables
      val scaleType = image.getAttributeNS(ANDROID_URI, ATTR_SCALE_TYPE)

      // For now, ignore if any scale type is set
      return !(scaleType != null && !scaleType.isEmpty())
    }
  }
}
