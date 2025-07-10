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

import com.android.AndroidXConstants.CONSTRAINT_LAYOUT
import com.android.AndroidXConstants.CONSTRAINT_LAYOUT_GUIDELINE
import com.android.SdkConstants.ANDROIDX_PKG_PREFIX
import com.android.SdkConstants.ANDROID_PKG_PREFIX
import com.android.SdkConstants.ANDROID_SUPPORT_PKG_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_CLASS
import com.android.SdkConstants.ATTR_CORE_APP
import com.android.SdkConstants.ATTR_FONT_FAMILY
import com.android.SdkConstants.ATTR_LAYOUT
import com.android.SdkConstants.ATTR_LAYOUT_RESOURCE_PREFIX
import com.android.SdkConstants.ATTR_PACKAGE
import com.android.SdkConstants.ATTR_SRC_COMPAT
import com.android.SdkConstants.ATTR_STYLE
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_LAYOUT
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.VIEW_FRAGMENT
import com.android.SdkConstants.VIEW_TAG
import com.android.SdkConstants.XMLNS
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.ANIM
import com.android.resources.ResourceFolderType.ANIMATOR
import com.android.resources.ResourceFolderType.COLOR
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.INTERPOLATOR
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.MENU
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Attr
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Detects layout attributes on builtin Android widgets that do not specify a prefix but probably
 * should.
 */
class MissingPrefixDetector : LayoutDetector() {
  companion object Issues {
    /** Attributes missing the android: prefix. */
    @JvmField
    val MISSING_NAMESPACE =
      Issue.create(
        id = "MissingPrefix",
        briefDescription = "Missing Android XML namespace",
        explanation =
          """
            Most Android views have attributes in the Android namespace. When referencing these attributes \
            you **must** include the namespace prefix, or your attribute will be interpreted by `aapt` as \
            just a custom attribute.

            Similarly, in manifest files, nearly all attributes should be in the `android:` namespace.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            MissingPrefixDetector::class.java,
            Scope.MANIFEST_AND_RESOURCE_SCOPE,
            Scope.MANIFEST_SCOPE,
            Scope.RESOURCE_FILE_SCOPE,
          ),
      )
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType == LAYOUT ||
      folderType == MENU ||
      folderType == DRAWABLE ||
      folderType == ANIM ||
      folderType == ANIMATOR ||
      folderType == COLOR ||
      folderType == INTERPOLATOR

  override fun getApplicableAttributes(): Collection<String> = ALL

  private fun isNoPrefixAttribute(attribute: String): Boolean =
    when (attribute) {
      ATTR_CLASS,
      ATTR_STYLE,
      ATTR_LAYOUT,
      ATTR_PACKAGE,
      ATTR_CORE_APP,
      "split" -> true
      else -> false
    }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    val uri = attribute.namespaceURI
    if (uri == null || uri.isEmpty()) {
      val name = attribute.name ?: return
      if (isNoPrefixAttribute(name)) {
        return
      }

      val element = attribute.ownerElement
      if (isCustomView(element) && context.resourceFolderType != null) {
        return
      } else if (context.resourceFolderType == LAYOUT) {
        // Data binding: These look like Android framework views but
        // are data binding directives not in the Android namespace
        val root = element.ownerDocument.documentElement
        if (TAG_LAYOUT == root.tagName) {
          return
        }
      }

      if (name.indexOf(':') != -1) {
        // Don't flag warnings for attributes that already have a different
        // namespace! This doesn't usually happen when lint is run from the
        // command line, since (with the exception of xmlns: declaration attributes)
        // an attribute shouldn't have a prefix *and* have no namespace, but
        // when lint is run in the IDE (with a more fault-tolerant XML parser)
        // this can happen, and we don't want to flag erroneous/misleading lint
        // errors in this case.
        return
      }

      val elementNamespace = element.namespaceURI
      if (elementNamespace != null && elementNamespace.isNotEmpty()) {
        // For example, <aapt:attr name="android:drawable">
        return
      }

      context.report(
        MISSING_NAMESPACE,
        attribute,
        context.getLocation(attribute),
        "Attribute is missing the Android namespace prefix",
      )
    } else if (
      ANDROID_URI != uri &&
        TOOLS_URI != uri &&
        context.resourceFolderType == LAYOUT &&
        !isCustomView(attribute.ownerElement) &&
        !isFragment(attribute.ownerElement) &&
        !attribute.localName.startsWith(ATTR_LAYOUT_RESOURCE_PREFIX) &&
        attribute.ownerElement.parentNode.nodeType == Node.ELEMENT_NODE
    ) {
      // A namespace declaration?
      val prefix = attribute.prefix
      if (XMLNS == prefix) {
        val name = attribute.nodeName
        // See if it's already reported on the root
        val root = attribute.ownerDocument.documentElement
        val attributes = root.attributes
        var i = 0
        val n = attributes.length
        while (i < n) {
          val item = attributes.item(i)
          if (name == item.nodeName && attribute.value == item.nodeValue) {
            context.report(
              NamespaceDetector.UNUSED,
              attribute,
              context.getLocation(attribute),
              String.format(
                "Unused namespace declaration %1\$s; already " + "declared on the root element",
                name,
              ),
            )
          }
          i++
        }

        return
      }

      if (context.resourceFolderType == LAYOUT && AUTO_URI == uri) {
        // Data binding: Can add attributes like onClickListener to buttons etc.
        val root = attribute.ownerDocument.documentElement
        if (TAG_LAYOUT == root.tagName) {
          return
        }

        // Appcompat now encourages decorating standard views (like ImageView and
        // ImageButton) with srcCompat in the app namespace
        if (
          attribute.localName == ATTR_SRC_COMPAT ||
            // Now handled by appcompat
            attribute.localName == ATTR_FONT_FAMILY
        ) {
          return
        }
      }
    }
  }

  private fun isFragment(element: Element): Boolean = VIEW_FRAGMENT == element.tagName

  private fun isCustomView(element: Element): Boolean {
    // If this is a custom view, the usage of custom attributes can be legitimate
    val tag = element.tagName
    if (tag == VIEW_TAG) {
      // <view class="my.custom.view" ...>
      return true
    }

    // For the purposes of this check, the ConstraintLayout isn't a custom view

    if (CONSTRAINT_LAYOUT.isEquals(tag) || CONSTRAINT_LAYOUT_GUIDELINE.isEquals(tag)) {
      return false
    }

    return tag.indexOf('.') != -1 &&
      (!tag.startsWith(ANDROID_PKG_PREFIX) ||
        tag.startsWith(ANDROID_SUPPORT_PKG_PREFIX) ||
        tag.startsWith(ANDROIDX_PKG_PREFIX))
  }
}
