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
import com.android.SdkConstants.APP_PREFIX
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.TOOLS_PREFIX
import com.android.SdkConstants.TOOLS_URI
import com.android.SdkConstants.URI_PREFIX
import com.android.SdkConstants.XMLNS_ANDROID
import com.android.SdkConstants.XMLNS_PREFIX
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceUrl
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.isEditableTo
import com.android.utils.XmlUtils.getFirstSubTag
import com.android.utils.XmlUtils.getNextTag
import java.util.HashMap
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Checks for various issues related to XML namespaces. */
class NamespaceDetector : ResourceXmlDetector() {

  private var unusedNamespaces: MutableMap<String, Attr>? = null

  override fun visitDocument(context: XmlContext, document: Document) {
    var haveCustomNamespace = false
    val root = document.documentElement
    val attributes = root.attributes
    val n = attributes.length
    for (i in 0 until n) {
      val item = attributes.item(i)
      val prefix = item.nodeName
      if (prefix.startsWith(XMLNS_PREFIX)) {
        val value = item.nodeValue

        if (value != ANDROID_URI) {
          val attribute = item as Attr

          if (value.startsWith(URI_PREFIX)) {
            haveCustomNamespace = true
            val namespaces =
              unusedNamespaces
                ?: run {
                  val new = HashMap<String, Attr>()
                  unusedNamespaces = new
                  new
                }
            namespaces[prefix.substring(XMLNS_PREFIX.length)] = attribute
          } else if (value.startsWith("urn:")) {
            continue
          } else if (!value.startsWith("http://")) {
            if (
              context.isEnabled(TYPO) &&
                // In XML there can be random XML documents from users
                // with arbitrary schemas; let them use https if they want
                context.resourceFolderType != ResourceFolderType.XML
            ) {
              var fix: LintFix? = null
              if (value.startsWith("https://")) {
                fix =
                  fix()
                    .replace()
                    .text("https")
                    .with("http")
                    .name("Replace with http://${value.substring(8)}")
                    .build()
              }
              context.report(
                TYPO,
                attribute,
                context.getValueLocation(attribute),
                //noinspection LintImplUnexpectedDomain
                "Suspicious namespace: should start with `http://`",
                fix,
              )
            }
            continue
          } else if (
            value != AUTO_URI &&
              value.contains("auto") &&
              value.startsWith("http://schemas.android.com/")
          ) {
            context.report(
              RES_AUTO,
              attribute,
              context.getValueLocation(attribute),
              "Suspicious namespace: Did you mean `$AUTO_URI`?",
            )
          } else if (
            value == TOOLS_URI &&
              (prefix == XMLNS_ANDROID ||
                prefix.endsWith(APP_PREFIX) && prefix == XMLNS_PREFIX + APP_PREFIX)
          ) {
            context.report(
              TYPO,
              attribute,
              context.getValueLocation(attribute),
              "Suspicious namespace and prefix combination",
            )
          }

          if (!context.isEnabled(TYPO)) {
            continue
          }

          val name = attribute.name
          if (name != XMLNS_ANDROID && name != XMLNS_A) {
            // See if it looks like a typo
            val resIndex = value.indexOf("/res/")
            if (resIndex != -1 && value.length + 5 > URI_PREFIX.length) {
              val urlPrefix = value.substring(0, resIndex + 5)
              if (urlPrefix != URI_PREFIX && isEditableTo(URI_PREFIX, urlPrefix, 3)) {
                val correctUri = URI_PREFIX + value.substring(resIndex + 5)
                context.report(
                  TYPO,
                  attribute,
                  context.getValueLocation(attribute),
                  "Possible typo in URL: was `\"$value\"`, should " +
                    "probably be `\"$correctUri\"`",
                )
              }
            }
            continue
          }

          if (name == XMLNS_A) {
            // For the "android" prefix we always assume that the namespace prefix
            // should be our expected prefix, but for the "a" prefix we make sure
            // that it's at least "close"; if you're bound it to something completely
            // different, don't complain.
            if (!isEditableTo(ANDROID_URI, value, 4)) {
              continue
            }
          }

          if (value.equals(ANDROID_URI, ignoreCase = true)) {
            context.report(
              TYPO,
              attribute,
              context.getValueLocation(attribute),
              "URI is case sensitive: was `\"$value\"`, expected `\"$ANDROID_URI\"`",
            )
          } else {
            context.report(
              TYPO,
              attribute,
              context.getValueLocation(attribute),
              "Unexpected namespace URI bound to the `\"android\"` " +
                "prefix, was `$value`, expected `$ANDROID_URI`",
            )
          }
        } else if (
          prefix != XMLNS_ANDROID &&
            (prefix.endsWith(TOOLS_PREFIX) && prefix == XMLNS_PREFIX + TOOLS_PREFIX ||
              prefix.endsWith(APP_PREFIX) && prefix == XMLNS_PREFIX + APP_PREFIX)
        ) {
          val attribute = item as Attr
          context.report(
            TYPO,
            attribute,
            context.getValueLocation(attribute),
            "Suspicious namespace and prefix combination",
          )
        }
      }
    }

    if (haveCustomNamespace) {
      val project = context.project
      val checkCustomAttrs =
        project.resourceNamespace == ResourceNamespace.RES_AUTO &&
          (context.isEnabled(CUSTOM_VIEW) && project.isLibrary ||
            context.isEnabled(RES_AUTO) && project.isGradleProject)

      if (checkCustomAttrs) {
        checkCustomNamespace(context, root)
      }

      val checkUnused = context.isEnabled(UNUSED)
      if (checkUnused) {
        checkUnused(root)
        val namespaces = unusedNamespaces
        if (namespaces != null && !namespaces.isEmpty()) {
          for ((prefix, attribute) in namespaces) {
            context.report(
              UNUSED,
              attribute,
              context.getLocation(attribute),
              "Unused namespace `$prefix`",
            )
          }
        }
      }
    }

    if (context.isEnabled(REDUNDANT)) {
      var child = getFirstSubTag(root)
      while (child != null) {
        checkRedundant(context, child)
        child = getNextTag(child)
      }
    }
  }

