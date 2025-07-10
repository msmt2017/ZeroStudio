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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Looks for classes that continue to proceed in WebViewClient#onReceivedSslError */
class WebViewClientDetector : Detector(), SourceCodeScanner {
  override fun applicableSuperClasses(): List<String> {
    return listOf("android.webkit.WebViewClient")
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val evaluator = context.evaluator
    for (method in declaration.methods.filter { it.name == "onReceivedSslError" }) {
      if (evaluator.isAbstract(method)) {
        continue
      }
      val visitor = OnReceivedSslErrorBodyVisitor(context)
      method.accept(visitor)
    }
  }

  private class OnReceivedSslErrorBodyVisitor(private val context: JavaContext) :
    AbstractUastVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node.isMethodCall()) {
        val receiver = node.receiver
        val receiverType = receiver?.getExpressionType()
        val methodName = node.methodName
        if (
          receiverType != null &&
            methodName != null &&
            receiverType.canonicalText == "android.webkit.SslErrorHandler" &&
            methodName == "proceed"
        ) {
          val message =
            "Permitting connections with SSL-related errors could allow " +
              "eavesdroppers to intercept data sent by your app, which impacts " +
              "the privacy of your users. Consider canceling the connections by " +
              "invoking `SslErrorHandler#cancel()`."
          context.report(PROCEEDS_ON_RECEIVED_SSL_ERROR, node, context.getLocation(node), message)
        }
      }
      return super.visitCallExpression(node)
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(WebViewClientDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val PROCEEDS_ON_RECEIVED_SSL_ERROR =
      create(
        id = "WebViewClientOnReceivedSslError",
        briefDescription = "Proceeds with the HTTPS connection despite SSL errors",
        explanation =
          "This check looks for `onReceivedSslError` implementations " +
            "that invoke `SslErrorHandler#proceed`.",
        moreInfo = "https://goo.gle/WebViewClientOnReceivedSslError",
        category = Category.SECURITY,
        priority = 5,
        androidSpecific = true,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )
  }
}
