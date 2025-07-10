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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator

/**
 * On Android 14 devices, apps requesting READ_MEDIA_IMAGES and/or READ_MEDIA_VIDEO should request
 * READ_MEDIA_VISUAL_USER_SELECTED to better support selected photos access. The
 * READ_MEDIA_VISUAL_USER_SELECTED permission must be declared (but not requested) to allow apps to
 * better control the UX of photos selection. The potential issue is apps adding this new permission
 * without changing their UX and permission logic to accommodate this behavior change.
 */
class SelectedPhotoAccessDetector : Detector(), XmlScanner {

  companion object {

    @JvmField
    val ISSUE =
      Issue.create(
          id = "SelectedPhotoAccess",
          briefDescription = "Behavior change when requesting photo library access",
          explanation =
            """
                  Selected Photo Access is a new ability for users to share partial access to their photo library \
                  when apps request access to their device storage on Android 14+.

                  Instead of letting the system manage the selection lifecycle, we recommend you adapt \
                  your app to handle partial access to the photo library.
                  """,
          category = Category.CORRECTNESS,
          priority = 5,
          severity = Severity.WARNING,
          implementation =
            Implementation(SelectedPhotoAccessDetector::class.java, Scope.MANIFEST_SCOPE),
          androidSpecific = true,
        )
        .addMoreInfo(
          "https://developer.android.com/about/versions/14/changes/partial-photo-video-access"
        )
  }

  override fun checkMergedProject(context: Context) {
    if (context.mainProject.isLibrary) return
    if (context.mainProject.targetSdk < VersionCodes.TIRAMISU) return
    val readMediaAttributeUsage: MutableList<Incident> = mutableListOf()
    val manifest = context.mainProject.mergedManifest ?: return
    for (node in manifest.documentElement) {
      if (node.tagName != TAG_USES_PERMISSION) continue
      val attributeNodeNS = node.getAttributeNodeNS(SdkConstants.ANDROID_URI, ATTR_NAME) ?: continue
      when (attributeNodeNS.value) {
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO" -> {
          readMediaAttributeUsage +=
            Incident(
              ISSUE,
              node,
              context.getLocation(attributeNodeNS, LocationType.VALUE),
              "Your app is currently not handling Selected Photos Access introduced in Android 14+",
            )
        }
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED" -> {
          return
        }
      }
    }
    for (incident in readMediaAttributeUsage) {
      context.report(incident)
    }
  }
}
