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
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TypeEvaluator
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.tryResolve

/** Checks for hardcoded seeds with random numbers. */
class SecureRandomDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf(SET_SEED)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arguments = node.valueArguments
    if (arguments.isEmpty()) {
      return
    }
    val seedArgument = arguments[0].skipParenthesizedExprDown() ?: return
    val evaluator = context.evaluator
    if (
      evaluator.isMemberInClass(method, JAVA_SECURITY_SECURE_RANDOM) ||
        evaluator.isMemberInSubClassOf(method, JAVA_UTIL_RANDOM, false) &&
          isSecureRandomReceiver(node)
    ) {
      // Called with a fixed seed?
      val seed = ConstantEvaluator.evaluate(context, seedArgument)
      if (seed != null) {
        context.report(
          ISSUE,
          node,
          context.getLocation(node),
          "Do not call `setSeed()` on a `SecureRandom` with a fixed seed: " +
            "it is not secure. Use `getSeed()`.",
        )
      } else {
        // Called with a simple System.currentTimeMillis() seed or something like that?
        val seedMethod = seedArgument.tryResolve()
        if (seedMethod is PsiMethod) {
          val methodName = seedMethod.name
          if (methodName == "currentTimeMillis" || methodName == "nanoTime") {
            context.report(
              ISSUE,
              node,
              context.getLocation(node),
              "It is dangerous to seed `SecureRandom` with the current " +
                "time because that value is more predictable to " +
                "an attacker than the default seed",
            )
          }
        }
      }
    }
  }

  /** Returns true if the given invocation is assigned a SecureRandom type. */
  private fun isSecureRandomReceiver(call: UCallExpression): Boolean {
    val operand = call.receiver?.skipParenthesizedExprDown()
    return operand != null && isSecureRandomType(operand)
  }

  /** Returns true if the node evaluates to an instance of type SecureRandom. */
  private fun isSecureRandomType(node: UElement): Boolean {
    return JAVA_SECURITY_SECURE_RANDOM == TypeEvaluator.evaluate(node)?.canonicalText
  }

  companion object {
    /** Unregistered activities and services. */
    @JvmField
    val ISSUE =
      Issue.create(
          id = "SecureRandom",
          briefDescription = "Using a fixed seed with `SecureRandom`",
          explanation =
            """
                Specifying a fixed seed will cause the instance to return a predictable \
                sequence of numbers. This may be useful for testing but it is not appropriate \
                for secure use.
                """,
          moreInfo = "https://goo.gle/SecureRandom",
          category = Category.SECURITY,
          priority = 9,
          severity = Severity.WARNING,
          implementation = Implementation(SecureRandomDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
        .addMoreInfo("https://developer.android.com/reference/java/security/SecureRandom.html")

    const val JAVA_SECURITY_SECURE_RANDOM = "java.security.SecureRandom"
    const val JAVA_UTIL_RANDOM = "java.util.Random"
    private const val SET_SEED = "setSeed"
  }
}
