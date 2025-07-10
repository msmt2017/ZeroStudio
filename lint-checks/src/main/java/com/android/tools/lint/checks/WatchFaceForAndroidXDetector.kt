/*
 * Copyright (C) 2021 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import org.w3c.dom.Element

class WatchFaceForAndroidXDetector : Detector(), XmlScanner {
  companion object Issues {
    const val WATCH_FACE_META_DATA_NAME =
      "com.google.android.wearable.watchface.wearableConfigurationAction"

    const val WATCH_FACE_EDITOR_ACTION = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "WatchFaceForAndroidX",
        briefDescription = "AndroidX watch faces must use action `WATCH_FACE_EDITOR`",
        explanation =
          """
                If the package depends on `androidx.wear:wear-watchface`, \
                and an AndroidX watch face declares the `wearableConfigurationAction` metadata, \
                its value should be `androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR`.
            """,
        moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(WatchFaceForAndroidXDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )
  }

  override fun getApplicableElements() = listOf(TAG_META_DATA)

  private fun visitMetaData(context: XmlContext, metaData: Element) {
    if (
      metaData.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_META_DATA_NAME &&
        metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE) != WATCH_FACE_EDITOR_ACTION
    ) {
      context.project.buildVariant
        ?.mainArtifact
        ?.findCompileDependency("androidx.wear.watchface:watchface") ?: return
      val fix = fix().set().attribute("value").value(WATCH_FACE_EDITOR_ACTION).android().build()
      context.report(
        Incident(
          ISSUE,
          metaData,
          metaData.getAttributeNodeNS(ANDROID_URI, "value")?.let { context.getValueLocation(it) }
            ?: context.getLocation(metaData),
          "Watch face configuration action must be set to WATCH_FACE_EDITOR for an AndroidX watch face",
          fix,
        )
      )
    }
  }

  override fun visitElement(context: XmlContext, element: Element) {
    when (element.tagName) {
      TAG_META_DATA -> visitMetaData(context, element)
    }
  }
}
