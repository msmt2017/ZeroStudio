/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.lint.checks.CutPasteDetector.isReachableFrom
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.getUMethod
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Checks related to RecyclerView usage. */
class RecyclerViewDetector : Detector(), SourceCodeScanner {

  // ---- implements SourceCodeScanner ----

  override fun applicableSuperClasses(): List<String> {
    return listOf(SUPERCLASS_SUPPORTLIB, SUPERCLASS_ANDROIDX)
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(NOTIFY_DATA_SET_CHANGED)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val evaluator = context.evaluator
    val psiClass = declaration.javaPsi
    for (method in psiClass.findMethodsByName(ON_BIND_VIEW_HOLDER, false)) {
      val size = evaluator.getParameterCount(method)
      if (size == 2 || size == 3) {
        checkMethod(context, method, psiClass)
      }
    }
  }

  private fun checkMethod(context: JavaContext, declaration: PsiMethod, cls: PsiClass) {
    val parameters = declaration.parameterList.parameters
    val viewHolder = parameters[0]
    val parameter = parameters[1]

    val visitor = ParameterEscapesVisitor(cls, parameter)
    val method = declaration.getUMethod() ?: return
    method.accept(visitor)
    if (visitor.variableEscapes()) {
      reportError(context, viewHolder, parameter)
    }

    // Look for pending data binder calls that aren't executed before the method finishes
    checkDataBinders(context, method, visitor.dataBinders)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (
      method.name == NOTIFY_DATA_SET_CHANGED &&
        (context.evaluator.isMemberInSubClassOf(method, SUPERCLASS_SUPPORTLIB) ||
          context.evaluator.isMemberInSubClassOf(method, SUPERCLASS_ANDROIDX))
    ) {
      context.report(
        CLEAR_ALL_DATA,
        context.getLocation(node),
        "It will always be more efficient to use more specific change events if " +
          "you can. Rely on `notifyDataSetChanged` as a last resort.",
      )
    }
  }

  private fun reportError(context: JavaContext, viewHolder: PsiParameter, parameter: PsiParameter) {
    var variablePrefix = viewHolder.name
    if (variablePrefix == null) {
      variablePrefix = "ViewHolder"
    }
    val message =
      "Do not treat position as fixed; only use immediately " +
        "and call `$variablePrefix.getAdapterPosition()` to look it up later"
    context.report(FIXED_POSITION, parameter, context.getLocation(parameter), message)
  }

  private fun checkDataBinders(
    context: JavaContext,
    declaration: UMethod,
    references: List<UCallExpression>?,
  ) {
    if (references != null && references.isNotEmpty()) {
      val targets = Lists.newArrayList<UCallExpression>()
      val sources = Lists.newArrayList<UCallExpression>()
      for (ref in references) {
        if (isExecutePendingBindingsCall(ref)) {
          targets.add(ref)
        } else {
          sources.add(ref)
        }
      }

      // Only operate on the last call in each block: ignore siblings with the same parent
      // That way if you have
      //     dataBinder.foo();
      //     dataBinder.bar();
      //     dataBinder.baz();
      // we only flag the *last* of these calls as needing an executePendingBindings
      // afterwards. We do this with a parent map such that we correctly pair
      // elements when they have nested references within (such as if blocks.)
      val parentToChildren = Maps.newHashMap<UElement, UCallExpression>()
      for (reference in sources) {
        // Note: We're using a map, not a multimap, and iterating forwards:
        // this means that the *last* element will overwrite previous entries,
        // and we end up with the last reference for each parent which is what we
        // want
        val statement = reference.getParentOfType<UExpression>(UExpression::class.java, true)
        if (statement != null) {
          parentToChildren[statement.uastParent] = reference
        }
      }

      for (source in parentToChildren.values) {
        val sourceBinderReference = source.receiver ?: continue
        val sourceDataBinder = getDataBinderReference(sourceBinderReference) ?: continue

        var reachesTarget = false
        for (target in targets) {
          if (
            sourceDataBinder == getDataBinderReference(target.receiver) &&
              // TODO: Provide full control flow graph, or at least provide an
              // isReachable method which can take multiple targets
              isReachableFrom(declaration, source, target)
          ) {
            reachesTarget = true
            break
          }
        }
        if (!reachesTarget) {
          val lhs = sourceBinderReference.asSourceString()
          val message =
            "You must call `$lhs.executePendingBindings()` " +
              "before the `onBind` method exits, otherwise, the DataBinding " +
              "library will update the UI in the next animation frame " +
              "causing a delayed update & potential jumps if the item " +
              "resizes."
          val location = context.getLocation(source)
          context.report(DATA_BINDER, source, location, message)
        }
      }
    }
  }

  private fun isExecutePendingBindingsCall(call: UCallExpression): Boolean {
    return "executePendingBindings" == getMethodName(call)
  }

