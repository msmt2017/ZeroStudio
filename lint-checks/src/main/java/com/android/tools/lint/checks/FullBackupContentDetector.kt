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

import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.utils.iterator
import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node

/** Check which makes sure that a full-backup-content descriptor file is valid and logical. */
class FullBackupContentDetector : ResourceXmlDetector() {
  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val root = document.documentElement ?: return
    val rootTag = root.tagName
    if (rootTag == TAG_FULL_BACKUP_CONTENT) {
      checkSection(context, root)
    } else if (rootTag == TAG_DATA_EXTRACTION_RULES) {
      for (child in root) {
        // <cloud-backup> and <device-transfer> sections
        checkSection(context, child)
      }
    }
  }

  private fun checkSection(context: XmlContext, root: Element) {
    val includes: MutableList<Element> = mutableListOf()
    val excludes: MutableList<Element> = mutableListOf()
    val children = root.childNodes
    var i = 0
    val n = children.length
    while (i < n) {
      val child = children.item(i)
      if (child.nodeType == Node.ELEMENT_NODE) {
        val element = child as Element
        val tag = element.tagName
        if (TAG_INCLUDE == tag) {
          includes.add(element)
        } else if (TAG_EXCLUDE == tag) {
          excludes.add(element)
        } else {
          // See FullBackup#validateInnerTagContents
          context.report(
            ISSUE,
            element,
            context.getNameLocation(element),
            "Unexpected element `<$tag>`",
          )
        }
      }
      i++
    }
    val includePaths: Multimap<String, String> = ArrayListMultimap.create(includes.size, 4)
    for (include in includes) {
      val domain = validateDomain(context, include)
      val path = validatePath(context, include)
      if (domain == null) {
        continue
      }
      includePaths.put(domain, path)
    }
    for (exclude in excludes) {
      val excludePath = validatePath(context, exclude)
      if (excludePath.isEmpty()) {
        continue
      }
      val domain = validateDomain(context, exclude) ?: continue
      if (includePaths.isEmpty) {
        // There is no <include> anywhere: that means that everything
        // is considered included and there's no potential prefix mismatch
        continue
      }
      var hasPrefix = false
      val included = includePaths[domain] ?: continue
      for (includePath in included) {
        if (excludePath.startsWith(includePath) || includePath == ".") {
          if (excludePath == includePath) {
            val pathNode = exclude.getAttributeNode(ATTR_PATH) ?: continue
            val location = context.getValueLocation(pathNode)
            // Find corresponding include path so we can link to it in the
            // chained location list
            for (include in includes) {
              val includePathNode = include.getAttributeNode(ATTR_PATH)
              val includeDomain = include.getAttribute(ATTR_DOMAIN)
              if (
                includePathNode != null &&
                  excludePath == includePathNode.value &&
                  domain == includeDomain
              ) {
                val earlier = context.getLocation(includePathNode)
                earlier.message = "Unnecessary/conflicting <include>"
                location.secondary = earlier
              }
            }
            context.report(ISSUE, exclude, location, "Include `$excludePath` is also excluded")
          }
          hasPrefix = true
          break
        }
      }
      if (!hasPrefix) {
        val pathNode = exclude.getAttributeNode(ATTR_PATH)
        context.report(
          ISSUE,
          exclude,
          context.getValueLocation(pathNode),
          "`$excludePath` is not in an included path",
        )
      }
    }
  }

  private fun validatePath(context: XmlContext, element: Element): String {
    val pathNode = element.getAttributeNode(ATTR_PATH) ?: return ""
    val value = pathNode.value
    if (value.contains("//")) {
      context.report(
        ISSUE,
        element,
        context.getValueLocation(pathNode),
        "Paths are not allowed to contain `//`",
      )
    } else if (value.contains("..")) {
      context.report(
        ISSUE,
        element,
        context.getValueLocation(pathNode),
        "Paths are not allowed to contain `..`",
      )
    } else if (value.contains("/")) {
      val domain = element.getAttribute(ATTR_DOMAIN)
      if (DOMAIN_SHARED_PREF == domain || DOMAIN_DATABASE == domain) {
        context.report(
          ISSUE,
          element,
          context.getValueLocation(pathNode),
          "Subdirectories are not allowed for domain `$domain`",
        )
      }
    }
    return value
  }

  private fun validateDomain(context: XmlContext, element: Element): String? {
    val domainNode = element.getAttributeNode(ATTR_DOMAIN)
    if (domainNode == null) {
      context.report(
        ISSUE,
        element,
        context.getElementLocation(element),
        "Missing domain attribute, expected one of ${VALID_DOMAINS.joinToString(", ")}",
      )
      return null
    }
    val domain = domainNode.value
    for (availableDomain in VALID_DOMAINS) {
      if (availableDomain == domain) {
        return domain
      }
    }
    context.report(
      ISSUE,
      element,
      context.getValueLocation(domainNode),
      "Unexpected domain `$domain`, expected one of ${VALID_DOMAINS.joinToString(", ")}",
    )
    return domain
  }

  companion object {
    /** Validation of `<data-extraction-rules` and `<full-backup-content>` XML elements. */
    @JvmField
    val ISSUE =
      create(
        id = "FullBackupContent",
        briefDescription = "Valid Full Backup Content File",
        explanation =
          """
                Ensures that `<data-extraction-rules`> and `<full-backup-content>` files, which configure \
                backup options, are valid.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.FATAL,
        moreInfo =
          "https://android-developers.googleblog.com/2015/07/auto-backup-for-apps-made-simple.html",
        implementation =
          Implementation(FullBackupContentDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )

    private const val DOMAIN_SHARED_PREF = "sharedpref"
    private const val DOMAIN_ROOT = "root"
    private const val DOMAIN_FILE = "file"
    private const val DOMAIN_DATABASE = "database"
    private const val DOMAIN_EXTERNAL = "external"
    private const val DOMAIN_DEVICE_FILE = "device_file"
    private const val DOMAIN_DEVICE_DATABASE = "device_database"
    private const val DOMAIN_DEVICE_SHAREDPREF = "device_sharedpref"
    private const val DOMAIN_DEVICE_ROOT = "device_root"
    private const val TAG_EXCLUDE = "exclude"
    private const val TAG_INCLUDE = "include"
    private const val TAG_FULL_BACKUP_CONTENT = "full-backup-content"
    private const val TAG_DATA_EXTRACTION_RULES = "data-extraction-rules"
    private const val ATTR_PATH = "path"
    private const val ATTR_DOMAIN = "domain"

    /** Valid domains; see FullBackup#getTokenForXmlDomain for authoritative list. */
    private val VALID_DOMAINS =
      arrayOf(
        DOMAIN_FILE,
        DOMAIN_DATABASE,
        DOMAIN_SHARED_PREF,
        DOMAIN_EXTERNAL,
        DOMAIN_ROOT,
        DOMAIN_DEVICE_FILE,
        DOMAIN_DEVICE_DATABASE,
        DOMAIN_DEVICE_SHAREDPREF,
        DOMAIN_DEVICE_ROOT,
      )
  }
}
