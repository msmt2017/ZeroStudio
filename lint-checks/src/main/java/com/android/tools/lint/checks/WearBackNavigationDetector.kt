/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

private const val ATTR_SWIPE_TO_DISMISS = "android:windowSwipeToDismiss"

class WearBackNavigationDetector : WearDetector(), XmlScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(WearBackNavigationDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
          id = "WearBackNavigation",
          briefDescription = "Wear: Disabling Back navigation",
          explanation =
            """
              Disabling swipe-to-dismiss is generally not recommended for Wear applications because \
              the user expects to dismiss any screen with a swipe. \
              If your activity does not require swipe-to-dismiss to be disabled, the recommendation is to \
              remove the `android:windowSwipeToDismiss` attribute from your theme declaration.
            """,
          category = Category.USABILITY,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
          enabledByDefault = true,
          androidSpecific = true,
        )
        .addMoreInfo(
          "https://developer.android.com/training/wearables/views/exit#disabling-swipe-to-dismiss"
        )
  }

  override fun appliesTo(folderType: ResourceFolderType) =
    isWearProject && ResourceFolderType.VALUES == folderType

  override fun getApplicableElements(): Collection<String> = listOf(SdkConstants.TAG_ITEM)

  override fun visitElement(context: XmlContext, element: Element) {
    val nameAttribute = element.getAttribute(SdkConstants.ATTR_NAME)
    if (
      nameAttribute == ATTR_SWIPE_TO_DISMISS &&
        element.textContent?.trim() == SdkConstants.VALUE_FALSE
    ) {
      val fix =
        fix()
          .name("Delete `android:windowSwipeToDismiss` from theme")
          .replace()
          .with("")
          .autoFix()
          .build()

      context.report(
        ISSUE,
        element,
        context.getLocation(element),
        "Disabling swipe-to-dismiss is generally not recommended for Wear applications",
        fix,
      )
    }
  }
}
