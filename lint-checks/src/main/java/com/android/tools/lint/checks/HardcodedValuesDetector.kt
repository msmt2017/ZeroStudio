/*
 * Copyright (C) 2011 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_CONTENT_DESCRIPTION
import com.android.SdkConstants.ATTR_HINT
import com.android.SdkConstants.ATTR_LABEL
import com.android.SdkConstants.ATTR_PROMPT
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.ATTR_TITLE
import com.android.resources.ResourceFolderType
import com.android.tools.lint.checks.RestrictionsDetector.Companion.ATTR_DESCRIPTION
import com.android.tools.lint.checks.RestrictionsDetector.Companion.TAG_RESTRICTIONS
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr

/**
 * Check which looks at the children of ScrollViews and ensures that they fill/match the parent
 * width instead of setting wrap_content.
 *
 * TODO: Consider looking at the localization="suggested" attribute in the platform attrs.xml to
 *   catch future recommended attributes.
 */
class HardcodedValuesDetector : LayoutDetector() {

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(
      // Layouts
      ATTR_TEXT,
      ATTR_CONTENT_DESCRIPTION,
      ATTR_HINT,
      ATTR_LABEL,
      ATTR_PROMPT,
      "textOn",
      "textOff",

      // Menus
      ATTR_TITLE,

      // App restrictions
      ATTR_DESCRIPTION,
    )
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return (folderType == ResourceFolderType.LAYOUT ||
      folderType == ResourceFolderType.MENU ||
      folderType == ResourceFolderType.XML)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val value = attribute.value
    if (!value.isEmpty() && value[0] != '@' && value[0] != '?') {
      // Make sure this is really one of the android: attributes
      if (ANDROID_URI != attribute.namespaceURI) {
        return
      }

      // Filter out a few special cases:

      if (value == "Hello World!") {
        // This is the default text in new templates. Users are unlikely to
        // leave this in, so let's not add warnings in the editor as their
        // welcome to Android development greeting.
        return
      }
      if (
        value == "Large Text" ||
          value == "Medium Text" ||
          value == "Small Text" ||
          value.startsWith("New ") &&
            (value == "New Text" || value == "New " + attribute.ownerElement.tagName)
      ) {
        // The layout editor initially places the label "New Button", "New TextView",
        // etc on widgets dropped on the layout editor. Again, users are unlikely
        // to leave it that way, so let's not flag it until they change it.
        return
      }

      // In XML folders, currently only checking application restriction files
      // (since in general the res/xml folder can contain arbitrary XML content
      // interpreted by the app)
      if (context.resourceFolderType == ResourceFolderType.XML) {
        val tagName = attribute.ownerDocument.documentElement.tagName
        if (tagName != TAG_RESTRICTIONS) {
          return
        }
      }

      context.report(
        ISSUE,
        attribute,
        context.getLocation(attribute),
        String.format("Hardcoded string \"%1\$s\", should use `@string` resource", value),
      )
    }
  }

  companion object {
    // TODO: Add additional issues here, such as hardcoded colors, hardcoded sizes, etc

    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "HardcodedText",
        briefDescription = "Hardcoded text",
        explanation =
          """
                Hardcoding text attributes directly in layout files is bad for several reasons:

                * When creating configuration variations (for example for landscape or \
                portrait) you have to repeat the actual text (and keep it up to date when \
                making changes)

                * The application cannot be translated to other languages by just adding new \
                translations for existing string resources.

                There are quickfixes to automatically extract this hardcoded string into a \
                resource lookup.
                """,
        category = Category.I18N,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(HardcodedValuesDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }
}
