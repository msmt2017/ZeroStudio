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

import com.android.AndroidXConstants.MOTION_LAYOUT
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.VIEW
import com.android.SdkConstants.VIEW_MERGE
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.SdkInfo
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.isLayoutMarkerTag
import com.android.utils.iterator
import org.w3c.dom.Element

/** Detector to ensure all Views declared within a MotionLayout tag have an assigned ID. */
class MotionLayoutIdDetector : LayoutDetector() {

  override fun getApplicableElements() = listOf(MOTION_LAYOUT.oldName(), MOTION_LAYOUT.newName())

  override fun visitElement(context: XmlContext, element: Element) {
    val evaluator = context.client.getUastParser(context.project).evaluator
    val sdkInfo = context.sdkInfo

    for (child in element) {
      val elementTagName = child.tagName
      // TODO: Check if layout referenced by <include> has an ID
      if (
        TAG_INCLUDE == elementTagName ||
          // TODO: Check if the views referenced by <merge> have ID
          VIEW_MERGE == elementTagName ||
          // Ignore non-view tags
          isLayoutMarkerTag(elementTagName) ||
          // Skip if the tag is not an actual View instance
          !isView(elementTagName, evaluator, sdkInfo)
      ) {
        continue
      }

      if (!child.hasAttributeNS(ANDROID_URI, ATTR_ID)) {
        val prefix = context.document.lookupPrefix(ANDROID_URI) ?: "android"
        context.report(
          MISSING_ID,
          child,
          context.getNameLocation(child),
          "Views inside `MotionLayout` require an `$prefix:id` attribute",
          fix().set().todo(ANDROID_URI, ATTR_ID, "@+id/").build(),
        )
      }
    }
  }

  companion object {
    @JvmField
    val MISSING_ID =
      Issue.create(
        id = "MotionLayoutMissingId",
        briefDescription = "Views inside `MotionLayout` require an `android:id`",
        explanation = "Views inside `MotionLayout` require an `android:id`.",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(MotionLayoutIdDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
        androidSpecific = true,
      )
  }
}

/** Returns whether the [tagName] represents an actual android View object. */
private fun isView(tagName: String, evaluator: JavaEvaluator, sdkInfo: SdkInfo): Boolean {
  if (tagName == VIEW || sdkInfo.getParentViewName(tagName) != null) {
    // It's a known/pre-defined View
    return true
  }
  if (tagName.indexOf('.') <= 0) {
    // At this point any valid tag should be fully qualified
    return false
  }
  val tagClass = ViewTypeDetector.findViewForTag(tagName, evaluator) ?: return false
  return evaluator.extendsClass(tagClass, CLASS_VIEW)
}
