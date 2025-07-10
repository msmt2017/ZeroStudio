/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.lint.checks.SecureRandomDetector.Companion.JAVA_SECURITY_SECURE_RANDOM
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression

/**
 * Flags code that is asking for a provider that is no longer available in more recent versions of
 * the platform.
 */
class DeletedProviderDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames(): List<String> {
    return listOf(GET_INSTANCE)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val qualifiedName = method.containingClass?.qualifiedName ?: return
    if (qualifiedName != JAVAX_CRYPTO_CIPHER && qualifiedName != JAVA_SECURITY_SECURE_RANDOM) {
      return
    }

    // These are the matching methods for {Cipher,SecureRandom}.getInstance(...) :
    //     public static SecureRandom getInstance(String algorithm)
    //     public static SecureRandom getInstance(String algorithm, String provider)
    //     public static SecureRandom getInstance(String algorithm, Provider provider)
    //     public static final Cipher getInstance(String transformation)
    //     public static final Cipher getInstance(String transformation, String provider)
    //     public static final Cipher getInstance(String transformation, Provider provider)
    // We care about the ones passing in a string provider.

    val arguments = node.valueArguments
    if (arguments.size != 2) {
      return
    }
    val expression = arguments[1]
    val expressionType = expression.getExpressionType()
    if (expressionType != null && expressionType.canonicalText != JAVA_LANG_STRING) {
      return
    }
    val value = ConstantEvaluator.evaluate(context, expression)
    if (value == "Crypto") {
      context.report(
        ISSUE,
        expression,
        context.getLocation(expression),
        "The Crypto provider has been deleted " +
          "in Android P (and was deprecated in Android N), so the code will crash",
      )
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
          id = "DeletedProvider",
          briefDescription = "Using Deleted Provider",
          explanation =
            """
                The `Crypto` provider has been completely removed in Android P (and was \
                deprecated in an earlier release). This means that the code will throw a \
                `NoSuchProviderException` and the app will crash. Even if the code catches \
                that exception at a higher level, this is not secure and should not be \
                used.
                """,
          moreInfo =
            "https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html",
          category = Category.SECURITY,
          priority = 9,
          severity = Severity.ERROR,
          androidSpecific = true,
          implementation =
            Implementation(DeletedProviderDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
        .addMoreInfo("https://goo.gle/DeletedProvider")

    private const val JAVAX_CRYPTO_CIPHER = "javax.crypto.Cipher"
    private const val GET_INSTANCE = "getInstance"
  }
}
