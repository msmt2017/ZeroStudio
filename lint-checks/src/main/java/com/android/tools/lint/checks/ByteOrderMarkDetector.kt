/*
 * Copyright (C) 2014 The Android Open Source Project
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
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import java.util.EnumSet
import org.w3c.dom.Document

/** Checks that byte order marks do not appear in resource names. */
class ByteOrderMarkDetector : ResourceXmlDetector(), SourceCodeScanner, GradleScanner {
  companion object Issues {

    /** Detects BOM characters in the middle of files. */
    @JvmField
    val BOM =
      Issue.create(
        id = "ByteOrderMark",
        briefDescription = "Byte order mark inside files",
        explanation =
          """
            Lint will flag any byte-order-mark (BOM) characters it finds in the middle of a file. Since we \
            expect files to be encoded with UTF-8 (see the EnforceUTF8 issue), the BOM characters are not \
            necessary, and they are not handled correctly by all tools. For example, if you have a BOM as \
            part of a resource name in one particular translation, that name will not be considered identical \
            to the base resource's name and the translation will not be used.""",
        moreInfo = "https://en.wikipedia.org/wiki/Byte_order_mark",
        category = Category.I18N,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(
            ByteOrderMarkDetector::class.java,
            // Applies to all text files
            EnumSet.of(
              Scope.MANIFEST,
              Scope.RESOURCE_FILE,
              Scope.JAVA_FILE,
              Scope.GRADLE_FILE,
              Scope.PROPERTY_FILE,
              Scope.PROGUARD_FILE,
            ),
            Scope.RESOURCE_FILE_SCOPE,
            Scope.JAVA_FILE_SCOPE,
            Scope.MANIFEST_SCOPE,
            Scope.JAVA_FILE_SCOPE,
            Scope.GRADLE_SCOPE,
            Scope.PROPERTY_SCOPE,
            Scope.PROGUARD_SCOPE,
          ),
      )
  }

  override fun beforeCheckFile(context: Context) {
    if (context is XmlContext && context.resourceFolderType == ResourceFolderType.RAW) {
      return
    }

    val source = context.getContents() ?: return
    val max = source.length
    for (i in 1 until max) {
      val c = source[i]
      if (c == '\uFEFF') {
        val location = Location.create(context.file, source, i, i + 1)
        val message = "Found byte-order-mark in the middle of a file"

        if (context is XmlContext) {
          val leaf = context.parser.findNodeAt(context, i)
          if (leaf != null) {
            context.report(BOM, leaf, location, message)
            continue
          }
        } else if (context is JavaContext) {
          val file = context.uastFile
          val psi = file?.javaPsi
          if (psi != null) {
            var closest = psi.findElementAt(i)
            if (closest == null && file.classes.isNotEmpty()) {
              closest = file.classes[0].javaPsi
            }
            if (closest != null) {
              context.report(BOM, closest, location, message)
              continue
            }
          }
        }

        // Report without surrounding scope node; no nearby @SuppressLint annotation
        context.report(BOM, location, message)
      }
    }
  }

  // XML files: work is done in beforeCheckFile()
  override fun visitDocument(context: XmlContext, document: Document) = Unit

  // Java files: work is done in beforeCheckFile()
  override fun createUastHandler(context: JavaContext): UElementHandler? = null

  // ProGuard files: work is done in beforeCheckFile()
  override fun run(context: Context) = Unit

  // Gradle: Don't attempt to visit the file
  override val customVisitor: Boolean
    get() = true
}
