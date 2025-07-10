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

import com.android.SdkConstants.CLASS_VIEW
import com.android.SdkConstants.FQCN_SCROLL_VIEW
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTypes
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.psi.KtConstantExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * Reports overrides of `View.onInitializeAccessibilityNodeInfo(info)` when all the following are
 * true:
 * - In the function body, we see at least one of
 *   `info.addAction(ACTION_SCROLL_{FORWARD,BACKWARD})`.
 * - In the function body, we do NOT see any of
 *   `info.addAction(ACTION_SCROLL_{UP,DOWN,LEFT,RIGHT})`.
 * - In the function body, `info` does NOT escape (ignoring escape via a call to the super method).
 * - The containing class (subclass of `View`) is NOT a subclass of `ScrollView`, but behaves like a
 *   `ScrollView`; that is, at least one of the following must hold:
 *     - In the function body, we see `info.setCollectionInfo(...)`.
 *     - In the function body, we see `info.setClassName(S)`.
 *     - The containing class overrides `getAccessibilityClassName` and just returns `S`.
 *
 * ...where S is a String that contains "ScrollView" or contains a reference to `ScrollView.class`
 * (to allow for expressions like `ScrollView.class.getName()`).
 */
class AccessibilityViewScrollActionsDetector : Detector(), SourceCodeScanner {

