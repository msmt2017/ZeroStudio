/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UComment
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.evaluateString

/** Looks for problems related to bidirectional text escapes */
class BidirectionalTextDetector : ResourceXmlDetector(), SourceCodeScanner, GradleScanner {
  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UFile::class.java, ULiteralExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return object : UElementHandler() {
      override fun visitFile(node: UFile) {
        for (comment in node.allCommentsInFile) {
          checkBidi(context, comment, comment.text)
        }
      }

      override fun visitLiteralExpression(node: ULiteralExpression) {
        val value = node.value as? String ?: return
        checkBidi(context, node, node.sourcePsi?.text ?: node.evaluateString() ?: value)
      }
    }
  }

  private fun checkBidi(context: JavaContext, node: UElement, source: CharSequence) {
    if (containsUnterminatedBidiSegment(source)) {
      val type = if (node is UComment) "Comment" else "String"
      context.report(
        BIDI_SPOOFING,
        node,
        context.getLocation(node),
        "$type contains misleading Unicode bidirectional text",
      )
    }
  }

  private fun containsUnterminatedBidiSegment(s: CharSequence): Boolean {
    // https://unicode.org/reports/tr9/
    var embeddingCount = 0
    var isolateCount = 0
    for (c in s) {
      when (c) {
        LRI,
        RLI,
        FSI -> isolateCount++
        LRE,
        LRO,
        RLO,
        RLE -> embeddingCount++
        PDI -> if (isolateCount > 0) isolateCount--
        PDF -> if (embeddingCount > 0) embeddingCount--
        '\n',
        '\r',
        FF,
        VT,
        NEL,
        LS,
        PS -> {
          isolateCount = 0
          embeddingCount = 0
        }
      }
    }
    return embeddingCount + isolateCount > 0
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(BidirectionalTextDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Looks for bidirectional text spans which may be used to change the meaning of the code */
    @JvmField
    val BIDI_SPOOFING =
      create(
          id = "BidiSpoofing",
          briefDescription = "Bidirectional text spoofing",
          explanation =
            """
                Unicode bidirectional text characters can alter the order in which the compiler processes \
                tokens. However, this can also be used to hide malicious code, and can be difficult to spot. \
                This lint check audits the source code and looks for cases where it looks like bidirectional \
                text has the potential to be misleading.
                """,
          category = Category.SECURITY,
          priority = 2,
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
          moreInfo =
            //noinspection LintImplUnexpectedDomain
            "https://krebsonsecurity.com/2021/11/trojan-source-bug-threatens-the-security-of-all-code/",
        )
        .addMoreInfo("https://goo.gle/BidiSpoofing")

    const val VT = '\u000B'
    const val FF = '\u000C'
    const val NEL = '\u0085'
    const val LS = '\u2028'
    const val PS = '\u2029'
    const val LRE = '\u202A'
    const val RLE = '\u202B'
    const val PDF = '\u202C'
    const val LRO = '\u202D'
    const val RLO = '\u202E'
    const val LRI = '\u2066'
    const val RLI = '\u2067'
    const val FSI = '\u2068'
    const val PDI = '\u2069'
  }
}
