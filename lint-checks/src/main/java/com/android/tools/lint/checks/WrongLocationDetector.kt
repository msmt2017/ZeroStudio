/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.SdkConstants.TAG_RESOURCES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

/** Looks for problems with XML files being placed in the wrong folder. */
class WrongLocationDetector : LayoutDetector() {
  companion object Issues {
    /** Main issue investigated by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "WrongFolder",
        briefDescription = "Resource file in the wrong `res` folder",
        explanation =
          """
            Resource files are sometimes placed in the wrong folder, and it can lead to subtle bugs that are \
            hard to understand. This check looks for problems in this area, such as attempting to place a \
            layout "alias" file in a `layout/` folder rather than the `values/` folder where it belongs.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        implementation =
          Implementation(WrongLocationDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement
    if (root != null && root.tagName == TAG_RESOURCES) {
      context.report(
        ISSUE,
        root,
        context.getElementLocation(root),
        "This file should be placed in a `values`/ folder, not a `layout`/ folder",
      )
    }
  }
}
