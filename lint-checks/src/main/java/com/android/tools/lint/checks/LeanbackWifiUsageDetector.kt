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
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.TAG_USES_FEATURE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.VALUE_FALSE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import org.w3c.dom.Element

class LeanbackWifiUsageDetector : Detector(), XmlScanner {
  override fun checkMergedProject(context: Context) {
    var wifiFeatureNode: Element? = null
    val document = context.mainProject.mergedManifest?.documentElement ?: return
    var wifiPermissionsNode: Element? = null

    // Only applies if manifest has <uses-feature> which includes android.software.leanback
    var hasLeanBack = false
    for (element in document) {
      when (element.tagName) {
        TAG_USES_FEATURE -> {
          val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (name == LEANBACK_ATTR_NAME) {
            hasLeanBack = true
          } else if (name == WIFI_FEATURE_NAME) {
            wifiFeatureNode = element
          }
        }
        TAG_USES_PERMISSION -> {
          val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (isWifiStatePermission(name)) {
            wifiPermissionsNode = element
          }
        }
      }
    }

    if (!hasLeanBack) {
      return
    }

    val wifiFeatureNodeRequired =
      wifiFeatureNode?.let { wifiNode ->
        wifiNode.getAttributeNS(ANDROID_URI, ATTR_REQUIRED) != VALUE_FALSE
      } ?: false

    if (wifiFeatureNode != null) {
      if (wifiFeatureNodeRequired) {
        context.report(
          ISSUE,
          context.getLocation(wifiFeatureNode),
          "Requiring `android.hardware.wifi` limits app availability on TVs that support only Ethernet",
        )
      }
    } else if (wifiPermissionsNode != null) {
      context.report(
        ISSUE,
        context.getLocation(wifiPermissionsNode),
        "Requiring Wifi permissions limits app availability on TVs that support only Ethernet",
      )
    }
  }

  companion object {
    private const val LEANBACK_ATTR_NAME = "android.software.leanback"
    private const val WIFI_FEATURE_NAME = "android.hardware.wifi"

    private fun isWifiStatePermission(s: String): Boolean {
      return when (s) {
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE" -> true
        else -> false
      }
    }

    @JvmField
    val ISSUE =
      Issue.create(
        id = "LeanbackUsesWifi",
        briefDescription = "Using android.hardware.wifi on TV",
        explanation =
          """
                WiFi is not required for Android TV and many devices connect to the internet via \
                alternative methods e.g. Ethernet.

                If your app is not focused specifically on WiFi functionality and only wishes to \
                connect to the internet, please modify your Manifest to contain: \
                `<uses-feature android:name="android.hardware.wifi" android:required="false" />`

                Un-metered or non-roaming connections can be detected in software using \
                `NetworkCapabilities#NET_CAPABILITY_NOT_METERED` and \
                `NetworkCapabilities#NET_CAPABILITY_NOT_ROAMING.`
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(LeanbackWifiUsageDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )
  }
}
