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

import com.android.tools.lint.checks.CheckResultDetector.Companion.isExpressionValueUnused
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.getUMethod
import com.intellij.psi.CommonClassNames.JAVA_LANG_BOOLEAN
import com.intellij.psi.CommonClassNames.JAVA_LANG_BYTE
import com.intellij.psi.CommonClassNames.JAVA_LANG_CHARACTER
import com.intellij.psi.CommonClassNames.JAVA_LANG_CHAR_SEQUENCE
import com.intellij.psi.CommonClassNames.JAVA_LANG_DOUBLE
import com.intellij.psi.CommonClassNames.JAVA_LANG_FLOAT
import com.intellij.psi.CommonClassNames.JAVA_LANG_INTEGER
import com.intellij.psi.CommonClassNames.JAVA_LANG_LONG
import com.intellij.psi.CommonClassNames.JAVA_LANG_NUMBER
import com.intellij.psi.CommonClassNames.JAVA_LANG_SHORT
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassInitializer
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.asJava.classes.KtLightClass
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.kotlin.asJava.elements.KtLightMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UInstanceExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UPrefixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPostfixOperator
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.tryResolve

class NoOpDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private val IMPLEMENTATION = Implementation(NoOpDetector::class.java, Scope.JAVA_FILE_SCOPE)

    val ASSUME_PURE_GETTERS =
      BooleanOption(
        "pure-getters",
        "Whether to assume methods with getter-names have no side effects",
        false,
        """
                Getter methods (where names start with `get` or `is`, and have non-void \
                return types, and no arguments) should not have side effects. With this \
                option turned on, lint will assume that is the case and will list any \
                getter calls whose results are ignored as suspicious code.
                """,
      )

    @JvmField
    val ISSUE =
      Issue.create(
          id = "NoOp",
          briefDescription = "NoOp Code",
          explanation =
            """
                This check looks for code which looks like it's a no-op -- usually \
                leftover expressions from interactive debugging, but in some cases \
                bugs where you had intended to do something with the expression such \
                as assign it to a field.
                """,
          category = CORRECTNESS,
          severity = Severity.WARNING,
          implementation = IMPLEMENTATION,
          enabledByDefault = false,
        )
        .setAliases(listOf("ResultOfMethodCallIgnored"))
        .setOptions(listOf(ASSUME_PURE_GETTERS))

    /** Maximum number of indirect calls it will search when looking for side effects */
    private val MAX_CALL_DEPTH = if (LintClient.isStudio) 1 else 2
    /** Maximum depth of the AST tree it will search when looking for side effects */
    private val MAX_RECURSION_DEPTH = if (LintClient.isStudio) 5 else 10
    /**
     * When analyzing a block such as a method, the maximum number of statements it will consider
     * when looking for side effects
     */
    private val MAX_STATEMENT_COUNT = if (LintClient.isStudio) 8 else 20

    private fun hasSideEffect(node: UExpression?, depth: Int = 0, callDepth: Int = 0): Boolean {
      node ?: return false
      if (depth == MAX_RECURSION_DEPTH) {
        return true // assume yes since we don't dare to search any deeper
      }
      when (node) {
        is UUnaryExpression -> {
          val operator = node.operator
          if (
            operator == UastPrefixOperator.INC ||
              operator == UastPrefixOperator.DEC ||
              operator == UastPostfixOperator.INC ||
              operator == UastPostfixOperator.DEC
          ) {
            if ((callDepth > 0 && isLocal(node.operand))) {
              // If we're inside a called method, and we're just manipulating local variables,
              // don't flag that as a side effect
              return false
            }
            return true
          }
          return hasSideEffect(node.operand, depth + 1, callDepth)
        }
        is UPolyadicExpression -> {
          if (
            node is UBinaryExpression &&
              node.operator is UastBinaryOperator.AssignOperator &&
              (callDepth == 0 || !isLocal(node.leftOperand))
          ) {
            return true
          }
          for (operand in node.operands) {
            if (hasSideEffect(operand, depth + 1, callDepth)) {
              return true
            }
          }
        }
        is UQualifiedReferenceExpression -> {
          return hasSideEffect(node.selector, depth + 1, callDepth)
        }
        is UParenthesizedExpression -> {
          return hasSideEffect(node.expression, depth + 1, callDepth)
        }
        is UCallExpression -> {
          if (callDepth >= MAX_CALL_DEPTH) {
            return true // assume true, since we don't dare to look any deeper
          }
          val called = node.resolve() ?: return true
          if (called is PsiCompiledElement) {
            if (isGetterName(called.name) || called.name == "toString") {
              return false
            }
            return true
          } else {
            val body = called.getUMethod()?.uastBody ?: return true
            if (hasSideEffect(body, depth + 1, callDepth + 1)) {
              return true
            }
          }
        }
        is UReturnExpression -> {
          // returning in the current method is a side effect
          return callDepth == 0 || hasSideEffect(node.returnExpression, depth + 1, callDepth)
        }
        is UIfExpression -> {
          if (
            hasSideEffect(node.condition, depth + 1, callDepth) ||
              hasSideEffect(node.thenExpression, depth + 1, callDepth) ||
              hasSideEffect(node.elseExpression, depth + 1, callDepth)
          ) {
            return true
          }
        }
        is UTryExpression -> {
          if (
            hasSideEffect(node.tryClause, depth + 1, callDepth) ||
              hasSideEffect(node.finallyClause, depth + 1, callDepth)
          ) {
            return true
          }
        }
        is UBlockExpression -> {
          val expressions: List<UExpression> = node.expressions
          if (expressions.size >= MAX_STATEMENT_COUNT) {
            return true
          }
          for (expression in expressions) {
            if (hasSideEffect(expression, depth + 1, callDepth)) {
              return true
            }
          }
        }
        is ULoopExpression -> {
          return hasSideEffect(node.body, depth + 1, callDepth)
        }
        is UThisExpression,
        is USuperExpression -> return true
        is UThrowExpression -> return true
      }

      return false
    }

    /**
     * Is the given [name] a likely getter name, such as `getFoo` or `isBar` ?
     *
     * We don't consider "get" by itself to be a getter name; it needs to be a prefix for a named
     * property.
     */
    private fun isGetterName(name: String): Boolean {
      val length = name.length
      if (name.startsWith("is") && length > 2 && name[2].isUpperCase()) {
        return true
      }

      if (name.startsWith("get") && length > 3 && name[3].isUpperCase()) {
        if (name.startsWith("getAnd") && length > 6 && name[6].isUpperCase()) {
          // E.g. AtomicInteger.getAndIncrement
          return false
        }
        return true
      }

      return false
    }

    private fun isLocal(lhs: UExpression): Boolean {
      val resolved = lhs.tryResolve()
      return resolved is PsiLocalVariable || resolved is PsiParameter
    }

    private val KOTLIN_PRIMITIVES = StandardClassIds.primitiveTypes.map { it.asFqNameString() }
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(
      USimpleNameReferenceExpression::class.java,
      UQualifiedReferenceExpression::class.java,
      UBinaryExpression::class.java,
      ULiteralExpression::class.java,
      UPolyadicExpression::class.java,
    )
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    if (context.isTestSource) {
      // Tests are full of intentional no ops, often within assertThrows etc.
      // TestRules often look like getters but have side effects.
      // The value of removing no-ops from tests isn't very high.
      return null
    }
    return object : UElementHandler() {
      override fun visitSimpleNameReferenceExpression(node: USimpleNameReferenceExpression) {
        val parent = skipParenthesizedExprUp(node.uastParent)
        if (parent is UBlockExpression) {
          val resolved = node.resolve() ?: return
          if (resolved is KtLightMember<*>) {
            val property = resolved.getProperty()
            if (property == null || property.hasSideEffect()) {
              return
            }
          } else if (resolved is PsiMethod && resolved.getParameter() == null) {
            // Skip methods, unless they're simple parameter getters
            return
          } else if (resolved is KtLightClass) {
            val ktClass = resolved.unwrapped
            if (ktClass is KtObjectDeclaration) {
              // Accessing object for initialization
              return
            }
          }

          if (
            isExpressionValueUnused(node) && !expectsSideEffect(context, node) && !isUnit(resolved)
          ) {
            report(context, node, node.identifier)
          }
        }
      }

      override fun visitQualifiedReferenceExpression(node: UQualifiedReferenceExpression) {
        val selector = node.selector
        if (selector is UCallExpression) {
          if (node.receiver is UInstanceExpression) {
            return
          }
          checkCall(selector, node)
        } else if (selector is USimpleNameReferenceExpression) {
          val resolved = selector.resolve() ?: return

          if (isComplexPropertyMethod(resolved)) {
            return
          }

          if (resolved is PsiMethod) {
            checkCall(
              null,
              node,
              selector.identifier,
              resolved,
              resolved.containingClass?.qualifiedName,
            )
          } else {
            if (isExpressionValueUnused(selector) && !expectsSideEffect(context, selector)) {
              report(context, selector, selector.identifier)
            }
          }
        }
      }

      override fun visitLiteralExpression(node: ULiteralExpression) {
        if (isExpressionValueUnused(node) && !expectsSideEffect(context, node)) {
          report(
            context,
            node,
            node.sourcePsi?.text ?: node.value?.toString() ?: node.asSourceString(),
          )
        }
      }

      override fun visitPolyadicExpression(node: UPolyadicExpression) {
        // Even simple "test" string literal is mapped to a polyadic expression
        // after KTIJ-27448 (ui injection host)
        val operand = node.operands.singleOrNull() as? ULiteralExpression ?: return
        // Make sure we check if the overall polyadic expression is used and expects side effect
        if (isExpressionValueUnused(node) && !expectsSideEffect(context, node)) {
          // But report on that literal expression
          report(context, operand, operand.sourcePsi?.text ?: operand.asSourceString())
        }
      }

      private fun checkCall(call: UCallExpression, node: UQualifiedReferenceExpression) {
        val method = call.resolve()
        method?.name?.let { methodName ->
          checkCall(
            call,
            node,
            call.methodIdentifier?.name ?: methodName,
            method,
            method.containingClass?.qualifiedName,
          )
        }
        if (method == null) {
          // Unresolved by UAST, but perhaps due to the lack of way to represent the resolution
          // result in [PsiMethod] form.
          val sourcePsi = call.sourcePsi as? KtElement ?: return
          analyze(sourcePsi) {
            val symbol = getFunctionLikeSymbol(sourcePsi) ?: return
            val callName = symbol.callableId?.callableName?.asString() ?: return
            checkCall(call, node, callName, null, symbol.callableId?.classId?.asFqNameString())
          }
        }
      }

      private fun checkCall(
        call: UCallExpression?,
        node: UQualifiedReferenceExpression,
        callName: String,
        method: PsiMethod?,
        containingClassFqName: String?,
      ) {
        // "Pure" methods are methods without side effects
        if (
          !isPureMethod(
            context,
            call ?: node,
            method,
            method?.name ?: callName,
            containingClassFqName,
          )
        ) {
          return
        }

        if (!isExpressionValueUnused(node)) {
          return
        }

        if (isAnnotatedCheckResult(context, method)) {
          // If methods are annotated with @CheckResult/@CheckReturnValue/etc. they'll
          // be flagged from the separate CheckResultDetector (and with the separate
          // issue ID CheckResult)
          return
        }

        if (expectsSideEffect(context, node)) {
          // We may be relying on a side effect of this method; this is typically
          // what you see in unit tests where you put the call inside a try/catch
          // followed by an immediate fail call.
          return
        }

        report(context, call ?: node.selector, callName)
      }

      override fun visitBinaryExpression(node: UBinaryExpression) {
        if (node.operator is UastBinaryOperator.AssignOperator) {
          return
        }
        if (node.resolveOperator() != null) {
          // Custom operator
          return
        }
        if (
          isExpressionValueUnused(node) &&
            !expectsSideEffect(context, node) &&
            !hasSideEffect(node.leftOperand) &&
            !hasSideEffect(node.rightOperand)
        ) {
          report(context, node, node.sourcePsi?.text ?: node.asSourceString())
        }
      }
    }
  }

  private fun PsiElement?.getProperty(): KtProperty? {
    return (this as? KtLightMember<*>)?.unwrapped as? KtProperty
  }

  private fun PsiElement?.getParameter(): KtParameter? {
    return (this as? KtLightMethod)?.unwrapped as? KtParameter
  }

  private fun KtProperty.hasSideEffect(): Boolean {
    return getter != null || hasDelegate() || hasDelegateExpression()
  }

  private fun isComplexPropertyMethod(resolved: PsiElement?): Boolean {
    val property = resolved.getProperty() ?: return false
    return property.hasSideEffect()
  }

  private fun isUnit(resolved: PsiElement?): Boolean {
    return (resolved as? PsiClass)?.qualifiedName == "kotlin.Unit"
  }

  private fun isPureMethod(
    context: JavaContext,
    node: UExpression,
    method: PsiMethod?,
    methodName: String,
    containingClassFqName: String?,
  ): Boolean {
    when (methodName) {
      // Integer.valueOf, etc
      "valueOf",
      "equals" -> return method?.parameterList?.parametersCount == 1
      "toString",
      "hashCode",
      "clone",
      "getText" -> return method?.parameterList?.parametersCount == 0

      // Methods which often have side effects (such as triggering initialization) despite name
      "getClass", // often used in proto files to force class loading
      "getPreferredSize",
      "getInstance",
      "getResourceResolver",
      "getAvailableFontFamilyNames" -> return false
      "getComponent" ->
        if (method.isIn("com.intellij.execution.ui.RunnerLayoutUi", context)) {
          return false
        }
      "isActive" ->
        if (method.isIn("android.view.inputmethod.InputMethodManager", context)) {
          return false
        }
      "getDecorView" ->
        if (method.isIn("android.view.Window", context)) {
          return false
        }
      "getValue" ->
        if (
          method.isIn("androidx.compose.runtime.State", context) ||
            method.isIn("androidx.compose.runtime.MutableState", context)
        ) {
          return false
        }
      // getCount() on a Cursor is often used to force the cursor to execute the query
      "getCount" ->
        if (method.isIn("android.database.Cursor", context)) {
          return false
        }
    }

    if (methodName.startsWith("parse")) {
      // a parse call (e.g. Integer.parseUnsignedInt, Double.parseDouble, etc) inside a try/catch;
      // we may be just looking for the exception side effect.
      node.getParentOfType<UTryExpression>(true)?.let {
        if (it.catchClauses.isNotEmpty()) {
          return false
        }
      }
    }

    when (containingClassFqName) {
      JAVA_LANG_STRING,
      JAVA_LANG_CHAR_SEQUENCE,
      "kotlin.text.StringsKt__StringsKt",
      "kotlin.text.StringsKt" -> {
        // Strings are immutable and all the methods are pure, except for a couple
        // that copy into a destination array (getChars, getBytes)
        if (
          (methodName == "getChars" || methodName == "getBytes") &&
            method != null &&
            method.parameterList.parameters.any { it.type is PsiArrayType }
        ) {
          return false
        }

        if (method != null && method.throwsList.referenceElements.isNotEmpty()) {
          // e.g. throws UnsupportedEncodingException, might be a deliberate side effect
          return false
        }

        return true
      }
      JAVA_LANG_BOOLEAN,
      JAVA_LANG_LONG,
      JAVA_LANG_INTEGER,
      JAVA_LANG_SHORT,
      JAVA_LANG_BYTE,
      JAVA_LANG_FLOAT,
      JAVA_LANG_DOUBLE,
      JAVA_LANG_NUMBER -> return true
      JAVA_LANG_CHARACTER -> {
        // All methods are immutable except for one which copies into an array
        return (methodName != "toChars" ||
          method != null && method.parameterList.parameters.none { it.type is PsiArrayType })
      }
      // Use CommonClassNames.JAVA_NET_URI and JAVA_NET_URL once we update to latest prebuilts
      "java.net.URI" -> return true
      "java.net.URL" -> {
        // getContent despite name is shorthand for openConnection().getContent()
        return methodName != "getContent" &&
          !methodName.startsWith("set") &&
          !methodName.startsWith("open")
      }
      "android.database.sqlite.SQLiteOpenHelper",
      "androidx.sqlite.db.SupportSQLiteOpenHelper" ->
        return false // the "getters" like getWritableDatabase etc have side effects
      in KOTLIN_PRIMITIVES -> return true
    }

    if (
      isGetterName(methodName) &&
        isGetter(method) &&
        ASSUME_PURE_GETTERS.getValue(context.configuration)
    ) {
      if (context.evaluator.isStatic(method)) {
        // Calls to static methods called get tend to be used for intentional
        // initialization -- for example, getInstance() etc.
        return false
      }

      // Is the given [method] a method from [java.nio.Buffer] ? These use
      // getter-naming but have side effects.
      if (isBufferMethod(context, method)) {
        return false
      }

      return true
    }

    return false
  }

  private fun PsiMethod?.isIn(containingClass: String, context: JavaContext): Boolean {
    return context.evaluator.isMemberInClass(this, containingClass)
  }

  override fun applicableAnnotations(): List<String> = listOf("org.jetbrains.annotations.Contract")

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type == AnnotationUsageType.METHOD_CALL
  }

  override fun inheritAnnotation(annotation: String): Boolean = true

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    // TODO: Look for @Immutable too?
    val pure = UastLintUtils.getAnnotationBooleanValue(annotationInfo.annotation, "pure", false)
    if (!pure) {
      return
    }
    if (isExpressionValueUnused(element)) {
      report(context, element, (usageInfo.referenced as? PsiNamedElement)?.name ?: "?")
    }
  }

  private fun isAnnotatedCheckResult(context: JavaContext, method: PsiMethod?): Boolean {
    return context.evaluator.getAnnotations(method).any {
      val name = it.qualifiedName ?: ""
      // See CheckResultDetector#applicableAnnotations
      name.endsWith("CheckResult") || name.endsWith("CheckReturnValue")
    }
  }

  fun expectsSideEffect(context: JavaContext, element: UElement): Boolean {
    if (CheckResultDetector.expectsSideEffect(context, element)) {
      return true
    }

    // Also allow accessing fields, for class initialization
    val containingMethod = element.getParentOfType(UMethod::class.java)
    if (containingMethod != null) {
      if (containingMethod.isConstructor && containingMethod.sourcePsi is KtClass) {
        // Kotlin constructor initializer (static or instance)
        return true
      }

      if (
        containingMethod.uAnnotations.any {
          it.qualifiedName == "androidx.compose.runtime.Composable"
        }
      ) {
        // In composable functions special rules apply
        return true
      }
    } else if (element.sourcePsi?.getParentOfType<PsiClassInitializer>(true) != null) {
      // Java static initializer
      return true
    }

    return false
  }

  private fun isGetter(psiMethod: PsiMethod?): Boolean {
    if (psiMethod == null) return false
    if (
      psiMethod.isConstructor ||
        psiMethod.hasParameters() ||
        psiMethod.returnType == PsiTypes.voidType()
    ) {
      return false
    }
    if (psiMethod.throwsList.referenceElements.isNotEmpty()) {
      return false
    }
    val body = UastFacade.getMethodBody(psiMethod)

    if (body != null) {
      return isGetterBody(body)
    }

    val property = psiMethod.getProperty()
    if (property != null) {
      return !property.hasSideEffect()
    }

    val parameter = psiMethod.getParameter()
    if (parameter != null) {
      return true
    }

    return psiMethod is PsiCompiledElement
  }

  private fun isGetterBody(node: UExpression?): Boolean {
    node ?: return true

    when (node) {
      is UReferenceExpression,
      is ULiteralExpression -> {
        return true
      }
      is UBinaryExpression -> {
        return node.operator !is UastBinaryOperator.AssignOperator
      }
      is UPrefixExpression -> {
        return node.operator != UastPrefixOperator.DEC && node.operator != UastPrefixOperator.INC
      }
      is UBlockExpression -> {
        val expressions = node.expressions
        if (expressions.size == 1) {
          val expression = expressions[0]
          if (expression is UReturnExpression) {
            return isGetterBody(expression.returnExpression)
          }
        }
      }
    }

    return false
  }

  /**
   * Is the given [method] a method from [java.nio.Buffer] ? These use getter-naming but have side
   * effects.
   */
  private fun isBufferMethod(context: JavaContext, method: PsiMethod?) =
    method != null && context.evaluator.isMemberInSubClassOf(method, "java.nio.Buffer")

  private fun report(context: JavaContext, expression: UElement, name: String) {
    val message =
      "This ${if (expression is UCallExpression) "call result" else "reference"} is unused: $name"
    context.report(ISSUE, expression, context.getLocation(expression), message)
  }
}
