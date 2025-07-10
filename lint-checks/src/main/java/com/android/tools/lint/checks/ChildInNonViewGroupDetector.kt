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

import com.android.SdkConstants.VIEW_GROUP
import com.android.tools.lint.client.api.SdkInfo
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.isLayoutMarkerTag
import com.android.utils.iterator
import org.w3c.dom.Element

/**
 * Check which makes sure that only views that are `android.view.ViewGroup` have children.
 *
 * <ImageView android:layout_width="wrap_content" android:layout_height="wrap_content"
 * android:src="@drawable/icon" > <TextView /> </ImageView>
 *
 * Attempting to inflate a layout above, will otherwise lead to the following exception:
 *
 * java.lang.ClassCastException: android.widget.ImageView cannot be cast to android.view.ViewGroup
 */
class ChildInNonViewGroupDetector : LayoutDetector() {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(ChildInNonViewGroupDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    /** The main issue discovered by this detector. */
    @JvmField
    val CHILD_IN_NON_VIEW_GROUP_ISSUE =
      Issue.create(
        id = "ChildInNonViewGroup",
        briefDescription = "Only view groups can have children",
        explanation =
          """
            Only classes inheriting from `ViewGroup` can have children.
            """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableElements(): Collection<String> = ALL

  override fun visitElement(context: XmlContext, element: Element) {
    context.explore(element)
  }

  private fun XmlContext.explore(element: Element) {
    if (isLayoutMarkerTag(element)) {
      return
    }
    for (child in element) {
      if (isLayoutMarkerTag(child)) continue
      if (!sdkInfo.isChildOfViewGroup(child)) {
        reportWrongParent(child)
      }
    }
  }

  private fun XmlContext.reportWrongParent(element: Element) =
    report(
      CHILD_IN_NON_VIEW_GROUP_ISSUE,
      element,
      getNameLocation(element),
      "A ${element.parentNode.nodeName} should have no children declared in XML",
    )

  private fun SdkInfo.isChildOfViewGroup(element: Element): Boolean =
    isSubViewOf(VIEW_GROUP, element.parentNode.nodeName)
}
