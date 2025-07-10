/*
 * Copyright (C) 2018 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_GRAPH
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.ATTR_START_DESTINATION
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TAG_INCLUDE
import com.android.SdkConstants.TAG_NAVIGATION
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.stripIdPrefix
import java.io.IOException
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/** Check to make sure the startDestination attribute on navigation elements is set and valid. */
class StartDestinationDetector : ResourceXmlDetector() {
  companion object Issues {

    @JvmField
    val ISSUE =
      Issue.create(
        id = "InvalidNavigation",
        briefDescription = "No start destination specified",
        explanation =
          """
            All `<navigation>` elements must have a start destination specified, and it must \
            be a direct child of that `<navigation>`.
            """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.WARNING,
        implementation =
          Implementation(StartDestinationDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType == ResourceFolderType.NAVIGATION

  override fun getApplicableElements() = listOf(TAG_NAVIGATION)

  override fun visitElement(context: XmlContext, element: Element) {
    val children = element.childNodes
    // If there are no children, don't show the warning yet.
    if ((0 until children.length).none { children.item(it) is Element }) return

    val destinationAttr = element.getAttributeNodeNS(AUTO_URI, ATTR_START_DESTINATION)
    val destinationAttrValue = destinationAttr?.value
    // smart cast to non-null doesn't seem to work with isNullOrBlank
    if (destinationAttrValue == null || destinationAttrValue.isBlank()) {
      context.report(
        ISSUE,
        element,
        context.getNameLocation(element),
        "No start destination specified",
      )
    } else {
      // TODO(namespaces): Support namespaces in ids
      val url = ResourceUrl.parse(destinationAttrValue)
      if (url == null || url.type != ResourceType.ID) {
        context.report(
          ISSUE,
          element,
          context.getNameLocation(element),
          "`startDestination` must be an id",
        )
        return
      }
      for (i in 0 until children.length) {
        val child = children.item(i) as? Element ?: continue
        if (child.tagName == TAG_INCLUDE) {
          val includedGraph = child.getAttributeNS(AUTO_URI, ATTR_GRAPH)
          val includedUrl = ResourceUrl.parse(includedGraph) ?: continue
          val client = context.client
          val repository =
            if (context.isGlobalAnalysis())
              client.getResources(context.mainProject, LOCAL_DEPENDENCIES)
            else client.getResources(context.project, ResourceRepositoryScope.PROJECT_ONLY)
          val items =
            repository.getResources(ResourceNamespace.TODO(), includedUrl.type, includedUrl.name)
          if (items.isEmpty() && !context.isGlobalAnalysis()) {
            // The included layout is in another module; in that case, we can't check it.
            return
          }
          for (item in items) {
            val source = item.source ?: continue
            try {
              val parser = client.createXmlPullParser(source)
              if (parser != null && checkId(parser, url.name)) {
                return
              }
            } catch (ignore: XmlPullParserException) {
              // Users might be editing these files in the IDE; don't flag
            } catch (ignore: IOException) {
              // Users might be editing these files in the IDE; don't flag
            }
          }
        } else {
          val childId = child.getAttributeNS(ANDROID_URI, ATTR_ID)
          val childUrl = ResourceUrl.parse(childId) ?: continue
          if (url.name == childUrl.name) {
            return
          }
        }
      }
      context.report(
        ISSUE,
        element,
        context.getValueLocation(destinationAttr),
        "Invalid start destination $destinationAttrValue",
      )
    }
  }

  private fun checkId(parser: XmlPullParser, target: String): Boolean {
    while (true) {
      when (parser.next()) {
        XmlPullParser.START_TAG ->
          return stripIdPrefix(parser.getAttributeValue(ANDROID_URI, ATTR_ID)) == target
        XmlPullParser.END_TAG,
        XmlPullParser.END_DOCUMENT -> return false
      }
    }
  }
}
