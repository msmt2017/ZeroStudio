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
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.sdklib.AndroidVersion.VersionCodes.S
import com.android.tools.lint.checks.ScopedStorageDetector.Companion.ATTR_MAX_SDK_VERSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import org.w3c.dom.Element

class FineLocationDetector : Detector(), XmlScanner {
  override fun checkMergedProject(context: Context) {
    if (context.mainProject.targetSdk < S) return
    var fineElement: Element? = null
    var coarseElement: Element? = null
    val manifest = context.mainProject.mergedManifest ?: return
    for (node in manifest.documentElement) {
      if (node.tagName != TAG_USES_PERMISSION) continue
      when (node.getAttributeNS(ANDROID_URI, ATTR_NAME)) {
        FINE_LOCATION_PERMISSION -> {
          if (node.permissionApplies()) {
            fineElement = node
          }
        }
        COARSE_LOCATION_PERMISSION -> {
          if (node.permissionApplies()) {
            coarseElement = node
          }
        }
      }
    }
    if (fineElement != null && coarseElement == null) {
      context.report(
        Incident(
          ISSUE,
          context.getLocation(fineElement),
          "If you need access to FINE location, you must request both " +
            "`ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`",
        )
      )
    }
  }

  private fun Element.permissionApplies(): Boolean {
    val max = getAttributeNS(ANDROID_URI, ATTR_MAX_SDK_VERSION).toIntOrNull() ?: return true
    return max >= S
  }

  companion object {
    private const val FINE_LOCATION_PERMISSION = "android.permission.ACCESS_FINE_LOCATION"
    private const val COARSE_LOCATION_PERMISSION = "android.permission.ACCESS_COARSE_LOCATION"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CoarseFineLocation",
        //noinspection LintImplTextFormat
        briefDescription = "Cannot use `ACCESS_FINE_LOCATION` without `ACCESS_COARSE_LOCATION`",
        explanation =
          """
                If your app requires access to FINE location, on Android 12 and higher you must \
                now request both FINE and COARSE. Users will have the option to grant only COARSE \
                location. Ensure your app can work with just COARSE location.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = Implementation(FineLocationDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
      )
  }
}
