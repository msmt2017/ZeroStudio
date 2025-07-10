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

import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.VersionChecks.Companion.getOuterVersionCheckConstraint
import com.google.common.collect.Sets
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UQualifiedReferenceExpression

/**
 * Ensures that Cipher.getInstance is not called with AES as the parameter. Also flags usages of
 * deprecated BC provider.
 */
class CipherGetInstanceDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf(GET_INSTANCE)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInSubClassOf(method, JAVAX_CRYPTO_CIPHER, false)) {
      return
    }
    val arguments = node.valueArguments
    if (arguments.isNotEmpty()) {
      val expression = arguments[0]
      val transformation = ConstantEvaluator.evaluate(context, expression)
      if (transformation is String) {
        checkTransformation(
          context,
          node,
          expression,
          transformation,
          expression !is ULiteralExpression,
        )
      }
    }
    if (arguments.size == 2) {
      val expression = arguments[1]
      val provider = ConstantEvaluator.evaluate(context, expression)
      if (provider is String) {
        checkProvider(context, node, expression, provider)
      } else if (expression is UQualifiedReferenceExpression) {
        val selector = expression.selector
        if (selector is UCallExpression) {
          // Are we passing in Security.getProvider("BC") ?
          val getProvider = selector.resolve()
          if (getProvider?.name == "getProvider") {
            val args = selector.valueArguments
            if (args.size == 1) {
              val nestedProvider = ConstantEvaluator.evaluate(context, args[0])
              if (nestedProvider is String) {
                checkProvider(context, node, expression, nestedProvider)
              }
            }
          }
        }
      }
    }
  }

  private fun checkTransformation(
    context: JavaContext,
    call: UCallExpression,
    node: UElement,
    transformation: String,
    includeValue: Boolean,
  ) {
    if (ALGORITHM_ONLY.contains(transformation)) {
      val message =
        "`Cipher.getInstance` should not be called without setting the" +
          " encryption mode and padding"
      context.report(ISSUE, call, context.getLocation(node), message)
    } else if (
      (transformation.contains("/ECB/") || transformation.endsWith("/ECB")) &&
        !transformation.startsWith("RSA/")
    ) {
      var message = "ECB encryption mode should not be used"
      if (includeValue) {
        message += " (was \"$transformation\")"
      }
      context.report(ISSUE, call, context.getLocation(node), message)
    }
  }

  private fun checkProvider(
    context: JavaContext,
    call: UCallExpression,
    node: UElement,
    provider: String,
  ) {
    if (provider == "BC") {
      val atLeastP = ApiConstraint.get(28)
      val constraint = getOuterVersionCheckConstraint(context, call)
      if (
        constraint != null &&
          constraint != ApiConstraint.UNKNOWN &&
          constraint.not().alwaysAtLeast(atLeastP)
      ) {
        return
      }
      val incident = Incident(DEPRECATED_PROVIDER, call, context.getLocation(node), "")
      context.report(incident, map())
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val prefix =
      if (context.mainProject.targetSdkVersion.featureLevel >= 28) {
        "The `BC` provider is deprecated and as of Android P " +
          "this method will throw a `NoSuchAlgorithmException`."
      } else {
        "The `BC` provider is deprecated and when `targetSdkVersion` is moved " +
          "to `P` this method will throw a `NoSuchAlgorithmException`."
      }
    val message =
      prefix +
        " To fix " +
        "this you should stop specifying a provider and use the default " +
        "implementation"
    incident.message = message
    return true
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "GetInstance",
        briefDescription = "Cipher.getInstance with ECB",
        explanation =
          """
                `Cipher#getInstance` should not be called with ECB as the cipher mode or \
                without setting the cipher mode because the default mode on android is \
                ECB, which is insecure.""",
        moreInfo = "https://goo.gle/GetInstance",
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    @JvmField
    val DEPRECATED_PROVIDER =
      Issue.create(
          id = "DeprecatedProvider",
          briefDescription = "Using BC Provider",
          explanation =
            """
        The `BC` provider has been deprecated and will not be provided when `targetSdkVersion` is P or higher.
        """,
          moreInfo =
            "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
          category = Category.SECURITY,
          priority = 9,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation =
            Implementation(CipherGetInstanceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
        .addMoreInfo("https://goo.gle/DeprecatedProvider")

    private const val JAVAX_CRYPTO_CIPHER = "javax.crypto.Cipher"
    private const val GET_INSTANCE = "getInstance"
    private val ALGORITHM_ONLY = Sets.newHashSet("AES", "DES", "DESede")
  }
}