  /**
   * Determines whether a given variable "escapes" either to a field or to a nested runnable. (We
   * deliberately ignore variables that escape via method calls.)
   */
  private class ParameterEscapesVisitor(private val bindClass: PsiClass, variable: PsiParameter) :
    AbstractUastVisitor() {
    private val variables: MutableList<PsiVariable>
    private var escapes: Boolean = false
    private var foundInnerClass: Boolean = false
    var dataBinders: MutableList<UCallExpression>? = null

    init {
      variables = Lists.newArrayList(variable)
    }

    fun variableEscapes(): Boolean {
      return escapes
    }

    override fun visitVariable(node: UVariable): Boolean {
      val initializer = node.uastInitializer
      if (initializer is UReferenceExpression) {
        val resolved = initializer.resolve()

        if (resolved != null && variables.contains(resolved)) {
          if (resolved is ULocalVariable) {
            variables.add(node)
          } else if (resolved is PsiField) {
            escapes = true
          }
        }
      }

      return super.visitVariable(node)
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (node.operator is UastBinaryOperator.AssignOperator) {
        val rhs = node.rightOperand
        var clearLhs = true
        if (rhs is UReferenceExpression) {
          val resolved = rhs.resolve()

          if (resolved != null && variables.contains(resolved)) {
            clearLhs = false
            val resolvedLhs = node.leftOperand.tryResolve()
            if (resolvedLhs is PsiLocalVariable) {
              variables.add(resolvedLhs)
            } else if (resolvedLhs is PsiField) {
              escapes = true
            }
          }
        }
        if (clearLhs) {
          // If we reassign one of the variables, clear it out
          val resolved = node.leftOperand.tryResolve()
          if (resolved != null) {

            variables.remove(resolved)
          }
        }
      }
      return super.visitBinaryExpression(node)
    }

    override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression): Boolean {
      if (foundInnerClass) {
        // Check to see if this reference is inside the same class as the original
        // onBind (e.g. is this a reference from an inner class, or a reference
        // to a variable assigned from there)
        val resolved = node.resolve()

        if (resolved != null && variables.contains(resolved)) {
          val outer = node.getParentOfType<UElement>(UClass::class.java, true)
          if (bindClass != outer) {
            escapes = true
          }
        }
      }

      return super.visitSimpleNameReferenceExpression(node)
    }

    override fun visitClass(node: UClass): Boolean {
      if (node is UAnonymousClass || !node.isStatic) {
        foundInnerClass = true
      }

      return super.visitClass(node)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node.isMethodCall()) {
        val methodExpression = node.receiver
        val dataBinder = getDataBinderReference(methodExpression)

        if (dataBinder != null) {
          val list =
            dataBinders
              ?: run {
                val new = Lists.newArrayList<UCallExpression>()
                dataBinders = new
                new
              }
          list.add(node)
        }
      }

      return super.visitCallExpression(node)
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(RecyclerViewDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val FIXED_POSITION =
      Issue.create(
        id = "RecyclerView",
        briefDescription = "RecyclerView Problems",
        explanation =
          """
                `RecyclerView` will **not** call `onBindViewHolder` again when the position \
                of the item changes in the data set unless the item itself is invalidated or \
                the new position cannot be determined.

                For this reason, you should **only** use the position parameter while \
                acquiring the related data item inside this method, and should **not** keep \
                a copy of it.

                If you need the position of an item later on (e.g. in a click listener), use \
                `getAdapterPosition()` which will have the updated adapter position.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        androidSpecific = true,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val DATA_BINDER =
      Issue.create(
        id = "PendingBindings",
        briefDescription = "Missing Pending Bindings",
        explanation =
          """
                When using a `ViewDataBinding` in a `onBindViewHolder` method, you **must** \
                call `executePendingBindings()` before the method exits; otherwise the data \
                binding runtime will update the UI in the next animation frame causing a \
                delayed update and potential jumps if the item resizes.
                """,
        category = Category.CORRECTNESS,
        priority = 8,
        androidSpecific = true,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val CLEAR_ALL_DATA =
      Issue.create(
        id = "NotifyDataSetChanged",
        briefDescription = "Invalidating All RecyclerView Data",
        explanation =
          """
                The `RecyclerView` adapter's `onNotifyDataSetChanged` method does not specify what \
                about the data set has changed, forcing any observers to assume that all existing \
                items and structure may no longer be valid. `LayoutManager`s will be forced to \
                fully rebind and relayout all visible views.
                """,
        category = Category.PERFORMANCE,
        priority = 8,
        androidSpecific = true,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    private const val ON_BIND_VIEW_HOLDER = "onBindViewHolder"
    private const val NOTIFY_DATA_SET_CHANGED = "notifyDataSetChanged"
    private const val SUPERCLASS_SUPPORTLIB = "android.support.v7.widget.RecyclerView.Adapter"
    private const val SUPERCLASS_ANDROIDX = "androidx.recyclerview.widget.RecyclerView.Adapter"

    private fun getDataBinderReference(element: UElement?): PsiField? {
      if (element is UReferenceExpression) {
        val resolved = element.resolve()
        if (resolved is PsiField) {
          if ("dataBinder" == resolved.name) {
            return resolved
          }
        }
      }

      return null
    }
  }
}
