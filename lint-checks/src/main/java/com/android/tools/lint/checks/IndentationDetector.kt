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

import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypes
import kotlin.math.min
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UDoWhileExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UJumpExpression
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastErrorType
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isAssignment

/**
 * Looks for likely mistakes suggested by indentation. This isn't common if the codebase is using
 * formatting tools, but can be useful when you're editing in the IDE and haven't yet formatted.
 */
class IndentationDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(IndentationDetector::class.java, Scope.JAVA_FILE_SCOPE)

    val ALWAYS_RUN_OPTION =
      BooleanOption(
        "always-run",
        "Whether this check should be included while editing",
        false,
        """
                While you're editing, it's common to have a temporary situation where \
                you have suspicious indentation scenarios -- e.g. you start typing an \
                `if` statement on the line above something you want to make conditional, and \
                you haven't indented it yet. It can be distracting and misleading to \
                suddenly have both statements light up as errors. Therefore, lint will \
                avoid including this check when running on the fly in the editor, unless \
                it looks like the file has not been recently edited. With this option, you \
                can turn it on in all cases.
                """,
      )

    @JvmField
    val ISSUE =
      Issue.create(
          id = "SuspiciousIndentation",
          briefDescription = "Suspicious indentation",
          explanation =
            """
                This check looks for cases where the indentation suggests a grouping that isn't actually \
                there in the code. A common example of this would be something like
                ```kotlin
                if (column > width)
                    line++
                    column = 0
                ```
                Here, the `column = 0` line will be executed every single time, not just if the condition \
                is true.
                """,
          category = Category.CORRECTNESS,
          priority = 6,
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
        )
        .setAliases(listOf("SuspiciousIndentAfterControlStatement"))
        .setOptions(listOf(ALWAYS_RUN_OPTION))
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UBlockExpression::class.java)
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    // If single file analysis in the IDE, don't run this check. See
    // https://issuetracker.google.com/230626781
    // and the documentation for [IN_EDITOR_OPTION].
    if (
      LintClient.isStudio &&
        !ALWAYS_RUN_OPTION.getValue(context) &&
        Scope.checkSingleFile(context.driver.scope) &&
        context.client.isEdited(context.file, true)
    ) {
      // See if it looks like the file has not been modified
      return null
    }

    return IndentationVisitor(context)
  }

  private class IndentationVisitor(private val context: JavaContext) : UElementHandler() {
    private val contents = context.getContents() ?: ""

    override fun visitBlockExpression(node: UBlockExpression) {
      val expressions = node.expressions
      var prevStart = -1
      var prevIndent = -1
      for (i in expressions.indices) {
        val statement = expressions[i]
        val sourcePsi = statement.sourcePsi
        if (sourcePsi == null) {
          prevIndent = -1
          prevStart = -1
          continue
        }
        val startOffset = sourcePsi.startOffset
        val lineStart = findLineBeginBackwards(startOffset)
        if (lineStart == -1) {
          prevIndent = -1
          prevStart = -1
          continue
        }
        val indent = startOffset - lineStart
        val body =
          when (statement) {
            is UIfExpression -> {
              // TODO: Instead, visit BOTH of these
              val elseExpression = statement.elseExpression
              if (elseExpression != null && elseExpression !is UastEmptyExpression) elseExpression
              else statement.thenExpression
            }
            is ULoopExpression -> statement.body
            else -> null
          }
        if (
          body != null &&
            body !is UBlockExpression &&
            body.sourcePsi != null &&
            // Sometimes code will align if/else's like this:
            //   if something
            //   else //noinspection blah blah blah
            //   if something-else
            // and here the last if is really relative to the else, but we're okay in this scenario
            (statement !is UIfExpression || body !is UIfExpression) &&
            // If we have something like
            //   if something
            //   return true
            //   return false
            // while not great, it's obvious that the first return isn't unconditional
            (body !is UJumpExpression ||
              i < expressions.size - 1 && expressions[i + 1] !is UJumpExpression)
        ) {
          val nestedStart = body.sourcePsi!!.startOffset
          val nestedLineStart = findLineBeginBackwards(nestedStart)
          val nestedIndent = nestedStart - nestedLineStart
          if (nestedIndent == indent) {
            val secondary = getLineLocation(sourcePsi)
            val location = getLineLocation(body).withSecondary(secondary, "Previous statement here")
            context.report(
              ISSUE,
              node,
              location,
              "Suspicious indentation: This is conditionally executed; expected it to be indented",
            )
          }
        }

        if (prevIndent == -1 || indent == -1) {
          prevIndent = indent
          prevStart = lineStart
          continue
        }
        if (indent > prevIndent + 1 && prevIndent != 0) {
          // +1: allow off by one indentation: hard to spot and probably not confusing
          // !=0: some debug logging conventions place single line debug statements in column 0,
          // so it's normal for non-debugging code to follow and to be indented relative to it
          val prevExpression = expressions[i - 1]
          if (
            !hasBenignPredecessor(expressions, i) && !conditionCommentedOut(lineStart, prevIndent)
          ) {
            val secondary = getLineLocation(prevExpression)
            val location =
              getLineLocation(statement).withSecondary(secondary, "Previous statement here")
            val prevSummary = describeElement(prevExpression)
            val controlExpression =
              expressions[i - 1].skipParenthesizedExprDown().isControlExpression()
            val nestedUnder = if (controlExpression) "nested under" else "continuing"
            val message =
              "Suspicious indentation: This is indented but is not $nestedUnder the previous expression (`$prevSummary`...)"
            context.report(ISSUE, node, location, message)
            return
          }
        } else if (indent <= prevIndent) {
          val delta = getIndentationDeltaOffset(prevStart, lineStart, indent)
          if (delta != -1) {
            val prevLineLoc =
              Location.create(context.file, contents, prevStart + delta, prevStart + indent)
            val location =
              Location.create(context.file, contents, lineStart + delta, lineStart + indent)
                .withSecondary(prevLineLoc, "Previous line indentation here")
            val prevChar = contents[prevStart + delta].describe()
            val currChar = contents[lineStart + delta].describe()
            val incident =
              Incident(
                  ISSUE,
                  node,
                  location,
                  "The indentation string here is different from on the previous line (`$prevChar` vs `$currChar`)",
                )
                .overrideSeverity(Severity.WARNING)
            context.report(incident)
            return
          }
        }
        prevIndent = indent
        prevStart = lineStart
      }
    }

    private fun conditionCommentedOut(lineStart: Int, prevIndent: Int): Boolean {
      if (lineStart < 2) {
        return false
      }
      val prevLineStart = contents.lastIndexOf('\n', lineStart - 2) + 1
      if (prevLineStart != -1) {
        val prevContentStart = findLineBegin(prevLineStart)
        val prevLineIndent = prevContentStart - prevLineStart
        if (prevLineIndent == prevIndent && contents.regionMatches(prevContentStart, "//", 0, 2)) {
          return true
        }
      }

      return false
    }

    /**
     * Pick out the source code from the beginning of an element, attempting to break at a symbol
     * instead of mid-word.
     */
    private fun describeElement(expression: UExpression): String {
      val min = 10
      val max = 20
      val text = expression.sourcePsi?.text ?: ""
      var end = min(max, text.length - 1)
      while (end < max) {
        if (end == min) {
          break
        }
        if (text[end].isJavaIdentifierPart()) {
          end++
          break
        }
        end--
      }
      return text.substring(0, end).replace('\n', ' ')
    }

    /** Is this a control expression where a non-indented next element is potentially confusing? */
    private fun UElement.isControlExpression(): Boolean =
      this is UIfExpression || this is ULoopExpression && this !is UDoWhileExpression

    private fun UElement.isClosedWithBraces(): Boolean =
      this.sourcePsi?.text?.endsWith("}") ?: false

    /**
     * Given a list of expressions, and a pointer (well, index) pointing to a specific expression in
     * that list of siblings, returns true if the previous sibling is "benign if indented
     * differently", in other words that it's unlikely that even if these siblings have different
     * indentations, users would look at the code and be confused about whether the second one
     * depends on the first one. For a simple example, if there's an explicit close brace, that
     * makes it clear.
     */
    private fun hasBenignPredecessor(expressions: List<UExpression>, index: Int): Boolean {
      assert(index > 0)
      val prev = expressions[index - 1].skipParenthesizedExprDown() ?: return true
      if (prev.isControlExpression()) {
        return prev.isClosedWithBraces()
      }
      if (prev is UDeclarationsExpression || prev.isAssignment()) {
        val curr = expressions[index]
        if (
          curr.isAssignment() ||
            curr is UPrefixExpression &&
              (curr.operator == UastPrefixOperator.INC || curr.operator == UastPrefixOperator.DEC)
        ) {
          return true
        }
        val type = curr.getExpressionType()
        if (type == null || type == PsiTypes.voidType() || type == UastErrorType) {
          return true
        }
        return prev.isClosedWithBraces()
      }
      if (prev is UBinaryExpression) {
        return false
      }
      return true
    }

    private fun Char.describe(): String {
      return when {
        this == '\t' -> "\\t"
        this == '\r' -> "\\r"
        this == ' ' -> "\" \""
        this <= 255.toChar() -> String.format("\\u%02X", this.toInt())
        else -> String.format("\\u%04X", this.toInt())
      }
    }

    /**
     * Returns the location for the given node, taking the shortest span of the whole node and its
     * first line
     */
    private fun getLineLocation(node: UElement): Location {
      return getLineLocation(node.sourcePsi!!)
    }

    private fun getLineLocation(sourcePsi: PsiElement): Location {
      val startOffset = sourcePsi.startOffset

      var lineEnd = contents.indexOf('\n', startOffset)
      if (lineEnd == -1) {
        lineEnd = contents.length
      }

      // Strip trailing whitespace
      while (lineEnd > 0 && contents[lineEnd - 1].isWhitespace()) {
        lineEnd--
      }

      val endOffset = sourcePsi.endOffset
      return Location.create(context.file, contents, startOffset, min(endOffset, lineEnd))
    }

    /**
     * From the given offset, return the offset of the first character on the same line, unless
     * there is some other text earlier on the line (in that case return -1)
     */
    private fun findLineBeginBackwards(offset: Int): Int {
      if (offset > contents.length) {
        return -1
      }
      for (i in offset - 1 downTo 0) {
        val c = contents[i]
        if (c == '\n') {
          return i + 1
        } else if (!c.isWhitespace()) {
          return -1
        }
      }
      return 0
    }

    /** Searches forwards from the beginning of a line to the first non-space character */
    private fun findLineBegin(lineStart: Int): Int {
      val length = contents.length
      for (offset in lineStart until length) {
        if (!contents[offset].isWhitespace()) {
          return offset
        }
      }
      return length
    }

    private fun getIndentationDeltaOffset(
      prevLineOffset: Int,
      currLineOffset: Int,
      indentationLength: Int,
    ): Int {
      for (i in 0 until indentationLength) {
        if (contents[prevLineOffset + i] != contents[currLineOffset + i]) {
          return i
        }
      }
      return -1
    }
  }
}
