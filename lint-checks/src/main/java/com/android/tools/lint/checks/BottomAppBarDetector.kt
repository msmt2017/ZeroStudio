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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/**
 * Check which looks for potential errors in declarations of BottomAppBar, such as having the wrong
 * parent.
 */
class BottomAppBarDetector : LayoutDetector() {
  override fun getApplicableElements(): Collection<String> {
    return listOf(OLD_BOTTOM_APP_BAR, NEW_BOTTOM_APP_BAR)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val parentLayout = element.parentNode?.nodeName
    if (parentLayout != OLD_COORDINATOR_LAYOUT && parentLayout != NEW_COORDINATOR_LAYOUT) {
      val coordinatorLayout =
        if (element.tagName == OLD_BOTTOM_APP_BAR) {
          OLD_COORDINATOR_LAYOUT
        } else {
          NEW_COORDINATOR_LAYOUT
        }
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "This `BottomAppBar` must be wrapped in a `CoordinatorLayout` (`$coordinatorLayout`)",
      )
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(BottomAppBarDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    /** Wrong parent etc. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "BottomAppBar",
        briefDescription = "BottomAppBar Problems",
        explanation =
          """
            The `BottomAppBar` widget must be placed within a `CoordinatorLayout`.
            """,
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    private const val OLD_BOTTOM_APP_BAR = "android.support.design.bottomappbar.BottomAppBar"
    private const val NEW_BOTTOM_APP_BAR = "com.google.android.material.bottomappbar.BottomAppBar"
    private const val OLD_COORDINATOR_LAYOUT = "android.support.design.widget.CoordinatorLayout"
    private const val NEW_COORDINATOR_LAYOUT = "androidx.coordinatorlayout.widget.CoordinatorLayout"
  }
}
