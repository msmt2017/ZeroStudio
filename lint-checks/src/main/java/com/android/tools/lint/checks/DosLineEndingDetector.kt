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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LayoutDetector
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import org.w3c.dom.Document

/** Checks that the line endings in DOS files are consistent. */
class DosLineEndingDetector : LayoutDetector() {
  companion object Issues {
    /** Detects mangled DOS line ending documents. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MangledCRLF",
        briefDescription = "Mangled file line endings",
        explanation =
          """
            On Windows, line endings are typically recorded as carriage return plus newline: \\r\\n.

            This detector looks for invalid line endings with repeated carriage return characters \
            (without newlines). Previous versions of the ADT plugin could accidentally introduce these \
            into the file, and when editing the file, the editor could produce confusing visual artifacts.
            """,
        moreInfo = "https://bugs.eclipse.org/bugs/show_bug.cgi?id=375421",
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.ERROR,
        // This check is probably not relevant for most users anymore
        enabledByDefault = false,
        implementation =
          Implementation(DosLineEndingDetector::class.java, Scope.RESOURCE_FILE_SCOPE),
      )
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    val contents = context.getContents() ?: return

    // We could look for *consistency* and complain if you mix \n and \r\n too,
    // but that isn't really a problem (most editors handle it) so let's
    // not complain needlessly.

    var prev: Char = 0.toChar()
    var i = 0
    val n = contents.length
    while (i < n) {
      val c = contents[i]
      if (c == '\r' && prev == '\r') {
        val message =
          "Incorrect line ending: found carriage return (`\\r`) without corresponding newline (`\\n`)"

        // Mark the whole line as the error range, since pointing just to the
        // line ending makes the error invisible in IDEs and error reports etc
        // Find the most recent non-blank line
        var blankLine = true
        for (index in i - 2 until i) {
          val d = contents[index]
          if (!Character.isWhitespace(d)) {
            blankLine = false
          }
        }

        var lineBegin = i
        for (index in i - 2 downTo 0) {
          val d = contents[index]
          if (d == '\n') {
            lineBegin = index + 1
            if (!blankLine) {
              break
            }
          } else if (!Character.isWhitespace(d)) {
            blankLine = false
          }
        }

        val lineEnd = Math.min(contents.length, i + 1)
        val location = Location.create(context.file, contents, lineBegin, lineEnd)
        context.report(ISSUE, document.documentElement, location, message)
        return
      }
      prev = c
      i++
    }
  }
}
