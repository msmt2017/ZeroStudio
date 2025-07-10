/*
 * Copyright (C) 2012 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_LABEL_FOR
import com.android.SdkConstants.ATTR_TEXT
import com.android.SdkConstants.AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.EDIT_TEXT
import com.android.SdkConstants.ID_PREFIX
import com.android.SdkConstants.MULTI_AUTO_COMPLETE_TEXT_VIEW
import com.android.SdkConstants.NEW_ID_PREFIX
import com.android.SdkConstants.TEXT_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.stripIdPrefix
import org.w3c.dom.Attr
import org.w3c.dom.Element

/** Detector which finds unlabeled text fields */
class LabelForDetector : LayoutDetector() {
  private var labels: MutableSet<String>? = null
  private var editableTextFields: MutableList<Element>? = null

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(ATTR_LABEL_FOR)
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(EDIT_TEXT, AUTO_COMPLETE_TEXT_VIEW, MULTI_AUTO_COMPLETE_TEXT_VIEW)
  }

  override fun afterCheckFile(context: Context) {
    val labels = labels ?: emptySet()
    val editableTextFields = editableTextFields ?: emptyList()
    this.labels = null
    this.editableTextFields = null

    for (element in editableTextFields) {
      val id = element.getAttributeNS(ANDROID_URI, ATTR_ID)
      val hintProvided = element.hasAttributeNS(ANDROID_URI, ATTR_HINT)
      var labelForProvided = false
      when {
        labels.contains(id) -> labelForProvided = true
        id.startsWith(NEW_ID_PREFIX) ->
          labelForProvided = labels.contains(ID_PREFIX + stripIdPrefix(id))
        id.startsWith(ID_PREFIX) ->
          labelForProvided = labels.contains(NEW_ID_PREFIX + stripIdPrefix(id))
      }

      // Note: if only android:hint is provided, no need for a warning.
      if (
        (!hintProvided || !labelForProvided) &&
          (hintProvided || labelForProvided) &&
          (!labelForProvided || context.project.minSdk >= 17)
      ) {
        return
      }
      val location = context.getLocation(element)
      val incident = Incident(ISSUE, element, location, "")
      context.report(incident, map().put(KEY_HINT, hintProvided).put(KEY_LABEL, labelForProvided))
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val hintProvided = map.getBoolean(KEY_HINT) ?: false
    val labelForProvided = map.getBoolean(KEY_LABEL) ?: false
    var message = ""
    val minSdk = context.mainProject.minSdk
    if (hintProvided && labelForProvided) {
      // Note: labelFor no-ops below 17.
      if (minSdk >= 17) {
        message = "$PROVIDE_LABEL_FOR_OR_HINT, but not both"
      }
    } else if (!hintProvided && !labelForProvided) {
      message =
        if (minSdk < 17) {
          PROVIDE_HINT
        } else {
          PROVIDE_LABEL_FOR_OR_HINT
        }
    } else {
      // Note: if only android:hint is provided, no need for a warning.
      if (labelForProvided) {
        if (minSdk < 17) {
          message = PROVIDE_HINT
        }
      }
    }
    if (message.isEmpty()) {
      return false
    }

    incident.message = messageWithPrefix(message)
    return true
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    assert(attribute.localName == ATTR_LABEL_FOR)
    val element = attribute.ownerElement

    val labels = labels ?: mutableSetOf<String>().also { labels = it }
    labels.add(attribute.value)

    // Unlikely this is anything other than a TextView. If it is, bail.
    if (element.localName != TEXT_VIEW) {
      return
    }
    val textAttributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_TEXT)
    val contentDescriptionNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_CONTENT_DESCRIPTION)
    if (
      (textAttributeNode == null || textAttributeNode.value.isEmpty()) &&
        (contentDescriptionNode == null || contentDescriptionNode.value.isEmpty())
    ) {
      val fix =
        fix()
          .alternatives(
            fix().set().todo(ANDROID_URI, ATTR_TEXT).build(),
            fix().set().todo(ANDROID_URI, ATTR_CONTENT_DESCRIPTION).build(),
          )
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element, null, ANDROID_URI, ATTR_LABEL_FOR),
        messageWithPrefix(
          "when using `android:labelFor`, you must also define an " +
            "`android:text` or an `android:contentDescription`"
        ),
        fix,
      )
    }
  }

  override fun visitElement(context: XmlContext, element: Element) {
    if (element.hasAttributeNS(ANDROID_URI, ATTR_HINT)) {
      val hintAttributeNode = element.getAttributeNodeNS(ANDROID_URI, ATTR_HINT)
      if (hintAttributeNode.value.isEmpty()) {
        context.report(
          ISSUE,
          hintAttributeNode,
          context.getLocation(hintAttributeNode),
          "Empty `android:hint` attribute",
        )
      }
    }
    val fields = editableTextFields ?: mutableListOf<Element>().also { editableTextFields = it }
    fields.add(element)
  }

  companion object {
    /** The main issue discovered by this detector */
    @JvmField
    val ISSUE =
      create(
        id = "LabelFor",
        briefDescription = "Missing accessibility label",
        explanation =
          """
                 Editable text fields should provide an `android:hint` or, provided your `minSdkVersion` \
                 is at least 17, they may be referenced by a view with a `android:labelFor` attribute.

                 When using `android:labelFor`, be sure to provide an `android:text` or an \
                 `android:contentDescription`.

                 If your view is labeled but by a label in a different layout which includes this one, \
                 just suppress this warning from lint.
                """,
        category = Category.A11Y,
        priority = 2,
        severity = Severity.WARNING,
        implementation = Implementation(LabelForDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )

    private const val PREFIX = "Missing accessibility label"
    private const val PROVIDE_HINT = "where minSdk < 17, you should provide an `android:hint`"
    private const val PROVIDE_LABEL_FOR_OR_HINT =
      "provide either a view with an " +
        "`android:labelFor` that references this view or provide an `android:hint`"
    private const val KEY_HINT = "hint"
    private const val KEY_LABEL = "label"

    private fun messageWithPrefix(message: String): String {
      return "$PREFIX: $message"
    }
  }
}
