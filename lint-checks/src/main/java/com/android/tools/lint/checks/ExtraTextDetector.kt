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

import com.android.SdkConstants.AAPT_URI
import com.android.SdkConstants.TAG_ITEM
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.RAW
import com.android.resources.ResourceFolderType.VALUES
import com.android.resources.ResourceFolderType.XML
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document
import org.w3c.dom.Node

/**
 * Check which looks for invalid resources. Aapt already performs some validation, such as making
 * sure that resource references point to resources that exist, but this detector looks for
 * additional issues.
 */
class ExtraTextDetector : ResourceXmlDetector() {
  companion object Issues {

    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ExtraText",
        briefDescription = "Extraneous text in resource files",
        explanation =
          """
            Non-value resource files should only contain elements and attributes. Any XML text content found \
            in the file is likely accidental (and potentially dangerous if the text resembles XML and the \
            developer believes the text to be functional).
            """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            ExtraTextDetector::class.java,
            Scope.MANIFEST_AND_RESOURCE_SCOPE,
            Scope.RESOURCE_FILE_SCOPE,
            Scope.MANIFEST_SCOPE,
          ),
      )
  }

  override fun appliesTo(folderType: ResourceFolderType): Boolean =
    folderType != VALUES && folderType != XML && folderType != RAW

  override fun visitDocument(context: XmlContext, document: Document) {
    warnings = 0
    errors = 0
    visitNode(context, document)
  }

  private var warnings = 0
  private var errors = 0

  private fun visitNode(context: XmlContext, node: Node) {
    if (errors > 0) {
      // We only report at most one error per file
      return
    }

    val nodeType = node.nodeType
    if (nodeType == Node.TEXT_NODE) {
      val text = node.nodeValue
      var i = 0
      val n = text.length
      while (i < n) {
        val c = text[i]
        if (!Character.isWhitespace(c)) {
          var snippet = text.trim().replace('\n', ' ').replace(Regex("\\s+"), " ")
          val maxLength = 100
          if (snippet.length > maxLength) {
            snippet = snippet.substring(0, maxLength) + "..."
          }
          val location = context.getLocation(node)
          val warnOnly = text.none { it.isJavaIdentifierPart() }
          if (!warnOnly || warnings == 0) {
            val type = context.resourceFolderType?.getName() ?: "manifest"
            val incident =
              Incident(ISSUE, node, location, "Unexpected text found in $type file: \"$snippet\"")

            // If the string only contains punctuation, only flag as a warning
            if (warnOnly) {
              incident.overrideSeverity(Severity.WARNING)
              warnings++
            } else {
              errors++
            }

            context.report(incident)
          }
          break
        }
        i++
      }
    } else if (nodeType == Node.ELEMENT_NODE) {
      if (node.prefix != null && node.namespaceURI == AAPT_URI) {
        // <aapt:x> directives in the resource file can introduce value definitions where text
        // content
        // is both allowed and expected.
        return
      }
      if (node.nodeName == TAG_ITEM) {
        // <item> seems to be allowed for some drawables, animators etc (see issue b/237555614)
        return
      }
    }

    // Visit children
    val childNodes = node.childNodes
    var i = 0
    val n = childNodes.length
    while (i < n) {
      val child = childNodes.item(i)
      visitNode(context, child)
      i++
    }
  }
}
