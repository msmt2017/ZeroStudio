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

import com.android.SdkConstants.CLASS_VIEW
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isJava
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import java.util.Locale
import java.util.regex.Pattern
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.singleFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULocalVariableEx
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParameterForArgument
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isAssignment
import org.jetbrains.uast.util.isConstructorCall
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Looks for bugs around implicit SAM conversions. */
class SamDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    /** Improperly handling implicit SAM instances. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "ImplicitSamInstance",
        briefDescription = "Implicit SAM Instances",
        explanation =
          """
          Kotlin's support for SAM (single abstract method) interfaces lets you pass \
          a lambda to the interface. This will create a new instance on the fly even \
          though there is no explicit constructor call. If you pass one of these \
          lambdas or method references into a method which (for example) stores or \
          compares the object identity, unexpected results may happen.

          In particular, passing a lambda variable in as a listener, and then later \
          attempting to remove the listener will not work because a different \
          instance is passed in.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = null,
        implementation = Implementation(SamDetector::class.java, Scope.JAVA_FILE_SCOPE),
        moreInfo = "https://kotlinlang.org/docs/fun-interfaces.html#sam-conversions",
      )

    private const val HANDLER_CLASS = "android.os.Handler"
    private const val DRAWABLE_CALLBACK_CLASS = "android.graphics.drawable.Drawable.Callback"
    private const val RUNNABLE_CLASS = "java.lang.Runnable"
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(
      ULambdaExpression::class.java,
      UCallableReferenceExpression::class.java,
      UCallExpression::class.java,
    )

  private fun checkRemoveMethod(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val arguments = node.valueArguments
    for (argument in arguments) {
      if (argument is UCallableReferenceExpression) {
        // Already handled in the UElement handler below
        continue
      }

      if (argument.getExpressionType().hasLambdaType()) {
        if (checkRemoveLambda(context, node, method, argument)) {
          return
        }
      } else if (arguments.size == 1) {
        val selector = argument.findSelector()
        if (selector is UCallExpression && selector.isConstructorCall()) {
          val parameter = node.getParameterForArgument(argument) ?: return
          if (!isInstanceRemoval(method, parameter)) {
            return
          }
          val methodName = method.name
          val location = context.getLocation(selector)
          context.report(
            ISSUE,
            selector,
            location,
            "This argument is a new instance so `$methodName` will not remove anything",
          )
        }
      }
    }
  }

  private fun PsiType?.hasLambdaType() =
    this is PsiClassType && canonicalText.startsWith("kotlin.jvm.functions.Function")

  private fun checkRemoveLambda(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    argument: UExpression,
  ): Boolean {
    if (!isSamConversion(node, argument)) {
      return false
    }

    val parameter = node.getParameterForArgument(argument) ?: return false
    if (!isInstanceRemoval(method, parameter)) {
      return false
    }

    val location = context.getLocation(argument)
    val variable = argument.tryResolve()

    val container = node.getParentOfType<UClass>()
    val qualifiedName = method.containingClass?.qualifiedName
    val isHandler = (qualifiedName == HANDLER_CLASS || qualifiedName == CLASS_VIEW)
    if (variable != null && container?.sourcePsi != null) {
      val posts =
        if (isHandler) findPostCalls(container, qualifiedName!!, variable) else emptyList()
      var last = location
      for (post in posts) {
        val secondary = context.getLocation(post)
        last.withSecondary(
          secondary,
          "Different instance than the one for `${method.name}()` due to SAM conversion; wrap with a shared `Runnable`",
        )
        last = secondary
      }
    }

    val argumentName = argument.sourcePsi?.text
    val samType = parameter.type
    val samTypeString = samType.presentableText
    val samTypeVar =
      samTypeString.substringBefore("<").replaceFirstChar { it.lowercase(Locale.ROOT) }

    var fix: LintFix? = null
    if (variable is PsiVariable && variable.containingFile === node.sourcePsi?.containingFile) {
      val declaration = variable.toUElement() as? UVariable
      fix = createLambdaVariableFix(context, declaration, samTypeString)
    }

    val addVerb = if (isHandler) "post" else "add"
    val example =
      if (argument is ULambdaExpression) "val $samTypeVar = $samTypeString $argumentName"
      else "val $samTypeVar = $samTypeString { $argumentName() }"
    val message =
      "`$argumentName` is an implicit SAM conversion, so the instance you are removing here will not match anything. " +
        "To fix this, use for example `$example` and $addVerb and " +
        "remove the `$samTypeVar` val instead."
    context.report(ISSUE, argument, location, message, fix)
    return true
  }

  private fun isSamConversion(call: UCallExpression, argument: UExpression): Boolean {
    val sourcePsi = call.sourcePsi
    if (sourcePsi !is KtCallExpression) {
      return false
    }
    return isSamConversion(sourcePsi, argument)
  }

  private fun isSamConversion(callExpression: KtCallExpression, argument: UExpression): Boolean {
    return analyze(callExpression) { isSamConversion(callExpression, argument) }
  }

  private fun KaSession.isSamConversion(
    callExpression: KtCallExpression,
    argument: UExpression,
  ): Boolean {
    val type = getParameterType(callExpression, argument) ?: return false
    return type.isFunctionalInterface
  }

  private fun KaSession.getParameterType(
    callExpression: KtCallExpression,
    argument: UExpression,
  ): KaType? {
    val callInfo = callExpression.resolveToCall()?.singleFunctionCallOrNull() ?: return null
    val mapping = callInfo.argumentMapping
    val parameterSignature =
      mapping[argument.sourcePsi]
        ?: mapping[argument.skipParenthesizedExprDown().sourcePsi]
        ?: return null
    return parameterSignature.returnType
  }

  /**
   * Returns true if this string starts with the given [prefix] as a word (meaning that the next
   * character is upper case), so `hasWordPrefix("remove")` would return true for `remove` and
   * `removeListener` but not `removedNotify`.
   */
  private fun String.hasWordPrefix(prefix: String): Boolean {
    if (startsWith(prefix)) {
      val length = prefix.length
      return this.length == length || this[length].isUpperCase()
    }
    return false
  }

  /** Does this string represent a likely lambda-removal method name? */
  private fun String?.isRemoveMethodName(): Boolean {
    this ?: return false
    return hasWordPrefix("remove") || hasWordPrefix("unregister") || hasWordPrefix("stop")
  }

  /**
   * Given a removal method name, returns the expected name of the corresponding addition method.
   */
  private fun getPairedName(methodName: String): String {
    val pairedName =
      when (methodName) {
        "remove",
        "removeAll" -> "add"
        "removeCallbacks" -> "postDelayed"
        "unregister" -> "register"
        else -> {
          if (methodName.startsWith("remove")) "add" + methodName.removePrefix("remove")
          else if (methodName.startsWith("unregister")) methodName.substring(2)
          else if (methodName.startsWith("stop")) "start" + methodName.removePrefix("stop") else ""
        }
      }
    return pairedName
  }

  /**
   * Returns true if the given [method] looks like it's removing the passed in parameter. It does
   * this by looking to see if there is a corresponding "add" method with the same type as one of
   * the parameters.
   */
  private fun isInstanceRemoval(method: PsiMethod, parameter: PsiParameter): Boolean {
    val methodName = method.name
    val pairedName = getPairedName(methodName)

    // Make sure this method is really something which adds and removes instances.
    // That means we want to make sure there is a paired "add" method on the API
    // that also takes the same SAM interface; otherwise, we may be looking at
    // something which for example takes a filter (such as removeIf(Predicate));
    // here it would be fine to pass in a new instance.
    if (methodName.endsWith("Listener")) {
      // Common pattern, very likely to be instance removal
      return true
    }
    val methods = method.containingClass?.findMethodsByName(pairedName, false)
    if (methods.isNullOrEmpty()) {
      return false
    }
    val samType = parameter.type
    if (samType !is PsiClassType) {
      return false
    }
    val samTypeErasure = samType
    return methods.any {
      method.parameterList.parameters.any { parameter ->
        methodParameterMatches(samTypeErasure, parameter)
      }
    }
  }

  private fun methodParameterMatches(samType: PsiType, parameter: PsiParameter): Boolean {
    val parameterType = parameter.type
    return parameterType == samType
  }

  private fun createLambdaVariableFix(
    context: JavaContext,
    declaration: UVariable?,
    samType: String,
  ): LintFix? {
    if (declaration != null) {
      val name = declaration.name
      val initializer = declaration.uastInitializer
      if (initializer is ULambdaExpression) {
        return fix()
          .name("Explicitly create $samType instance")
          .replace()
          .pattern("""$name(.*)\{""", Pattern.DOTALL)
          .range(context.getLocation(declaration as UElement))
          .with(" = $samType ")
          .reformat(true)
          .build()
      }
    }
    return null
  }

  /** Locates any post calls in the same method or class as the corresponding remove call. */
  private fun findPostCalls(
    container: UClass,
    className: String,
    variable: PsiElement,
  ): List<UExpression> {
    val matches = mutableListOf<UExpression>()
    container.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          val resolved = node.resolve() ?: return super.visitCallExpression(node)
          if (
            resolved.name.startsWith("post") && resolved.containingClass?.qualifiedName == className
          ) {
            val posted = node.valueArguments.firstOrNull()?.skipParenthesizedExprDown()
            val postedVariable = posted?.tryResolve()
            @Suppress("LintImplPsiEquals")
            if (postedVariable == variable) {
              matches.add(posted)
            }
          }
          return super.visitCallExpression(node)
        }
      }
    )

    return matches
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    val psi = context.uastFile?.sourcePsi ?: return null
    if (isJava(psi.language)) {
      return null
    }
    return object : UElementHandler() {
      override fun visitLambdaExpression(node: ULambdaExpression) {
        val parent = node.uastParent ?: return
        if (parent is ULocalVariable) {
          val psiVar =
            parent.sourcePsi as? PsiLocalVariable
              ?: (parent as? ULocalVariableEx)?.javaPsi
              ?: return
          checkCalls(context, node, psiVar)
        } else if (parent.isAssignment()) {
          val v = (parent as UBinaryExpression).leftOperand.tryResolve() ?: return
          val psiVar = v as? PsiLocalVariable ?: return
          checkCalls(context, node, psiVar)
        }
      }

      override fun visitCallableReferenceExpression(node: UCallableReferenceExpression) {
        val call = node.uastParent as? UCallExpression ?: return
        checkLambda(context, node, call, node)
      }

      override fun visitCallExpression(node: UCallExpression) {
        val name = node.methodIdentifier?.name
        if (name.isRemoveMethodName()) {
          val resolved = node.resolve()
          if (resolved != null) {
            checkRemoveMethod(context, node, resolved)
          }
        }
      }
    }
  }

  private fun checkCalls(
    context: JavaContext,
    lambda: ULambdaExpression,
    variable: PsiLocalVariable,
  ) {
    val method = lambda.getContainingUMethod() ?: return
    method.accept(
      object : AbstractUastVisitor() {
        override fun visitCallExpression(node: UCallExpression): Boolean {
          for (argument in node.valueArguments) {
            if (argument is UReferenceExpression && argument.resolve() == variable) {
              checkLambda(context, lambda, node, argument)
            }
          }
          return super.visitCallExpression(node)
        }
      }
    )
  }

  private fun checkLambda(
    context: JavaContext,
    lambda: UExpression,
    call: UCallExpression,
    argument: UReferenceExpression,
  ) {
    val psiMethod = call.resolve() ?: return
    val evaluator = context.evaluator
    if (psiMethod is PsiCompiledElement) {
      // The various Runnable methods in Handler operate on Runnable instances
      // that are stored. Ditto for View and Drawable.Callback.
      // (storing is fine, it's deleting that doesn't work!)
      val containingClass = psiMethod.containingClass
      if (
        psiMethod.name.isRemoveMethodName() &&
          (evaluator.isMemberInClass(psiMethod, HANDLER_CLASS) ||
            evaluator.inheritsFrom(containingClass, CLASS_VIEW, false) ||
            evaluator.inheritsFrom(containingClass, "android.view.ViewTreeObserver", false) ||
            evaluator.inheritsFrom(containingClass, DRAWABLE_CALLBACK_CLASS, false))
      ) {
        // idea: only store if temporarily in a variable
        val psiParameter = call.getParameterForArgument(lambda) ?: return
        val typeString = psiParameter.type.canonicalText
        if (typeString == RUNNABLE_CLASS) {
          reportError(context, lambda, typeString, argument)
        }
      }
      return
    }

    if (!isJava(psiMethod.language) || !isSamConversion(call, argument)) {
      return
    }

    val psiParameter = call.getParameterForArgument(argument) ?: return
    val method = psiMethod.toUElement(UMethod::class.java) ?: return
    if (
      instanceComparesLambda(method, psiParameter) &&
        !context.driver.isSuppressed(context, ISSUE, method as UElement)
    ) {
      val typeString = psiParameter.type.canonicalText
      reportError(context, lambda, typeString, argument)
    }
  }

  private fun reportError(
    context: JavaContext,
    lambda: UExpression,
    type: String,
    argument: UReferenceExpression,
  ) {
    val location = context.getLocation(argument)
    val simpleType = type.substringAfterLast('.').substringBefore("<")
    val range = context.getLocation(lambda)
    val parentVar = argument.getParentOfType<UVariable>()
    val fix =
      if (lambda is ULambdaExpression) {
        if (parentVar != null && parentVar.uastInitializer?.skipParenthesizedExprDown() == lambda) {
          createLambdaVariableFix(context, parentVar, simpleType)
        } else {
          fix()
            .name("Explicitly create $simpleType instance")
            .replace()
            .beginning()
            .with("$simpleType ")
            .range(range)
            .build()
        }
      } else {
        null
      }
    context.report(
      ISSUE,
      argument,
      location,
      "Implicit new `$simpleType` instance being passed to method which ends up " +
        "checking instance equality; this can lead to subtle bugs",
      fix,
    )
  }

  /**
   * Returns true if it looks like this method is taking the given lambda [parameter] and either
   * doing an instance comparison on it or attempting to remove it from a collection (which would
   * also involve an instance comparison).
   */
  private fun instanceComparesLambda(method: UMethod, parameter: PsiParameter): Boolean {
    var storesLambda = false
    method.accept(
      object : AbstractUastVisitor() {
        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          val resolved = node.resolve()
          if (resolved == parameter) {
            val parent = node.uastParent
            if (parent is UCallExpression) {
              // Decide if we're calling some method which is attempting to remove the new instance
              val methodName = parent.methodName
              if (methodName.isRemoveMethodName()) {
                storesLambda = true
              }
            } else if (parent is UBinaryExpression) {
              val kind = parent.operator
              if (
                (kind == UastBinaryOperator.IDENTITY_EQUALS ||
                  kind == UastBinaryOperator.IDENTITY_NOT_EQUALS) &&
                  !parent.rightOperand.isNullLiteral()
              ) {
                storesLambda = true
              }
            }
            // One thing I can try is to let you ONLY invoke methods on these things,
            // to see what else I can surface
          }
          return super.visitSimpleNameReferenceExpression(node)
        }
      }
    )
    return storesLambda
  }
}
