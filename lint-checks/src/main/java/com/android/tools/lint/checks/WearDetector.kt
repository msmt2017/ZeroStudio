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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.utils.iterator
import com.android.xml.AndroidManifest
import org.w3c.dom.Element

private fun isWearFeature(element: Element) =
  element.tagName == SdkConstants.TAG_USES_FEATURE &&
    element
      .getAttributeNS(SdkConstants.ANDROID_URI, AndroidManifest.ATTRIBUTE_NAME)
      .equals(FEATURE_WATCH)

private const val FEATURE_WATCH = "android.hardware.type.watch"

open class WearDetector : Detector() {

  var isWearProject = false

  override fun beforeCheckEachProject(context: Context) {
    super.beforeCheckEachProject(context)
    isWearProject = isWearProject(context)
  }

  companion object {
    fun isWearProject(context: Context) =
      containsWearFeature(
        if (context.isGlobalAnalysis()) context.mainProject.mergedManifest?.documentElement
        else context.project.mergedManifest?.documentElement
      )

    fun containsWearFeature(manifest: Element?): Boolean {
      if (manifest == null) {
        return false
      }
      for (element in manifest) {
        if (isWearFeature(element)) return true
      }
      return false
    }
  }
}
