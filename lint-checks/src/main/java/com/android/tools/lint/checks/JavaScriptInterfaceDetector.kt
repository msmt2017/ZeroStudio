/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TypeEvaluator
import com.android.tools.lint.detector.api.isAndroidProject
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UastVisibility

/**
 * Looks for addJavascriptInterface calls on interfaces have been properly annotated with
 * `@JavaScriptInterface`
 */
class JavaScriptInterfaceDetector : Detector(), SourceCodeScanner {

  // ---- implements SourceCodeScanner ----

  override fun getApplicableMethodNames(): List<String> {
    return listOf(ADD_JAVASCRIPT_INTERFACE)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arguments = node.valueArguments
    if (arguments.size != 2) {
      return
    }

    val evaluator = context.evaluator
    if (!evaluator.isMemberInClass(method, WEB_VIEW_CLS)) {
      return
    }

    val first = arguments[0]
    val evaluated = TypeEvaluator.evaluate(first)
    if (evaluated is PsiClassType) {
      val cls = evaluated.resolve() ?: return
      if (isJavaScriptAnnotated(cls)) {
        return
      }

      val location = context.getNameLocation(node)
      val message =
        "None of the methods in the added interface (${cls.name}) have " +
          "been annotated with `@android.webkit.JavascriptInterface`; they will not " +
          "be visible in API 17"
      val incident = Incident(ISSUE, node, location, message)
      context.report(incident, targetSdkAtLeast(17))
    }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>>? =
    listOf(UDeclaration::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    return object : UElementHandler() {
      override fun visitDeclaration(node: UDeclaration) {
        if (node.visibility == UastVisibility.PUBLIC) {
          return
        }
        for (annotation in node.uAnnotations) {
          if (annotation.qualifiedName == JAVASCRIPT_INTERFACE_CLS) {
            val incident =
              Incident(
                ISSUE,
                node as UElement,
                context.getNameLocation(node),
                "Must be public when using `@JavascriptInterface`",
              )
            context.report(incident, isAndroidProject())
          }
        }
      }
    }
  }

  companion object {
    /** The main issue discovered by this detector. */
    @JvmField
    val ISSUE =
      Issue.create(
          id = "JavascriptInterface",
          briefDescription = "Missing @JavascriptInterface on methods",
          explanation =
            """
                As of API 17, you must annotate methods in objects registered with the \
                `addJavascriptInterface` method with a `@JavascriptInterface` annotation.
                """,
          category = Category.SECURITY,
          moreInfo =
            "https://developer.android.com/reference/android/webkit/WebView.html#addJavascriptInterface(java.lang.Object, java.lang.String)",
          androidSpecific = true,
          priority = 8,
          severity = Severity.ERROR,
          implementation =
            Implementation(JavaScriptInterfaceDetector::class.java, Scope.JAVA_FILE_SCOPE),
        )
        .addMoreInfo("https://goo.gle/JavascriptInterface")

    private const val ADD_JAVASCRIPT_INTERFACE = "addJavascriptInterface"
    private const val JAVASCRIPT_INTERFACE_CLS = "android.webkit.JavascriptInterface"
    private const val WEB_VIEW_CLS = "android.webkit.WebView"

    private fun isJavaScriptAnnotated(clz: PsiClass?): Boolean {
      var current = clz
      while (current != null) {
        val modifierList = current.modifierList
        if (modifierList?.findAnnotation(JAVASCRIPT_INTERFACE_CLS) != null) {
          return true
        }

        for (method in current.methods) {
          if (method.modifierList.findAnnotation(JAVASCRIPT_INTERFACE_CLS) != null) {
            return true
          }
        }

        current = current.superClass
      }

      return false
    }
  }
}