  override fun applicableSuperClasses() = listOf(CLASS_VIEW)

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val psiClass = declaration.javaPsi as? PsiClass ?: return
    checkInitializeMethod(context, psiClass)
  }

  private fun checkInitializeMethod(context: JavaContext, psiClass: PsiClass) {
    // Must be a subclass of the View class, but not the View class itself.
    if (!context.evaluator.inheritsFrom(psiClass, CLASS_VIEW, strict = true)) return

    // Stop early if this is a subclass of ScrollView (or is ScrollView itself), as ScrollView
    // implements things correctly.
    if (context.evaluator.inheritsFrom(psiClass, FQCN_SCROLL_VIEW, strict = false)) return

    /**
     * Returns true if [method]'s signature is `void
     * onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo p0)` and is an override.
     */
    fun isInitializeMethod(method: PsiMethod): Boolean {
      if (PsiTypes.voidType() != method.returnType) return false
      val parameters = method.parameterList.parameters
      if (parameters.size != 1) return false
      val parameter = parameters[0]
      if (parameter.isVarArgs) return false
      val type = parameter.type
      if (type !is PsiClassReferenceType) return false
      if (type.reference.qualifiedName != "android.view.accessibility.AccessibilityNodeInfo")
        return false
      return context.evaluator.isOverride(method, includeInterfaces = false)
    }

    /**
     * Returns true if [method]'s signature is `CharSequence getAccessibilityClassName()` and is an
     * override.
     */
    fun isGetAccessibilityClassNameMethod(method: PsiMethod): Boolean {
      val returnType = method.returnType as? PsiClassReferenceType ?: return false
      if (returnType.reference.qualifiedName != "java.lang.CharSequence") return false
      val parameters = method.parameterList.parameters
      if (parameters.isNotEmpty()) return false
      return context.evaluator.isOverride(method, includeInterfaces = false)
    }

    // We must find the overridden onInitializeAccessibilityNodeInfo method.
    val initializeMethod =
      psiClass
        .findMethodsByName("onInitializeAccessibilityNodeInfo", false)
        .asSequence()
        .singleOrNull { isInitializeMethod(it) }
        ?.toUElementOfType<UMethod>() ?: return

    // We might find the overridden getAccessibilityClassName method, and it might trivially return
    // "android.widget.ScrollView" (imitating the ScrollView class).
    fun overridesAccessibilityClassNameAsScrollView(): Boolean {
      val classNameMethod =
        psiClass
          .findMethodsByName("getAccessibilityClassName", false)
          .asSequence()
          .singleOrNull { isGetAccessibilityClassNameMethod(it) }
          ?.toUElementOfType<UMethod>() ?: return false
      // We only check for the simple case where there is just a return expression inside a block.
      // This still works for Kotlin single-expression functions like `fun a(): Type = blah`.
      val block = classNameMethod.uastBody as? UBlockExpression ?: return false
      val expressions = block.expressions
      if (expressions.size != 1) return false
      val returnExpression = expressions[0] as? UReturnExpression ?: return false
      return returnExpression.returnExpression?.isScrollViewClassNameString() ?: false
    }

    // Scan the initialize method, tracking uses of the first param. We are looking for certain
    // method calls, like info.addAction(...) and info.setCollectionInfo(...).
    val accessibilityNodeInfoParam = initializeMethod.uastParameters.getOrNull(0) ?: return

    var addsForwardBackward = false
    var addsUpDownLeftRight = false
    var setsCollectionInfo = false
    var setsClassNameToScrollView = false

    val superMethods =
      lazy(LazyThreadSafetyMode.NONE) {
        (initializeMethod.javaPsi as? PsiMethod)?.findSuperMethods() ?: emptyArray<PsiMethod>()
      }

    val dfa =
      object : EscapeCheckingDataFlowAnalyzer(listOf(accessibilityNodeInfoParam)) {

        override fun argument(call: UCallExpression, reference: UElement) {
          val isSuperCall =
            call.resolve()?.let { resolved ->
              superMethods.value.any { sup -> resolved.isEquivalentTo(sup) }
            } ?: false
          // Ignore escapes from calls to super method. There is a risk of false-warnings here: the
          // onInitializeAccessibilityNodeInfo override could call
          // info.addAction(ACTION_SCROLL_FORWARD) and the super method could call
          // info.addAction(ACTION_SCROLL_UP). However, this seems very unlikely.
          if (!isSuperCall) {
            super.argument(call, reference)
          }
        }

        override fun receiver(call: UCallExpression) {
          super.receiver(call)
          when (call.methodName) {
            "addAction" -> {
              val param = call.getArgumentForParameter(0) ?: return
              val paramField = (param as? UReferenceExpression)?.resolve() as? PsiField
              if (paramField != null) {
                when (paramField.name) {
                  "ACTION_SCROLL_FORWARD",
                  "ACTION_SCROLL_BACKWARD" -> addsForwardBackward = true
                  "ACTION_SCROLL_LEFT",
                  "ACTION_SCROLL_RIGHT",
                  "ACTION_SCROLL_UP",
                  "ACTION_SCROLL_DOWN" -> addsUpDownLeftRight = true
                  else -> {}
                }
              } else {
                val paramValue = param.evaluate()
                if (paramValue is Int) {
                  when (paramValue) {
                    ACTION_SCROLL_FORWARD,
                    ACTION_SCROLL_BACKWARD -> addsForwardBackward = true
                    // The up, down, left, right values are resource ids; we skip checking for
                    // these, as we assume code will instead just reference the fields that are
                    // checked above.
                    else -> {}
                  }
                }
              }
            }
            "setCollectionInfo" -> {
              val paramPsi = call.getArgumentForParameter(0)?.sourcePsi ?: return
              if (paramPsi is PsiLiteralExpression || paramPsi is KtConstantExpression) {
                // assume this is null
                return
              }
              // Otherwise:
              setsCollectionInfo = true
            }
            "setClassName" -> {
              val param = call.getArgumentForParameter(0) ?: return
              val paramPsi = param.sourcePsi
              if (paramPsi is PsiLiteralExpression || paramPsi is KtConstantExpression) {
                // assume this is null
                return
              }
              if (param.isScrollViewClassNameString()) {
                setsClassNameToScrollView = true
              }
            }
          }
        }
      }
    initializeMethod.accept(dfa)

    if (
      !dfa.escaped &&
        addsForwardBackward &&
        !addsUpDownLeftRight &&
        (setsCollectionInfo ||
          setsClassNameToScrollView ||
          overridesAccessibilityClassNameAsScrollView())
    ) {
      context.report(
        issue = ISSUE,
        scope =
          initializeMethod
            as? UElement, // The cast is needed to disambiguate the report function signature.
        location = context.getLocation(initializeMethod),
        message =
          "Views that behave like `ScrollView` and support `ACTION_SCROLL_{FORWARD,BACKWARD}` should also support " +
            "`ACTION_SCROLL_{LEFT,RIGHT}` and/or `ACTION_SCROLL_{UP,DOWN}`",
      )
    }
  }

  companion object {
    private const val ACTION_SCROLL_FORWARD: Int = 1 shl 12
    private const val ACTION_SCROLL_BACKWARD: Int = 1 shl 13

    @JvmField
    val ISSUE =
      Issue.create(
        id = "AccessibilityScrollActions",
        briefDescription = "Incomplete Scroll Action support",
        explanation =
          """
          Views that behave like `ScrollView` and support `ACTION_SCROLL_{FORWARD,BACKWARD}` should also support \
          `ACTION_SCROLL_{LEFT,RIGHT}` and/or `ACTION_SCROLL_{UP,DOWN}`.
          """,
        category = Category.A11Y,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(AccessibilityViewScrollActionsDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )
  }
}

private fun UExpression.isScrollViewClassNameString(): Boolean {
  // E.g.
  // "android.widget.ScrollView"
  // "CustomScrollView"
  val name = this.evaluate() as? String
  if (name?.contains("ScrollView") == true) return true

  // For something like `ScrollView::class.java.name`, evaluate() does not work.
  // We just check if the expression contains the ScrollView class literal.
  var foundClassLiteral = false
  this.accept(
    object : AbstractUastVisitor() {
      override fun visitClassLiteralExpression(node: UClassLiteralExpression): Boolean {
        val type = node.type
        if (type is PsiClassReferenceType && type.reference.qualifiedName == FQCN_SCROLL_VIEW) {
          foundClassLiteral = true
          // Stop visiting.
          return true
        }
        return super.visitClassLiteralExpression(node)
      }
    }
  )

  return foundClassLiteral
}