  private fun checkUnused(element: Element) {
    val attributes = element.attributes
    val n = attributes.length
    for (i in 0 until n) {
      val attribute = attributes.item(i) as Attr
      val prefix = attribute.prefix
      if (prefix != null) {
        unusedNamespaces?.remove(prefix)
      }
      // Check if there is a namespace prefix in XML attribute value resource reference
      val attributeValue = attribute.nodeValue
      if (attributeValue != null) {
        val urlNamespace = ResourceUrl.parse(attributeValue)?.namespace
        if (urlNamespace != null) {
          unusedNamespaces?.remove(urlNamespace)
        }
      }
    }

    var child = getFirstSubTag(element)
    while (child != null) {
      checkUnused(child)
      child = getNextTag(child)
    }
  }

  private fun checkRedundant(context: XmlContext, element: Element) {
    // This method will not be called on the document element

    val attributes = element.attributes
    val n = attributes.length
    for (i in 0 until n) {
      val attribute = attributes.item(i) as Attr
      val name = attribute.name
      if (name.startsWith(XMLNS_PREFIX)) {
        // See if this attribute is already set on the document element
        val root = element.ownerDocument.documentElement
        val redundant = root.getAttribute(name) == attribute.value
        if (redundant) {

          val fix = fix().name("Delete namespace").set().remove(name).build()
          context.report(
            REDUNDANT,
            attribute,
            context.getLocation(attribute),
            "This namespace declaration is redundant",
            fix,
          )
        }
      }
    }

    var child = getFirstSubTag(element)
    while (child != null) {
      checkRedundant(context, child)
      child = getNextTag(child)
    }
  }

  private fun checkCustomNamespace(context: XmlContext, element: Element) {
    val attributes = element.attributes
    val n = attributes.length
    for (i in 0 until n) {
      val attribute = attributes.item(i) as Attr
      if (attribute.name.startsWith(XMLNS_PREFIX)) {
        val uri = attribute.value
        if (uri != null && !uri.isEmpty() && uri.startsWith(URI_PREFIX) && uri != ANDROID_URI) {
          if (context.project.isGradleProject) {
            context.report(
              RES_AUTO,
              attribute,
              context.getValueLocation(attribute),
              "In Gradle projects, always use `$AUTO_URI` for custom " + "attributes",
            )
          } else {
            context.report(
              CUSTOM_VIEW,
              attribute,
              context.getValueLocation(attribute),
              "When using a custom namespace attribute in a library " +
                "project, use the namespace `\"$AUTO_URI\"` instead",
            )
          }
        }
      }
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        NamespaceDetector::class.java,
        Scope.MANIFEST_AND_RESOURCE_SCOPE,
        Scope.RESOURCE_FILE_SCOPE,
        Scope.MANIFEST_SCOPE,
      )

    /** Typos in the namespace. */
    @JvmField
    val TYPO =
      Issue.create(
        id = "NamespaceTypo",
        briefDescription = "Misspelled namespace declaration",
        explanation =
          """
                Accidental misspellings in namespace declarations can lead to some very obscure \
                error messages. This check looks for potential misspellings to help track these \
                down.""",
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Unused namespace declarations. */
    @JvmField
    val UNUSED =
      Issue.create(
        id = "UnusedNamespace",
        briefDescription = "Unused namespace",
        explanation =
          """
                Unused namespace declarations take up space and require processing that is \
                not necessary""",
        category = Category.PERFORMANCE,
        priority = 1,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Unused namespace declarations. */
    @JvmField
    val REDUNDANT =
      Issue.create(
        id = "RedundantNamespace",
        briefDescription = "Redundant namespace",
        explanation =
          """
                In Android XML documents, only specify the namespace on the root/document \
                element. Namespace declarations elsewhere in the document are typically \
                accidental leftovers from copy/pasting XML from other files or documentation.""",
        category = Category.PERFORMANCE,
        priority = 1,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** Using custom namespace attributes in a library project. */
    @JvmField
    val CUSTOM_VIEW =
      Issue.create(
        id = "LibraryCustomView",
        briefDescription = "Custom views in libraries should use res-auto-namespace",
        explanation =
          """
                When using a custom view with custom attributes in a library project, the \
                layout must use the special namespace $AUTO_URI instead of a URI which includes \
                the library project's own package. This will be used to automatically adjust \
                the namespace of the attributes when the library resources are merged into \
                the application project.""",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    /** Unused namespace declarations. */
    @JvmField
    val RES_AUTO =
      Issue.create(
        id = "ResAuto",
        briefDescription = "Hardcoded Package in Namespace",
        explanation =
          """
                In Gradle projects, the actual package used in the final APK can vary; for \
                example,you can add a `.debug` package suffix in one version and not the other. \
                Therefore, you should **not** hardcode the application package in the resource; \
                instead, use the special namespace `http://schemas.android.com/apk/res-auto` \
                which will cause the tools to figure out the right namespace for the resource \
                regardless of the actual package used during the build.""",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.FATAL,
        implementation = IMPLEMENTATION,
      )

    private const val XMLNS_A = "xmlns:a"
  }
}
