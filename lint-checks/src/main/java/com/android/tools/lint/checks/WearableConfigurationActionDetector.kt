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
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_META_DATA
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.minSdkLessThan
import com.android.utils.childrenIterator
import com.android.utils.visitElements
import org.w3c.dom.Element

class WearableConfigurationActionDetector : Detector(), XmlScanner {
  companion object Issues {

    const val WEARABLE_CONFIGURATION_ACTION =
      "com.google.android.wearable.watchface.wearableConfigurationAction"

    const val CATEGORY_WEARABLE_CONFIGURATION =
      "com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION"

    const val WATCH_FACE_EDITOR_ACTION = "androidx.wear.watchface.editor.action.WATCH_FACE_EDITOR"

    @JvmField
    val ACTION_DUPLICATE =
      Issue.create(
        id = "WearableActionDuplicate",
        briefDescription = "Duplicate watch face configuration activities found",
        explanation =
          """
                If and only if a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, \
                there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` \
                (with com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION if minSdkVersion is less than 30).
            """,
        moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )

    @JvmField
    val CONFIGURATION_ACTION =
      Issue.create(
        id = "WearableConfigurationAction",
        briefDescription = "Wear configuration action metadata must match an activity",
        explanation =
          """
                Only when a watch face service defines `wearableConfigurationAction` metadata, with the value `WATCH_FACE_EDITOR`, \
                there should be an activity in the same package, which has an intent filter for `WATCH_FACE_EDITOR` \
                (with `com.google.android.wearable.watchface.category.WEARABLE_CONFIGURATION` if `minSdkVersion` is less than 30).
            """,
        moreInfo = "https://developer.android.com/training/wearables/watch-faces/configuration",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(WearableConfigurationActionDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )
  }

  private var duplicateAction: Element? = null
  private var foundAction: Element? = null
  private var foundCategory: Element? = null
  private var foundMetaData: Element? = null

  override fun checkMergedProject(context: Context) {
    context.project.buildVariant
      ?.mainArtifact
      ?.findCompileDependency("androidx.wear.watchface:watchface") ?: return
    beforeScanningManifest()
    val document = context.mainProject.mergedManifest?.documentElement ?: return
    document.visitElements { visitManifestElement(it) }
    afterScanningManifest(context)
  }

  private fun beforeScanningManifest() {
    duplicateAction = null
    foundAction = null
    foundCategory = null
    foundMetaData = null
  }

  private fun afterScanningManifest(context: Context) {
    // convert vars into vals for nullability checks
    val duplicateAction = duplicateAction
    val foundAction = foundAction
    val foundMetaData = foundMetaData
    val foundCategory = foundCategory
    if (duplicateAction != null) {
      val location = context.getLocation(duplicateAction)
      context.report(
        Incident(
          ACTION_DUPLICATE,
          location.source ?: duplicateAction,
          location,
          "Duplicate watch face configuration activities found",
        )
      )
    }
    if (foundMetaData != null && foundAction == null) {
      val location = context.getLocation(foundMetaData.getAttributeNodeNS(ANDROID_URI, "name"))
      context.report(
        Incident(
          CONFIGURATION_ACTION,
          location.source ?: foundMetaData,
          location,
          "Watch face configuration activity is missing",
        )
      )
    } else if (foundMetaData != null && foundAction != null && foundCategory == null) {
      val location = context.getLocation(foundAction)
      context.report(
        Incident(
          CONFIGURATION_ACTION,
          location.source ?: foundAction,
          location,
          "Watch face configuration tag is required",
        ),
        minSdkLessThan(30),
      )
    } else if (foundAction != null && foundMetaData == null) {
      val location = context.getLocation(foundAction.getAttributeNodeNS(ANDROID_URI, "name"))
      context.report(
        Incident(
          CONFIGURATION_ACTION,
          location.source ?: foundAction,
          location,
          "`wearableConfigurationAction` metadata is missing",
        ),
        minSdkLessThan(30),
      )
    }
  }

  private fun visitManifestElement(element: Element): Boolean {
    when (element.tagName) {
      TAG_META_DATA -> visitMetaData(element)
      TAG_INTENT_FILTER -> visitIntentFilterTag(element)
    }
    return false // not done looking
  }

  private fun visitMetaData(metaData: Element) {
    if (
      metaData.getAttributeNS(ANDROID_URI, ATTR_NAME) == WEARABLE_CONFIGURATION_ACTION &&
        metaData.getAttributeNS(ANDROID_URI, ATTR_VALUE) == WATCH_FACE_EDITOR_ACTION
    ) {
      foundMetaData = metaData
    }
  }

  private fun visitIntentFilterTag(intentFilter: Element) {
    var tmpCategory: Element? = null
    var tmpAction: Element? = null
    for (child in intentFilter.childrenIterator()) {
      child as? Element ?: continue
      when (child.tagName) {
        TAG_ACTION -> {
          if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) == WATCH_FACE_EDITOR_ACTION) {
            tmpAction = child
          }
        }
        TAG_CATEGORY -> {
          if (child.getAttributeNS(ANDROID_URI, ATTR_NAME) == CATEGORY_WEARABLE_CONFIGURATION) {
            tmpCategory = child
          }
        }
      }
    }
    // save a category only if a corresponding action is found
    if (tmpAction != null && foundAction != null) {
      duplicateAction = tmpAction
    } else if (tmpAction != null && foundAction == null) {
      foundAction = tmpAction
      foundCategory = tmpCategory
    }
  }
}
