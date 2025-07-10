/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.lint.checks.TraceSectionDetector.Companion.SearchResult.FOUND_EXCEPTION
import com.android.tools.lint.checks.TraceSectionDetector.Companion.SearchResult.FOUND_NOTHING
import com.android.tools.lint.checks.TraceSectionDetector.Companion.SearchResult.FOUND_RETURN
import com.android.tools.lint.checks.TraceSectionDetector.Companion.SearchResult.FOUND_SUSPEND
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getParentOfType

/**
 * Checks for calls to begin and end trace sections, ensuring they are properly nested and paired.
 * This check works for public tracing APIs (`android.os.Trace.beginSection()`), AndroidX APIs
 * (`androidx.tracing.Trace.beginSection()`), and hidden platform tracing APIs
 * (`android.os.Trace.traceBegin()`).
 */
class TraceSectionDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf(BEGIN_SECTION, TRACE_BEGIN)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val result = searchForMatchingTraceSection(context, node)
    val message =
      when (result) {
        FOUND_NOTHING -> return
        FOUND_SUSPEND ->
          "The `${node.methodName}()` call is not always closed with a matching " +
            "`${endName(node.methodName)}()` because the code in between may suspend"
        FOUND_RETURN ->
          "The `${node.methodName}()` call is not always closed with a matching " +
            "`${endName(node.methodName)}()` because the code in between may return early"
        FOUND_EXCEPTION ->
          "The `${node.methodName}()` call is not always closed with a matching " +
            "`${endName(node.methodName)}()` because the code in between may throw an exception"
      }
    context.report(
      UNCLOSED_TRACE,
      node,
      context.getCallLocation(node, includeReceiver = false, includeArguments = false),
      message,
    )
  }

  /** Return the name of the matching end-section call. */
  private fun endName(name: String?): String {
    return if (name == TRACE_BEGIN) TRACE_END else END_SECTION
  }

  companion object {
    @JvmField
    val STRICT_MODE =
      BooleanOption(
        "strict",
        "Whether to assume any method call could throw an exception",
        false,
        """
          In strict mode, this check assumes that any method call in between begin-section and \
          end-section pairs could potentially throw an exception. Strict mode is useful for \
          situations where unchecked Java exceptions are caught and do not necessarily result in \
          a crash.

          If strict mode is off, this check will still consider the flow of exceptions in Kotlin, \
          but it will ignore unchecked exceptions (`RuntimeException` and its subclasses) in Java \
          unless the method declares explicitly that it `throws` them. If strict mode is enabled, \
          all Java method calls need to be guarded using a finally block so ensure the trace is \
          always ended.
          """,
      )

    @JvmField
    val UNCLOSED_TRACE =
      Issue.create(
          id = "UnclosedTrace",
          briefDescription = "Incorrect trace section usage",
          explanation =
            """
            Calls to begin trace sections must be followed by corresponding calls to end those \
            trace sections. Care must be taken to ensure that begin-section / end-section pairs \
            are properly nested, and that functions do not return when there are still unclosed \
            trace sections. The easiest way to ensure begin-section / end-section pairs are \
            properly closed is to use a try-finally block as follows:

            ```kotlin
            try {
              Trace.beginSection("OK")
              return true
            } finally {
              Trace.endSection()
            }
            ```

            This lint check may result in false-positives if trace sections are guarded by \
            conditionals. For example, it may erroneously say the following has unclosed trace \
            sections:

            ```kotlin
            try {
              Trace.beginSection("Wrong")
              if (a == b) {
                Trace.beginSection("OK")
                blockingCall()
              }
            } finally {
              // Even though this is technically correct, the lint check isn't capable of detecting
              // that the two conditionals are the same
              if (a == b) Trace.endSection()
              Trace.endSection()
            }
            ```

            To fix the code snippet above, you could add a nested try-finally as follows:

            ```kotlin
            try {
              Trace.beginSection("OK")
              if (a == b) {
                try {
                  Trace.beginSection("OK")
                  blockingCall()
                } finally {
                  Trace.endSection()
                }
              }
            } finally {
              Trace.endSection()
            }
            ```
            """,
          category = Category.CORRECTNESS,
          priority = 2,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = Implementation(TraceSectionDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
        .setOptions(listOf(STRICT_MODE))

    private const val PLATFORM_TRACE_FQN = "android.os.Trace"
    private const val ANDROIDX_TRACE_FQN = "androidx.tracing.Trace"

    private const val TRACE_IS_ENABLED = "isEnabled"
    private const val TRACE_IS_TAG_ENABLED = "isTagEnabled"

    // Public platform and AndroidX APIs (called on PLATFORM_TRACE_FQN or ANDROIDX_TRACE_FQN):
    private const val BEGIN_SECTION = "beginSection"
    private const val END_SECTION = "endSection"

    // Hidden platform APIs (called on PLATFORM_TRACE_FQN):
    private const val TRACE_BEGIN = "traceBegin"
    private const val TRACE_END = "traceEnd"

    private class AstSearchContext(
      val context: JavaContext,
      val isBeginCall: (call: UCallExpression) -> Boolean,
      val isEndCall: (call: UCallExpression) -> Boolean,
    )

    enum class SearchResult {
      FOUND_NOTHING,
      FOUND_RETURN,
      FOUND_EXCEPTION,
      FOUND_SUSPEND,
    }

    private fun searchForMatchingTraceSection(
      context: JavaContext,
      node: UCallExpression,
    ): SearchResult {
      val searchContext =
        if (isPlatformPublicBeginCall(node)) {
          AstSearchContext(context, ::isPlatformPublicBeginCall, ::isPlatformPublicEndCall)
        } else if (isPlatformSystemBeginCall(node)) {
          AstSearchContext(context, ::isPlatformSystemBeginCall, ::isPlatformSystemEndCall)
        } else if (isAndroidXBeginCall(node)) {
          AstSearchContext(context, ::isAndroidXBeginCall, ::isAndroidXEndCall)
        } else {
          return FOUND_NOTHING
        }

      val containingMethod = node.getParentOfType<UMethod>() ?: return FOUND_NOTHING
      val strictMode = STRICT_MODE.getValue(context)
      val graph =
        ControlFlowGraph.create(
          containingMethod,
          builder =
            object :
              ControlFlowGraph.Companion.Builder(
                strictMode,
                trackCallThrows = true,
                trackUncheckedExceptions = false,
              ) {
              override fun canThrow(reference: UElement, method: PsiMethod): Boolean {
                val name = method.name
                // Ignore exceptions for begin and end calls. Technically,
                // android.os.Trace.beginSection() will throw an IllegalArgumentException if passed
                // a string longer than 127 characters, but that's a separate issue.
                if (
                  name == TRACE_IS_ENABLED ||
                    name == TRACE_IS_TAG_ENABLED ||
                    name == BEGIN_SECTION ||
                    name == END_SECTION ||
                    name == TRACE_BEGIN ||
                    name == TRACE_END
                ) {
                  val containing = method.containingClass?.qualifiedName
                  if (containing == PLATFORM_TRACE_FQN || containing == ANDROIDX_TRACE_FQN) {
                    return false
                  }
                }

                return super.canThrow(reference, method)
              }

              override fun checkBranchPaths(
                conditional: UExpression
              ): ControlFlowGraph.FollowBranch {
                val selector = conditional.findSelector()
                if (selector is UCallExpression) {
                  val resolved = selector.resolve()
                  val methodName = resolved?.name
                  val classFqn = resolved?.containingClass?.qualifiedName
                  val isAndroidXCall = classFqn == ANDROIDX_TRACE_FQN
                  val isPlatformCall = classFqn == PLATFORM_TRACE_FQN
                  if (
                    ((isAndroidXCall || isPlatformCall) && methodName == TRACE_IS_ENABLED) ||
                      (isPlatformCall && methodName == TRACE_IS_TAG_ENABLED)
                  ) {
                    // For the purpose of this lint check, assume Trace.isEnabled() and
                    // Trace.isTagEnabled() are always true
                    return ControlFlowGraph.FollowBranch.THEN
                  }
                }
                return super.checkBranchPaths(conditional)
              }
            },
        )

      val graphNode = graph.getNode(node) ?: return FOUND_NOTHING
      return dfs(searchContext, graphNode, 0, false)
    }

    private fun isPlatformPublicBeginCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == BEGIN_SECTION &&
        call.resolve()?.containingClass?.qualifiedName == PLATFORM_TRACE_FQN
    }

    private fun isPlatformPublicEndCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == END_SECTION &&
        call.resolve()?.containingClass?.qualifiedName == PLATFORM_TRACE_FQN
    }

    private fun isPlatformSystemBeginCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == TRACE_BEGIN &&
        call.resolve()?.containingClass?.qualifiedName == PLATFORM_TRACE_FQN
    }

    private fun isPlatformSystemEndCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == TRACE_END &&
        call.resolve()?.containingClass?.qualifiedName == PLATFORM_TRACE_FQN
    }

    private fun isAndroidXBeginCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == BEGIN_SECTION &&
        call.resolve()?.containingClass?.qualifiedName == ANDROIDX_TRACE_FQN
    }

    private fun isAndroidXEndCall(call: UCallExpression): Boolean {
      val name = call.methodName
      return name == END_SECTION &&
        call.resolve()?.containingClass?.qualifiedName == ANDROIDX_TRACE_FQN
    }

    private fun isSuspendCall(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
      val method = call.resolve() ?: return false
      return evaluator.isSuspend(method)
    }

    /**
     * Search all paths from the given target, verifying that all paths lead to a matching
     * `Trace.endSection()` call before returning, throwing an exception, or suspending.
     *
     * @param node the beginSection() node
     */
    private fun dfs(
      searchContext: AstSearchContext,
      node: ControlFlowGraph.Node<UElement>,
      traceSectionCount: Int,
      viaException: Boolean,
    ): SearchResult {
      if (node.visit > 0) {
        // Node already visited; don't search this path again
        return FOUND_NOTHING
      }
      node.visit++

      var openTraceSectionCount = traceSectionCount
      if (openTraceSectionCount > 0) {
        // Check for returns or uncaught exceptions when there are still open trace sections
        if (node.isExit()) {
          return if (viaException) FOUND_EXCEPTION else FOUND_RETURN
        }
      }

      val instruction = node.instruction
      if (instruction is UCallExpression) {
        if (searchContext.isEndCall(instruction)) {
          openTraceSectionCount--
          if (openTraceSectionCount <= 0) {
            // Found a matching endSection()
            return FOUND_NOTHING
          }
        } else if (
          isPlatformPublicBeginCall(instruction) ||
            isPlatformSystemBeginCall(instruction) ||
            isAndroidXBeginCall(instruction)
        ) {
          if (searchContext.isBeginCall(instruction)) {
            openTraceSectionCount++
          }
        } else {
          if (isSuspendCall(searchContext.context.evaluator, instruction)) {
            // Found a suspension point when there were still open trace sections
            return FOUND_SUSPEND
          }
        }
      }

      for ((_, next, _, isException) in node) {
        val other =
          dfs(
            searchContext,
            next,
            openTraceSectionCount,
            viaException = viaException || isException,
          )
        if (other != FOUND_NOTHING) {
          return other
        }
      }

      return FOUND_NOTHING
    }
  }
}
