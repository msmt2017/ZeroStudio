/*
 * Copyright (C) 2015 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_TITLE
import com.android.SdkConstants.PREFIX_RESOURCE_REF
import com.android.SdkConstants.STRING_PREFIX
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getChildren
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Maps
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Check which makes sure that an application restrictions file is correct. The rules are specified
 * in https://developer.android.com/reference/android/content/RestrictionsManager.html.
 */
class RestrictionsDetector : ResourceXmlDetector() {

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    if (TAG_RESTRICTIONS != root.tagName) {
      return
    }

    val keys = Maps.newHashMap<String, Element>()
    validateNestedRestrictions(context, root, null, keys, 0)
  }

  /** Validates the `<restriction>` **children** of the given element. */
  private fun validateNestedRestrictions(
    context: XmlContext,
    element: Element,
    restrictionType: String?,
    keys: MutableMap<String, Element>,
    depth: Int,
  ) {
    assert(depth == 0 || restrictionType != null)

    val children = getChildren(element)

    // Only restrictions of type bundle and bundle_array can have one or multiple nested
    // restriction elements.
    if (depth == 0 || restrictionType == VALUE_BUNDLE || restrictionType == VALUE_BUNDLE_ARRAY) {
      // Bundle and bundle array should not have a default value
      val defaultValue = element.getAttributeNodeNS(ANDROID_URI, VALUE_DEFAULT_VALUE)
      if (defaultValue != null) {
        context.report(
          ISSUE,
          element,
          context.getLocation(defaultValue),
          "Restriction type `$restrictionType` should not have a default value",
        )
      }
      for (child in children) {
        if (verifyRestrictionTagName(context, child)) {
          validateRestriction(context, child, depth + 1, keys)
        }
      }

      if (depth == 0) {
        // It's okay to have <restrictions />
      } else if (restrictionType == VALUE_BUNDLE_ARRAY) {
        if (children.size != 1) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Expected exactly one child for restriction of type `bundle_array`",
          )
        }
      } else {
        assert(restrictionType == VALUE_BUNDLE)
        if (children.isEmpty()) {
          context.report(
            ISSUE,
            element,
            context.getElementLocation(element),
            "Restriction type `bundle` should have at least one nested restriction",
          )
        }
      }

      if (children.size > MAX_NUMBER_OF_NESTED_RESTRICTIONS) {
        context.report(
          ISSUE,
          element,
          context.getElementLocation(element),
          // TODO: Reference Google Play store restriction here in error message,
          // e.g. that violating this will cause APK to be rejected?
          "Invalid nested restriction: too many nested restrictions (was ${children.size}, max $MAX_NUMBER_OF_NESTED_RESTRICTIONS)",
        )
      } else if (depth > MAX_NESTING_DEPTH) {
        // Same comment as for MAX_NUMBER_OF_NESTED_RESTRICTIONS: include source?
        context.report(
          ISSUE,
          element,
          context.getElementLocation(element),
          "Invalid nested restriction: nesting depth $depth too large (max $MAX_NESTING_DEPTH",
        )
      }
    } else if (children.isNotEmpty()) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "Only restrictions of type `bundle` and `bundle_array` can have one or multiple nested restriction elements",
      )
    }
  }

  /** Validates a `<restriction>` element (and recurses to validate the children) */
  private fun validateRestriction(
    context: XmlContext,
    node: Node,
    depth: Int,
    keys: MutableMap<String, Element>,
  ) {

    if (node.nodeType != Node.ELEMENT_NODE) {
      return
    }
    val element = node as Element

    // key, title and restrictionType are mandatory.
    val restrictionType = checkRequiredAttribute(context, element, ATTR_RESTRICTION_TYPE)
    val key = checkRequiredAttribute(context, element, ATTR_KEY)
    val title = checkRequiredAttribute(context, element, ATTR_TITLE)
    if (restrictionType == null || key == null || title == null) {
      return
    }

    // You use each restriction's android:key attribute to read its value from a
    // restrictions bundle. For this reason, each restriction must have a unique key string,
    // and the string cannot be localized. It must be specified with a string literal.
    when {
      key.startsWith(STRING_PREFIX) -> {
        val attribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        val valueLocation = context.getValueLocation(attribute)
        context.report(
          ISSUE,
          element,
          valueLocation,
          "Keys cannot be localized, they should be specified with a string literal",
        )
      }
      keys.containsKey(key) -> {
        val thisAttribute = element.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        val location = context.getValueLocation(thisAttribute)
        val prev = keys[key] ?: return
        val prevAttribute = prev.getAttributeNodeNS(ANDROID_URI, ATTR_KEY)
        val previousLocation = context.getValueLocation(prevAttribute)
        previousLocation.message = "Previous use of key here"
        location.secondary = previousLocation
        context.report(ISSUE, element, location, "Duplicate key `$key`")
      }
      else -> keys[key] = element
    }

    if (restrictionType == VALUE_CHOICE || restrictionType == VALUE_MULTI_SELECT) {
      // entries and entryValues are required if restrictionType is choice or multi-select.

      checkRequiredAttribute(context, element, VALUE_ENTRIES) != null ||
        // deliberate short circuit evaluation
        checkRequiredAttribute(context, element, VALUE_ENTRY_VALUES) != null
    } else if (restrictionType == VALUE_HIDDEN) {
      // hidden type must have a defaultValue
      checkRequiredAttribute(context, element, VALUE_DEFAULT_VALUE)
    } else if (restrictionType == VALUE_INTEGER) {
      val defaultValue = element.getAttributeNodeNS(ANDROID_URI, VALUE_DEFAULT_VALUE)
      if (defaultValue != null && !defaultValue.value.startsWith(PREFIX_RESOURCE_REF)) {
        try {

          Integer.decode(defaultValue.value)
        } catch (e: NumberFormatException) {
          context.report(ISSUE, element, context.getValueLocation(defaultValue), "Invalid number")
        }
      }
    }

    validateNestedRestrictions(context, element, restrictionType, keys, depth)
  }

  /**
   * Makes sure that the given element corresponds to a restriction tag, and if not, reports it and
   * return false.
   */
  private fun verifyRestrictionTagName(context: XmlContext, element: Element): Boolean {
    val tagName = element.tagName
    if (tagName != TAG_RESTRICTION) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "Unexpected tag `<$tagName>`, expected `<$TAG_RESTRICTION>`",
      )
      return false
    }
    return true
  }

  private fun checkRequiredAttribute(
    context: XmlContext,
    element: Element,
    attribute: String,
  ): String? {
    var fullAttribute = attribute
    if (!element.hasAttributeNS(ANDROID_URI, fullAttribute)) {
      val prefix: String? = element.lookupPrefix(ANDROID_URI)
      if (prefix != null) {
        fullAttribute = "$prefix:$fullAttribute"
      }
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Missing required attribute `$fullAttribute`",
      )
      return null
    }
    return element.getAttributeNS(ANDROID_URI, fullAttribute)
  }

  companion object {

    // Copied from Google Play store's AppRestrictionBuilder
    @VisibleForTesting const val MAX_NESTING_DEPTH = 20

    // Copied from Google Play store's AppRestrictionBuilder
    @VisibleForTesting const val MAX_NUMBER_OF_NESTED_RESTRICTIONS = 1000

    /** Validation of `<restrictions>` XML elements. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ValidRestrictions",
        briefDescription = "Invalid Restrictions Descriptor",
        explanation = "Ensures that an applications restrictions XML file is properly formed",
        moreInfo =
          "https://developer.android.com/reference/android/content/RestrictionsManager.html",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        implementation = Implementation(RestrictionsDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )

    const val TAG_RESTRICTIONS = "restrictions"
    private const val TAG_RESTRICTION = "restriction"
    private const val ATTR_RESTRICTION_TYPE = "restrictionType"
    private const val ATTR_KEY = "key"
    const val ATTR_DESCRIPTION = "description"
    private const val VALUE_BUNDLE = "bundle"
    private const val VALUE_BUNDLE_ARRAY = "bundle_array"
    private const val VALUE_CHOICE = "choice"
    private const val VALUE_MULTI_SELECT = "multi-select"
    private const val VALUE_ENTRIES = "entries"
    private const val VALUE_ENTRY_VALUES = "entryValues"
    private const val VALUE_HIDDEN = "hidden"
    private const val VALUE_DEFAULT_VALUE = "defaultValue"
    private const val VALUE_INTEGER = "integer"
  }
}
