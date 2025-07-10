/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.VALUE_TRUE
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope.ALL_DEPENDENCIES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

/**
 * Checks for the combination of textAllCaps=true and using markup in the string being formatted.
 */
/** Constructs an [AllCapsDetector] */
class AllCapsDetector : LayoutDetector() {
  companion object Issues {
    /** Using all caps with markup. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AllCaps",
        briefDescription = "Combining textAllCaps and markup",
        explanation =
          """
            The textAllCaps text transform will end up calling `toString` on the `CharSequence`, which has \
            the net effect of removing any markup such as `<b>`. This check looks for usages of strings \
            containing markup that also specify `textAllCaps=true`.""",
        category = Category.TYPOGRAPHY,
        priority = 8,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            AllCapsDetector::class.java,
            Scope.ALL_RESOURCES_SCOPE,
            Scope.RESOURCE_FILE_SCOPE,
          ),
      )
  }

  override fun getApplicableAttributes(): Collection<String> = listOf("textAllCaps")

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (ANDROID_URI != attribute.namespaceURI) {
      return
    }

    if (VALUE_TRUE != attribute.value) {
      return
    }

    val text = attribute.ownerElement.getAttributeNS(ANDROID_URI, ATTR_TEXT)
    if (text.isEmpty()) {
      return
    }

    val url = ResourceUrl.parse(text)
    if (url == null || url.isFramework) {
      return
    }

    val client = context.client
    val full = context.isGlobalAnalysis()
    val project = if (full) context.mainProject else context.project
    val repository = client.getResources(project, ALL_DEPENDENCIES)
    val items = repository.getResources(ResourceNamespace.TODO(), url.type, url.name)
    if (items.isEmpty()) {
      return
    }
    val resourceValue = items[0].resourceValue ?: return
    val rawXmlValue = resourceValue.rawXmlValue ?: return
    if (rawXmlValue.contains("<")) {
      val message =
        "Using `textAllCaps` with a string (`${url.name}`) that " +
          "contains markup; the markup will be dropped by the caps " +
          "conversion"
      context.report(ISSUE, attribute, context.getLocation(attribute), message)
    }
  }
}
