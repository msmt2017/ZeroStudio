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
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import org.w3c.dom.Element

class HighSensorSamplingRateDetector : Detector(), XmlScanner {
  override fun getApplicableElements() = setOf(TAG_USES_PERMISSION)

  override fun visitElement(context: XmlContext, element: Element) {
    val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
    if (name != HIGHER_SENSOR_SAMPLING_RATE) return
    context.report(
      Incident(
        ISSUE,
        context.getValueLocation(element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)),
        "Most apps don't need access to high sensor sampling rate.",
      ),
      targetSdkAtLeast(S),
    )
  }

  companion object {
    private const val HIGHER_SENSOR_SAMPLING_RATE = "android.permission.HIGH_SAMPLING_RATE_SENSORS"

    @JvmField
    val ISSUE =
      Issue.create(
        "HighSamplingRate",
        briefDescription = "High sensor sampling rate",
        explanation =
          """
                Most apps don't need access to high sensor sampling rate. Double check your use \
                case to ensure your app absolutely needs access to sensor sampling rate > 200Hz. \
                Be prepared for your app to be rejected from listing on Play Store until your use \
                case for high sensor sampling rate has been reviewed and validated by the policy \
                team.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(HighSensorSamplingRateDetector::class.java, Scope.MANIFEST_SCOPE),
      )
  }
}
