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

import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.CLASS_VIEW
import com.android.support.AndroidxName
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Makes sure that methods call super when overriding methods. */
class CallSuperDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    const val KEY_METHOD = "method"

    private val IMPLEMENTATION =
      Implementation(CallSuperDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Missing call to super. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MissingSuperCall",
        briefDescription = "Missing Super Call",
        explanation =
          """
            Some methods, such as `View#onDetachedFromWindow`, require that you also call the \
            super implementation as part of your method.
            """,
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    val CALL_SUPER_ANNOTATION = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "CallSuper")
    private const val AOSP_CALL_SUPER_ANNOTATION = "android.annotation.CallSuper"
    private const val ON_DETACHED_FROM_WINDOW = "onDetachedFromWindow"
    private const val ON_VISIBILITY_CHANGED = "onVisibilityChanged"

    /**
     * Checks whether the given method overrides a method which requires the super method to be
     * invoked, and if so, returns it (otherwise returns null)
     */
    fun getRequiredSuperMethod(evaluator: JavaEvaluator, method: PsiMethod): PsiMethod? {

      if (evaluator.isAbstract(method)) return null

      for (directSuper in method.findSuperMethods()) {
        if (evaluator.isAbstract(directSuper)) continue

        val name = method.name
        if (ON_DETACHED_FROM_WINDOW == name) {
          // No longer annotated on the framework method since it's
          // now handled via onDetachedFromWindowInternal, but overriding
          // is still dangerous if supporting older versions so flag
          // this for now (should make annotation carry metadata like
          // compileSdkVersion >= N).
          if (evaluator.isMemberInSubClassOf(method, CLASS_VIEW, false)) {
            return directSuper
          }
        } else if (ON_VISIBILITY_CHANGED == name) {
          // From Android Wear API; doesn't yet have an annotation
          // but we want to enforce this right away until the AAR
          // is updated to supply it once @CallSuper is available in
          // the support library
          if (
            evaluator.isMemberInSubClassOf(
              method,
              "android.support.wearable.watchface.WatchFaceService.Engine",
              false,
            )
          ) {
            return directSuper
          }
        }

        val annotations = evaluator.getAnnotations(directSuper, true)
        for (annotation in annotations) {
          val signature = annotation.qualifiedName
          if (
            CALL_SUPER_ANNOTATION.isEquals(signature) ||
              signature == AOSP_CALL_SUPER_ANNOTATION ||
              signature != null &&
                (signature.endsWith(".OverrideMustInvoke") ||
                  signature.endsWith(".OverridingMethodsMustInvokeSuper"))
          ) {
            return directSuper
          }
        }
      }

      return null
    }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> = listOf(UMethod::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler =
    object : UElementHandler() {
      override fun visitMethod(node: UMethod) {
        val evaluator = context.evaluator
        val superMethod = getRequiredSuperMethod(evaluator, node.javaPsi) ?: return
        val visitor = SuperCallVisitor(superMethod, node.getContainingUClass()?.sourcePsi)
        node.accept(visitor)
        val count = visitor.callsSuperCount
        if (count == 0) {
          val methodName = node.name
          // Temporary workaround for 180509152:
          if (methodName == "onCreate" && visitor.anySuperCallCount > 0) {
            val superMethodClass = superMethod.containingClass?.qualifiedName
            if (superMethodClass == "androidx.appcompat.app.AppCompatActivity") {
              // In recent versions this class has no onCreate implementation
              // and the super call visitor should not have found one; the
              // implementation it should jump to is in indirect superclass
              // FragmentActivity. For now hide this message.
              return
            }
          }
          val message = "Overriding method should call `super.$methodName`"
          val location = context.getNameLocation(node)
          val fix = fix().data(KEY_METHOD, superMethod)
          context.report(ISSUE, node, location, message, fix)
        } else if (count > 1 && node.name == "onCreate") {
          val overlap = visitor.findFirstOverlap(node) ?: return
          val message = "Calling `super.${node.name}` more than once can lead to crashes"
          val location = context.getNameLocation(overlap)
          context.report(ISSUE, node, location, message)
        }
      }
    }

  /** Visits a method and determines whether the method calls its super method. */
  private class SuperCallVisitor
  constructor(private val targetMethod: PsiMethod, private val childClass: PsiElement?) :
    AbstractUastVisitor() {
    val superCalls = mutableListOf<USuperExpression>()
    val callsSuperCount: Int
      get() = superCalls.size

    var anySuperCallCount: Int = 0

    private val targetMethodName = targetMethod.name
    private val targetSuperClassName = targetMethod.containingClass?.qualifiedName

    override fun visitSuperExpression(node: USuperExpression): Boolean {
      val containingClass = node.getContainingUClass()?.sourcePsi
      if (childClass == null || containingClass?.isEquivalentTo(childClass) != false) {
        anySuperCallCount++
        val parent = skipParenthesizedExprUp(node.uastParent)
        if (parent is UReferenceExpression) {
          val resolved = parent.resolve() as? PsiMethod
          val resolvedMethodName = resolved?.name
          val resolvedClassName = resolved?.containingClass?.qualifiedName
          if (
            resolved == null ||
              resolvedMethodName == null ||
              resolvedClassName == null || // Avoid false positives for type resolution problems
              resolvedMethodName == targetMethodName && resolvedClassName == targetSuperClassName
          ) {
            superCalls.add(node)
          }
        }
      }

      return super.visitSuperExpression(node)
    }

    fun findFirstOverlap(method: UMethod): USuperExpression? {
      for (i in 0 until superCalls.size) {
        for (j in i + 1 until superCalls.size) {
          if (CutPasteDetector.isReachableFrom(method, superCalls[i], superCalls[j])) {
            return superCalls[j]
          }
        }
      }

      return null
    }
  }
}
