/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TypeEvaluator
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.minSdkLessThan
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.tryResolve

/** Warns about a couple of broken iterators on Android N. */
class IteratorDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION = Implementation(IteratorDetector::class.java, Scope.JAVA_FILE_SCOPE)

    // TODO: Split into two separate issues
    // Actually include ConcurrentHashMap too
    @JvmField
    val ISSUE =
      Issue.create(
        id = "BrokenIterator",
        briefDescription = "Broken Iterator",
        explanation =
          """
                **For LinkedHashMap:**

                The spliterators returned by `LinkedHashMap` in Android Nougat (API levels 24 \
                and 25) use the wrong order (inconsistent with the iterators, which use \
                the correct order), despite reporting `Spliterator.ORDERED`. You may use the \
                following code fragments to obtain a correctly ordered `Spliterator` on API \
                level 24 and 25:

                For a Collection view `c = lhm.entrySet()`, `c = lhm.keySet()` or \
                `c = lhm.values()`, use \
                `java.util.Spliterators.spliterator(c, c.spliterator().characteristics())` \
                instead of `c.spliterator()`.

                Instead of `c.stream()` or `c.parallelStream()`, use \
                `java.util.stream.StreamSupport.stream(spliterator, false)` to construct a \
                (nonparallel) Stream from such a `Spliterator`.

                **For Vector:**

                The `listIterator()` returned for a `Vector` has a broken `add()` implementation \
                on Android N (API level 24). Consider switching to `ArrayList` and if necessary \
                adding synchronization.
            """,
        moreInfo = "https://developer.android.com/reference/java/util/LinkedHashMap",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("add", "spliterator", "stream", "parallelStream")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val receiver = node.receiver?.skipParenthesizedExprDown() ?: return

    val name = method.name

    if (name == "add") {
      // This is a common method; we only care about it on iterators.
      // Bail early to avoid flow analysis when not necessary.
      val c = method.containingClass?.name
      if (c == null || !c.endsWith("Iterator")) {
        return
      }
    }

    val resolved = receiver.tryResolve() ?: return
    val variable = resolved as? PsiVariable ?: return
    val initializer =
      UastLintUtils.findLastAssignment(variable, node)?.skipParenthesizedExprDown() ?: return
    if (initializer is UQualifiedReferenceExpression) {
      val r = initializer.receiver.skipParenthesizedExprDown()
      val type = TypeEvaluator.evaluate(r) ?: return
      val canonical = (type as? PsiClassType)?.rawType()?.canonicalText
      if (canonical == "java.util.LinkedHashMap") {
        // Look for acceptable uses: passing to the workaround functions
        val pp = skipParenthesizedExprUp(skipParenthesizedExprUp(node.uastParent)?.uastParent)
        if (
          pp is UQualifiedReferenceExpression &&
            (pp.selector as? UCallExpression)?.methodName == "characteristics"
        ) {
          return
        }

        val collection = receiver.asSourceString()
        val workaround =
          if (name == "spliterator") {
            "Use `java.util.Spliterators.spliterator($collection, $collection.spliterator().characteristics())`"
          } else {
            "Use `java.util.stream.StreamSupport.stream(spliterator, false)`"
          }
        // b/33945212
        context.report(
          Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "`LinkedHashMap#$name` was broken in API 24 and 25. Workaround: $workaround",
          ),
          minSdkLessThan(26),
        )
      } else if (canonical == "java.util.Vector") {
        // b/30974375
        context.report(
          Incident(
            ISSUE,
            node,
            context.getLocation(node),
            "`Vector#listIterator` was broken in API 24 and 25; it can return `hasNext()=false` before the last element. Consider switching to `ArrayList` with synchronization if you need it.",
          ),
          minSdkLessThan(26),
        )
      }
    }
  }
}
