/*
 * Copyright (C) 2024 The Android Open Source Project
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
import com.android.SdkConstants.VALUE_FALSE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope.Companion.MANIFEST_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Element

/** Check which looks for (likely) unnecessary required features in the manifest */
class RequiredFeatureDetector : ResourceXmlDetector() {

  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_USES_FEATURE)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val notRequired = element.getAttributeNS(ANDROID_URI, ATTR_REQUIRED) != VALUE_FALSE
    if (!notRequired) {
      return
    }
    val featureNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    val feature = featureNode.value
    if (isUnnecessaryRequiredFeature(feature)) {
      val message =
        "Consider whether this feature (`$feature`) really is required for the app to function; " +
          "you can set `android:required=\"false\"` to " +
          "indicate that the feature is used but not required"
      val fix = fix().set(ANDROID_URI, ATTR_REQUIRED, VALUE_FALSE).build()
      context.report(ISSUE, featureNode, context.getValueLocation(featureNode), message, fix)
    }
  }

  private fun isUnnecessaryRequiredFeature(feature: String): Boolean {
    return when (feature) {
      "android.hardware.camera.autofocus",
      "android.hardware.camera.flash",
      "android.hardware.location.gps",
      "android.hardware.nfc",
      "android.hardware.nfc.hce",
      "android.hardware.screen.portrait",
      "android.hardware.telephony",
      "android.hardware.touchscreen",
      "android.hardware.touchscreen.multitouch" -> true
      else -> false
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      create(
        id = "UnnecessaryRequiredFeature",
        briefDescription = "Potentially unnecessary required feature",
        explanation =
          """
          Avoid unnecessarily requiring features that may exclude your app from being served onto \
          devices it might otherwise support.

          Consider whether your application can function adequately without restrictive feature \
          requirements by setting these to `android:required="false"`. By doing so, you can increase \
          the availability of your app to a broader set of devices, including tablets, laptops and cars.
          """,
        category = Category.USABILITY,
        priority = 2,
        severity = Severity.WARNING,
        implementation = Implementation(RequiredFeatureDetector::class.java, MANIFEST_SCOPE),
        androidSpecific = true,
      )
  }
}
