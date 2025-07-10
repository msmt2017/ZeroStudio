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

import com.android.SdkConstants
import com.android.SdkConstants.DOT_XML
import com.android.SdkConstants.TAG_ADAPTIVE_ICON
import com.android.SdkConstants.TAG_APPLICATION
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.XmlUtils
import com.android.utils.subtag
import org.w3c.dom.Element

class MonochromeLauncherIconDetector : Detector(), XmlScanner {

  companion object {

    private val IMPLEMENTATION =
      Implementation(MonochromeLauncherIconDetector::class.java, Scope.RESOURCE_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "MonochromeLauncherIcon",
        briefDescription = "Monochrome icon is not defined",
        explanation =
          """
                If `android:roundIcon` and `android:icon` are both in your manifest, \
                you must either remove the reference to `android:roundIcon` if it is not needed; or, supply \
                the monochrome icon in the drawable defined by the `android:roundIcon` and `android:icon` attribute.

                For example, if `android:roundIcon` and `android:icon` are both in the manifest, a launcher might choose to use \
                `android:roundIcon` over `android:icon` to display the adaptive app icon. Therefore, your themed application icon\
                will not show if your monochrome attribute is not also specified in `android:roundIcon`.""",
        category = Category.ICONS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.DRAWABLE || folderType == ResourceFolderType.MIPMAP
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_ADAPTIVE_ICON)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      TAG_ADAPTIVE_ICON -> {
        if (XmlUtils.getFirstSubTagByName(element, "monochrome") != null) return
        val currentIconName = context.file.name.removeSuffix(DOT_XML)

        val applicationTag =
          context.project.manifestDom?.documentElement?.subtag(TAG_APPLICATION) ?: return
        val foundIconName =
          applicationTag
            .getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ICON)
            .substringAfterLast('/')
        val foundRoundIconName =
          applicationTag
            .getAttributeNS(SdkConstants.ANDROID_URI, SdkConstants.ATTR_ROUND_ICON)
            .substringAfterLast('/')

        if (currentIconName == foundIconName || currentIconName == foundRoundIconName) {
          val iconDescription = if (currentIconName == foundIconName) "icon" else "roundIcon"
          context.report(
            Incident(
              ISSUE,
              scope = element,
              location = context.getLocation(element),
              "The application adaptive $iconDescription is missing a monochrome tag",
            )
          )
        }
      }
    }
  }
}
