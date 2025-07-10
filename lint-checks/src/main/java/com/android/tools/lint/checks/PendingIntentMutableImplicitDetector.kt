/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.SdkConstants.CLASS_CONTEXT
import com.android.SdkConstants.CLASS_INTENT
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_CLASS
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isScopingFunction
import com.android.tools.lint.detector.api.isScopingIt
import com.android.tools.lint.detector.api.isScopingThis
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiVariable
import com.intellij.util.containers.headTail
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolLocation
import org.jetbrains.kotlin.analysis.api.symbols.KaTypeParameterSymbol
import org.jetbrains.kotlin.analysis.api.symbols.typeParameters
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.uast.UArrayAccessExpression
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastBinaryOperator.Companion.ASSIGN
import org.jetbrains.uast.UastCallKind.Companion.CONSTRUCTOR_CALL
import org.jetbrains.uast.UastCallKind.Companion.METHOD_CALL
import org.jetbrains.uast.UastCallKind.Companion.NESTED_ARRAY_INITIALIZER
import org.jetbrains.uast.UastCallKind.Companion.NEW_ARRAY_WITH_INITIALIZER
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedChain
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.jetbrains.uast.visitor.UastVisitor

/**
 * Looks for creations of mutable PendingIntents with implicit Intents. This type of object will
 * throw an exception in apps targeting Android 14 and above.
 */
class PendingIntentMutableImplicitDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames() = PendingIntentUtils.GET_METHOD_NAMES

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, PendingIntentUtils.CLASS)) return
    val flagsArgument =
      node.getArgumentForParameter(PendingIntentUtils.GET_ARGUMENT_POSITION_FLAG) ?: return
    val flags = ConstantEvaluator.evaluate(context, flagsArgument) as? Int ?: return
    if (!isNewMutableNonExemptedPendingIntent(flags)) return
    val intentArgument =
      node.getArgumentForParameter(PendingIntentUtils.GET_ARGUMENT_POSITION_INTENT) ?: return
    if (
      getIntentTypeForExpression(
          intentArgument,
          IntentExpressionInfo(context.evaluator, node, intentArgument),
        )
        .isImplicit()
    ) {
      val fixes =
        fix()
          .alternatives(
            buildImmutableFixOrNull(context, flagsArgument),
            buildNoCreateFix(context, flagsArgument),
          )
      val incident =
        Incident(
          issue = ISSUE,
          scope = node,
          location = context.getLocation(node),
          message = "Mutable implicit `PendingIntent` will throw an exception",
          fixes,
        )
      context.report(incident, map())
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk < 23) return false
    if (context.mainProject.targetSdk < 34) {
      incident.overrideSeverity(Severity.WARNING)
      incident.message += " once this app starts targeting Android 14 or above"
    }
    incident.message +=
      ", follow either of these recommendations: for an existing `PendingIntent` use" +
        " `FLAG_NO_CREATE` and for a new `PendingIntent` either make it immutable or make the" +
        " `Intent` within explicit"
    return true
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "MutableImplicitPendingIntent",
        briefDescription = "Mutable Implicit PendingIntent is disallowed",
        explanation =
          """
          Apps targeting Android 14 and above are not allowed to create `PendingIntents` with \
          `FLAG_MUTABLE` and an implicit intent within for security reasons.

          To retrieve an existing PendingIntent, use `FLAG_NO_CREATE`. To create a new \
          `PendingIntent`, either make the intent explicit, or make it immutable with \
          `FLAG_IMMUTABLE`.
          """,
        category = Category.SECURITY,
        priority = 5,
        /**
         * The severity of this issue is reported conditionally. See the overridden `filterIncident`
         * method.
         * - targetSdk >= 34: ERROR
         * - 23 <= targetSdk < 34: WARNING
         * - targetSdk < 23: not reported
         */
        severity = Severity.ERROR,
        implementation =
          Implementation(PendingIntentMutableImplicitDetector::class.java, Scope.JAVA_FILE_SCOPE),
        androidSpecific = true,
      )

    private const val CLASS_URI = "android.net.Uri"
    private const val METHOD_ARRAY_OF = "arrayOf"
    private const val METHOD_ARRAY_OF_NULLS = "arrayOfNulls"
    private const val METHOD_INTENT_SET_COMPONENT = "setComponent"
    private const val METHOD_INTENT_SET_PACKAGE = "setPackage"
    private const val METHOD_INTENT_SET_CLASS = "setClass"
    private const val METHOD_INTENT_SET_CLASS_NAME = "setClassName"
    private const val METHOD_LIST_OF = "listOf"
    private const val TYPE_PARAM = "T"

    // Used for the vararg argumentTypes in JavaEvaluator#methodMatches
    private val INTENT_EXPLICIT_CONSTRUCTOR_ARGS =
      listOf(
        arrayOf(CLASS_CONTEXT, TYPE_CLASS),
        arrayOf(TYPE_STRING, CLASS_URI, CLASS_CONTEXT, TYPE_CLASS),
      )

    private data class IntentExpressionInfo(
      val javaEvaluator: JavaEvaluator,
      val call: UCallExpression,
      val intentArgument: UExpression,
    )

    /**
     * An intent is implicit if it didn't escape, and both its component and package are null, hence
     * this class carries that information for further analysis of intent's implicitness before its
     * use in the PendingIntent method.
     */
    private data class IntentType(
      var hasComponent: Boolean,
      var hasPackage: Boolean,
      var isEscaped: Boolean = false,
    ) {
      // If an intent is implicit, then it doesn't have a component and a package.
      constructor(isImplicit: Boolean) : this(hasComponent = !isImplicit, hasPackage = !isImplicit)

      fun isImplicit(): Boolean = !isEscaped && !hasComponent && !hasPackage
    }

    private fun isNewMutableNonExemptedPendingIntent(flags: Int): Boolean {
      val isFlagNoCreateSet = (flags and PendingIntentUtils.FLAG_NO_CREATE) != 0
      val isFlagMutableSet = (flags and PendingIntentUtils.FLAG_MUTABLE) != 0
      val isFlagAllowUnsafeImplicitIntentSet =
        (flags and PendingIntentUtils.FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT) != 0
      return isFlagMutableSet && !isFlagNoCreateSet && !isFlagAllowUnsafeImplicitIntentSet
    }

    /**
     * Retrieves UExpression's intent type by pattern matching the type of the passed UExpression
     * and calls the corresponding getIntentTypeFor_EXPRESSION_TYPE method
     */
    private fun getIntentTypeForExpression(
      intentExp: UExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType =
      when (val intent = intentExp.skipParenthesizedExprDown()) {
        is UCallExpression -> getIntentTypeForCall(intent, intentInfo)
        is UQualifiedReferenceExpression -> getIntentTypeForQualified(intent, intentInfo)
        is USimpleNameReferenceExpression -> getIntentTypeForSimpleName(intent, intentInfo)
        else -> IntentType(isImplicit = false)
      }

    /**
     * Retrieves UCallExpression's intent type by pattern matching its UastCallKind as follows:
     * - Constructor: if an intent constructor matches any of the explicit intent constructors
     *   defined by [INTENT_EXPLICIT_CONSTRUCTOR_ARGS], then it's explicit in its component,
     *   otherwise implicit overall.
     * - ArrayInitializers: if the array initializer has any implicit expressions then it's
     *   implicit, otherwise non-implicit overall, e.g.: `new Intent[] { new Intent(), new
     *   Intent(context, SomeClass.class) }` is implicit because the first element is implicit.
     */
    private fun getIntentTypeForCall(
      intent: UCallExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType {
      return when (intent.kind) {
        CONSTRUCTOR_CALL -> {
          if (
            INTENT_EXPLICIT_CONSTRUCTOR_ARGS.any { constructorArgs ->
              intentInfo.javaEvaluator.methodMatches(
                intent.resolve() ?: return IntentType(isImplicit = false),
                CLASS_INTENT,
                allowInherit = false,
                *constructorArgs,
              )
            }
          ) {
            IntentType(hasComponent = true, hasPackage = false)
          } else {
            IntentType(isImplicit = true)
          }
        }
        NESTED_ARRAY_INITIALIZER,
        NEW_ARRAY_WITH_INITIALIZER -> {
          IntentType(
            isImplicit =
              intent.valueArguments.any { getIntentTypeForExpression(it, intentInfo).isImplicit() }
          )
        }
        METHOD_CALL -> {
          if (isWithScopeCall(intent)) {
            getIntentTypeForWithScopeCall(intent, intentInfo)
          } else if (isKotlinCollection(intent)) {
            IntentType(
              isImplicit =
                intent.valueArguments.any {
                  getIntentTypeForExpression(it, intentInfo).isImplicit()
                }
            )
          } else {
            IntentType(isImplicit = false)
          }
        }
        else -> IntentType(isImplicit = false)
      }
    }

    /**
     * Retrieves UQualifiedReferenceExpression's intent type as follows:
     *
     * An UQualifiedReferenceExpression's intent type consists of the following:
     * 1. Retrieving intent properties from its receiver, i.e. the intent object.
     * 2. Combining them with actions from all its selectors, i.e. method calls, via
     *    [getIntentTypeAfterCall]. If any of the selectors is an escape call, the method returns an
     *    explicit intentType.
     *
     * See examples below:
     * - `Intent().setPackage("android.package")`
     *     - Here, the intent object `Intent()` is implicit, but the call
     *       `setPackage("android.package")` is an explicit set method, so overall the expression is
     *       not an implicit intent.
     *     - If we add `.setPackage(null)` to the above, then the overall expression will become
     *       implicit because both component and package are null.
     * - `intent.setAction("TEST");` where `intent` is implicit
     *     - Given that `intent` is implicit and `setAction("TEST")` is not an explicit method, the
     *       expression is an implicit intent.
     * - `intent.foo("TEST");` where `intent` is implicit
     *     - Given that `intent` is implicit and `foo("TEST")` is not an Intent method, i.e. an
     *       escape call (we don't know what `foo()` does), so the expression is not an implicit
     *       intent.
     */
    private fun getIntentTypeForQualified(
      intent: UQualifiedReferenceExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType {
      var qualifiedChain = intent.getQualifiedChain()
      if (qualifiedChain.isEmpty()) return IntentType(isImplicit = false)
      if (
        isKotlin(intent.lang) &&
          qualifiedChain.size >= 3 &&
          qualifiedChain[0].asSourceString() == "android" &&
          qualifiedChain[1].asSourceString() == "content"
      ) {
        // Ignore the first 2 expressions for the fully qualified case of the
        // android.content.Intent() constructor
        qualifiedChain = qualifiedChain.drop(2)
      }
      val (intentObject, calls) = qualifiedChain.headTail()
      var intentType = getIntentTypeForExpression(intentObject, intentInfo)
      for (c in calls) {
        // break early if the intent escaped
        if (intentType.isEscaped) break

        val call = c as? UCallExpression ?: continue
        if (isSelectorScopeCall(call)) {
          intentType =
            getIntentTypeForSelectorScopeCall(intent, intentObject, intentType, call, intentInfo)
        } else {
          setIntentTypeAfterCallInPlace(intentType, call, intentInfo)
        }
      }
      return intentType
    }

    /**
     * Retrieves USimpleNameReferenceExpression's (e.g. a variable) intent type as follows:
     *
     * An USimpleNameReferenceExpression is implicit if all the following is true:
     * 1. Its last assignment is within the method where the PendingIntent is created
     * 2. Its both component and package are null before the PendingIntent creation
     * 3. It did not escape by other methods
     * 4. If it's an array, then at least one of its elements is implicit
     *
     * Conditions 2, 3, and 4 are checked by an [IntentSimpleNameAnalyzer].
     *
     * Below we consider 4 example scenarios where the above conditions are violated. Assume this
     * class:
     * ```
     * public class TestClass {
     *   Intent mIntent = new Intent();
     *   public void foo(Intent intentArg) {
     *     Intent intentOne = new Intent(); // implicit
     *     Intent intentTwo = new Intent(context, SomeClass.class); // explicit
     *     Intent[] intentArray = new Intent[2];
     *     intentArray[0] = intentTwo;
     *     intentArray[1] = intentTwo;
     *   }
     * }
     * ```
     * 1. If we use `mIntent` or `intentArg` inside `foo()` then condition 1 for `mIntent` and
     *    `intentArg` is not satisfied because these variables are defined outside of `foo()`
     * 2. If we call `intentOne.setPackage("android.package");` before using it in the
     *    PendingIntent, then condition 2 for `intentOne` is not satisfied because
     *    `setPackage("android.package")` makes the intent explicit
     * 3. If we call `intentOne.foo()` then condition 3 is not satisfied because `foo()` is an
     *    escape method
     * 4. If we use `intentArray`, then condition 4 is not satisfied because all its elements are
     *    explicit
     */
    private fun getIntentTypeForSimpleName(
      intent: USimpleNameReferenceExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType {
      val (assign, assignParentUMethod) =
        findLastAssignmentAndItsParentUMethod(intent, intentInfo.call)
          ?: return IntentType(isImplicit = false)
      val intentParentUMethod =
        intent.getParentOfType(UMethod::class.java) ?: return IntentType(isImplicit = false)
      if (assignParentUMethod != intentParentUMethod) {
        return IntentType(isImplicit = false) // only consider local assigns
      }
      var intentArraySize = 0
      var assignIntentType = IntentType(isImplicit = false)
      if (assign is UCallExpression && isNewJavaKotlinArrayWithDimensions(assign)) {
        if (assign.valueArguments.size > 1) {
          return IntentType(isImplicit = false) // only consider 1d arrays
        }
        intentArraySize = assign.valueArguments[0].evaluate() as? Int ?: 0
      } else {
        assignIntentType = getIntentTypeForExpression(assign, intentInfo)
      }

      // return early if the intent assignment escaped
      if (assignIntentType.isEscaped) return assignIntentType

      val analyzer =
        IntentSimpleNameAnalyzer(assign, intent, intentInfo, intentArraySize, assignIntentType)
      intentParentUMethod.accept(analyzer)
      return analyzer.getIntentTypeAfterGivenAssignment()
    }

    private fun isNewJavaKotlinArrayWithDimensions(exp: UCallExpression): Boolean =
      exp.isNewArrayWithDimensions() ||
        exp.isMethodCall() && isArrayOfOrArrayOfNulls(exp, METHOD_ARRAY_OF_NULLS)

    private fun findLastAssignmentAndItsParentUMethod(
      intent: USimpleNameReferenceExpression,
      call: UCallExpression,
    ): Pair<UExpression, UMethod?>? {
      val assign =
        findLastAssignment(intent.resolve() as? PsiVariable ?: return null, call)
          ?.skipParenthesizedExprDown() ?: return null
      return Pair(assign, assign.getParentOfType(UMethod::class.java))
    }

    /**
     * Sets intent type's properties with the action of the call expression in-place.
     * - For `setComponent(arg1, arg2)` and `setPackage(arg1)`, the intent has a component and a
     *   package if arg1 is not null respectively.
     * - For any `setClass` and `setClassName`, the intent has a component.
     * - If the intent hasn't escaped, then we need to check if the current call is an escape call.
     */
    private fun setIntentTypeAfterCallInPlace(
      intentType: IntentType,
      call: UCallExpression,
      intentInfo: IntentExpressionInfo,
    ) {
      when (call.methodName) {
        METHOD_INTENT_SET_COMPONENT -> intentType.hasComponent = !call.isFirstArgNull()
        METHOD_INTENT_SET_PACKAGE -> intentType.hasPackage = !call.isFirstArgNull()
        METHOD_INTENT_SET_CLASS,
        METHOD_INTENT_SET_CLASS_NAME -> intentType.hasComponent = true
      }
      if (!intentType.isEscaped) {
        intentType.isEscaped = isEscapeCall(call, intentInfo)
      }
    }

    private fun UCallExpression?.isFirstArgNull(): Boolean =
      (this?.valueArguments?.getOrNull(0) as? ULiteralExpression)?.isNull ?: false

    private fun isIntentMethodCall(
      call: UCallExpression?,
      intentInfo: IntentExpressionInfo,
    ): Boolean = intentInfo.javaEvaluator.isMemberInClass(call?.resolve(), CLASS_INTENT)

    private fun isEscapeCall(call: UCallExpression, intentInfo: IntentExpressionInfo): Boolean =
      !isIntentMethodCall(call, intentInfo) && !isSelectorScopeCall(call) && !isWithScopeCall(call)

    private fun isKotlinCollection(call: UCallExpression): Boolean {
      val sourcePsi = call.sourcePsi as? KtElement ?: return false
      analyze(sourcePsi) {
        val symbol = getFunctionLikeSymbol(sourcePsi) ?: return false
        return isListOf(symbol) || isArrayOfOrArrayOfNulls(symbol, METHOD_ARRAY_OF)
      }
    }

    private fun isArrayOfOrArrayOfNulls(call: UCallExpression, arrayOfMethodName: String): Boolean {
      val sourcePsi = call.sourcePsi as? KtElement ?: return false
      analyze(sourcePsi) {
        val symbol = getFunctionLikeSymbol(sourcePsi) ?: return false
        return isArrayOfOrArrayOfNulls(symbol, arrayOfMethodName)
      }
    }

    private fun KaSession.isArrayOfOrArrayOfNulls(
      symbol: KaFunctionSymbol,
      arrayOfMethodName: String,
    ): Boolean {
      if (!hasSingleTypeParameter(symbol) { it.isReified }) return false
      if (arrayOfMethodName == METHOD_ARRAY_OF && !hasVarargValueParameterOnly(symbol)) {
        return false
      }
      val callableId = symbol.callableId ?: return false
      val packageName = callableId.packageName
      val methodName = callableId.callableName.asString()
      val returnType = symbol.returnType
      return packageName == StandardClassIds.BASE_KOTLIN_PACKAGE &&
        symbol.location == KaSymbolLocation.TOP_LEVEL &&
        methodName == arrayOfMethodName &&
        returnType.isArrayOrPrimitiveArray
    }

    private fun KaSession.isListOf(symbol: KaFunctionSymbol): Boolean {
      if (!hasSingleTypeParameter(symbol) || !hasVarargValueParameterOnly(symbol)) {
        return false
      }
      val callableId = symbol.callableId ?: return false
      val packageName = callableId.packageName
      val methodName = callableId.callableName.asString()
      val returnType = symbol.returnType
      return packageName == StandardClassIds.BASE_COLLECTIONS_PACKAGE &&
        symbol.location == KaSymbolLocation.TOP_LEVEL &&
        methodName == METHOD_LIST_OF &&
        returnType.isClassType(StandardClassIds.List)
    }

    private fun hasSingleTypeParameter(
      symbol: KaFunctionSymbol,
      typeParameterCheck: (KaTypeParameterSymbol) -> Boolean = { true },
    ): Boolean {
      @OptIn(KaExperimentalApi::class) val typeParameters = symbol.typeParameters
      if (typeParameters.size != 1) return false
      val typeParam = typeParameters[0]
      return typeParam.name.asString() == TYPE_PARAM && typeParameterCheck(typeParam)
    }

    private fun hasVarargValueParameterOnly(symbol: KaFunctionSymbol): Boolean {
      val valueParameters = symbol.valueParameters
      if (valueParameters.size != 1) return false
      return valueParameters[0].isVararg
    }

    private fun isWithScopeCall(call: UCallExpression): Boolean =
      call.methodName == "with" && isScopingFunction(call)

    // Only considers lambdas with function calls that resolve to Intent methods because with()
    // returns the lambda result
    private fun getIntentTypeForWithScopeCall(
      intent: UCallExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType {
      val intentObject =
        intent.getArgumentForParameter(0)?.skipParenthesizedExprDown()
          ?: return IntentType(isImplicit = false)
      val lambdaExp = intent.getArgumentForParameter(1) ?: return IntentType(isImplicit = false)
      if (!areAllLambdaExpressionsIntentMethodCalls(intent, lambdaExp, intentInfo))
        return IntentType(isImplicit = false)
      val intentObjectType = getIntentTypeForExpression(intentObject, intentInfo)
      return getIntentTypeForLambda(intentObject, intentObjectType, lambdaExp, intentInfo, intent)
    }

    private fun isSelectorScopeCall(call: UCallExpression): Boolean =
      isApplyAlsoScopeCall(call) || isLetRunScopeCall(call)

    private fun isApplyAlsoScopeCall(call: UCallExpression): Boolean =
      listOf("apply", "also").any { it == call.methodName } && isScopingFunction(call)

    private fun isLetRunScopeCall(call: UCallExpression): Boolean =
      listOf("let", "run").any { it == call.methodName } && isScopingFunction(call)

    // For let and run, considers lambdas with function calls that resolve to Intent methods because
    // they return the lambda result
    private fun getIntentTypeForSelectorScopeCall(
      intent: UQualifiedReferenceExpression,
      intentObject: UExpression,
      intentType: IntentType,
      call: UCallExpression,
      intentInfo: IntentExpressionInfo,
    ): IntentType {
      val lambdaExp = call.valueArguments.getOrNull(0) ?: return IntentType(isImplicit = false)
      if (
        isLetRunScopeCall(call) &&
          !areAllLambdaExpressionsIntentMethodCalls(call, lambdaExp, intentInfo)
      ) {
        return IntentType(isImplicit = false)
      }
      return getIntentTypeForLambda(intentObject, intentType, lambdaExp, intentInfo, intent)
    }

    private fun getIntentTypeForLambda(
      intentObject: UExpression,
      intentObjectType: IntentType,
      lambdaExp: UExpression,
      intentInfo: IntentExpressionInfo,
      intent: UExpression,
    ): IntentType {
      val analyzer =
        IntentDataFlowAnalyzer(
          startExp = intentObject,
          endExp = lambdaExp,
          intentType = intentObjectType,
          intentInfo = intentInfo,
        )
      intent.getParentOfType(UMethod::class.java)?.accept(analyzer)
      return analyzer.getIntentTypeAfterGivenAssignment()
    }

    private fun areAllLambdaExpressionsIntentMethodCalls(
      call: UCallExpression,
      lambdaExp: UExpression?,
      intentInfo: IntentExpressionInfo,
    ): Boolean {
      val expressions = getLambdaExpressionsOrNull(lambdaExp) ?: return false
      for (exp in expressions) {
        val realExp = getRealExpressionOrNull(exp) ?: return false
        val containsOnlyIntentMethodCalls =
          when (realExp) {
            is UCallExpression -> isIntentMethodCall(realExp, intentInfo)
            is UQualifiedReferenceExpression ->
              containsQualifiedIntentMethodCalls(call, realExp, intentInfo)
            else -> false
          }
        if (!containsOnlyIntentMethodCalls) return false
      }
      return true
    }

    private fun getLambdaExpressionsOrNull(lambdaExp: UExpression?): List<UExpression>? =
      ((lambdaExp as? ULambdaExpression)?.body as? UBlockExpression)?.expressions

    private fun getRealExpressionOrNull(exp: UExpression): UExpression? {
      val realExp = if (exp is UReturnExpression) exp.returnExpression else exp
      return realExp?.skipParenthesizedExprDown()
    }

    private fun isReferencingContextObject(call: UCallExpression, exp: UExpression): Boolean =
      (isScopingIt(call) && (exp as? USimpleNameReferenceExpression)?.identifier == "it") ||
        (isScopingThis(call) && exp is UThisExpression)

    private fun containsQualifiedIntentMethodCalls(
      call: UCallExpression,
      exp: UQualifiedReferenceExpression,
      intentInfo: IntentExpressionInfo,
    ): Boolean {
      val chain = exp.getQualifiedChain()
      if (chain.isEmpty()) return false
      val (head, tail) = chain.headTail()
      return isReferencingContextObject(call, head) &&
        tail.all { isIntentMethodCall(it as? UCallExpression, intentInfo) }
    }

    /**
     * Helper class for analyzing the data flow of an intent variable to determine if it's implicit.
     *
     * Conditions for an intent variable to be implicit:
     * - If an intent variable is a single Intent, then it's implicit if after the last assignment
     *   but before its use, it wasn't made explicit in its component and package, nor escape calls
     *   were called on the variable.
     * - If an intent variable is an array of Intents of a given [intentArraySize], then it's
     *   implicit if any of its elements are implicit. The default value for unassigned indices is
     *   false, meaning the array won't be flagged if some values aren't assigned.
     *
     * For single intent's implementation details, refer to [IntentDataFlowAnalyzer]'s
     * documentation.
     */
    private data class IntentSimpleNameAnalyzer(
      val lastAssignment: UExpression,
      val intentFinalReference: USimpleNameReferenceExpression,
      val initialIntentInfo: IntentExpressionInfo,
      val intentArraySize: Int,
      val assignIntentType: IntentType,
    ) :
      IntentDataFlowAnalyzer(
        startExp = lastAssignment,
        endExp = initialIntentInfo.intentArgument,
        intentType = assignIntentType,
        intentInfo = initialIntentInfo,
      ) {
      private val intentArrayValuesIsImplicit: BooleanArray = BooleanArray(intentArraySize)

      /**
       * Considers assignments of array values and stores their isImplicit value inside
       * [intentArrayValuesIsImplicit]
       */
      override fun visitArrayAccessExpression(node: UArrayAccessExpression): Boolean {
        if (isIntentAnalyzable() && isMemberOfIntentArray(node)) {
          val parent = node.uastParent
          if (parent is UBinaryExpression && parent.operator == ASSIGN) {
            val index = node.indices.firstOrNull()?.evaluate() as? Int ?: 0
            if (index in 0 until intentArraySize) {
              intentArrayValuesIsImplicit[index] =
                getIntentTypeForExpression(parent.rightOperand, intentInfo).isImplicit()
            }
          }
        }
        return super.visitArrayAccessExpression(node)
      }

      override fun getIntentTypeAfterGivenAssignment(): IntentType {
        if (intentArraySize == 0) return intentType
        val finalIntentType = IntentType(isImplicit = intentArrayValuesIsImplicit.any { it })
        finalIntentType.isEscaped = intentType.isEscaped
        return finalIntentType
      }

      private fun isMemberOfIntentArray(node: UArrayAccessExpression): Boolean =
        (node.receiver as? USimpleNameReferenceExpression)?.identifier ==
          intentFinalReference.identifier
    }

    /**
     * Helper class for analyzing the data flow of a single intent to determine if it's implicit.
     *
     * An intent is implicit if after the `startExp` but before `endExp`, it wasn't made explicit in
     * its component and package, nor escape calls were called on the variable.
     *
     * How to use the analyzer:
     * 1. Start the analysis by calling the analyzer on a method where the intent variable is
     *    assigned and used.
     * 2. Retrieve the final result by calling the [getIntentTypeAfterGivenAssignment] method.
     *
     * How the analyzer works:
     * - It starts the analysis from the `startExp` expression.
     * - Then, the analyzer tracks the intentType properties.
     * - If the intent reference calls an external method or is used by an external method, then at
     *   that point the analyzer can't consider the intent implicit, so `intentType.isEscaped` is
     *   set to true.
     * - The analysis is finished when it reaches `endExp`.
     */
    private open class IntentDataFlowAnalyzer(
      val startExp: UExpression,
      val endExp: UExpression,
      var intentType: IntentType,
      val intentInfo: IntentExpressionInfo,
    ) : DataFlowAnalyzer(setOf(startExp)) {
      var isEndReached: Boolean = false

      override fun afterVisitExpression(node: UExpression) {
        if (node == endExp) isEndReached = true
        if (!isEndReached) super.afterVisitExpression(node)
      }

      fun isIntentAnalyzable(): Boolean = !intentType.isEscaped && !isEndReached

      override fun receiver(call: UCallExpression) {
        if (!isIntentAnalyzable()) return
        setIntentTypeAfterCallInPlace(intentType, call, intentInfo)
      }

      override fun argument(call: UCallExpression, reference: UElement) {
        if (!isIntentAnalyzable()) return
        if (!isIgnoredArgument(call, reference)) intentType.isEscaped = true
      }

      /**
       * Returns the final intent type of the intent. This should be called only after tha analyzer
       * has finished its analysis.
       */
      open fun getIntentTypeAfterGivenAssignment(): IntentType = intentType

      private fun isPendingIntentGetMethod(call: UCallExpression): Boolean =
        intentInfo.javaEvaluator.isMemberInClass(call.resolve(), PendingIntentUtils.CLASS) &&
          PendingIntentUtils.GET_METHOD_NAMES.contains(call.methodName)

      private fun isIgnoredArgument(call: UCallExpression, reference: UElement): Boolean =
        call == intentInfo.intentArgument.skipParenthesizedExprDown() ||
          reference == intentInfo.intentArgument ||
          call.isNewArrayWithInitializer() ||
          call.isArrayInitializer() ||
          isPendingIntentGetMethod(call) ||
          isWithScopeCall(call) ||
          isKotlinCollection(call)
    }

    /** Lint fixes related code */
    private fun buildImmutableFixOrNull(
      context: JavaContext,
      flagsArgument: UExpression,
    ): LintFix? {
      val mutableFlagExpression = findMutableFlagExpression(context, flagsArgument) ?: return null
      return LintFix.create()
        .name("Replace FLAG_MUTABLE with FLAG_IMMUTABLE")
        .replace()
        .reformat(true)
        .shortenNames()
        .range(context.getLocation(mutableFlagExpression))
        .with(PendingIntentUtils.FLAG_IMMUTABLE_STR)
        .build()
    }

    private fun buildNoCreateFix(context: JavaContext, flagsArgument: UExpression): LintFix {
      val orSymbol = if (isKotlin(flagsArgument.lang)) "or" else "|"
      val fixText = " $orSymbol " + PendingIntentUtils.FLAG_NO_CREATE_STR
      return LintFix.create()
        .name("Add FLAG_NO_CREATE")
        .replace()
        .end()
        .reformat(true)
        .shortenNames()
        .range(context.getLocation(flagsArgument))
        .with(fixText)
        .build()
    }

    private fun findMutableFlagExpression(
      context: JavaContext,
      flagsArgument: UExpression,
    ): UExpression? {
      var mutableFlagExpression: UExpression? = null
      flagsArgument.accept(
        object : UastVisitor {
          // Returns false to ensure the visitor goes to the element's children
          override fun visitElement(node: UElement): Boolean = false

          override fun visitExpression(node: UExpression): Boolean {
            if (ConstantEvaluator.evaluate(context, node) == PendingIntentUtils.FLAG_MUTABLE) {
              mutableFlagExpression = node
              return true
            }
            return false
          }
        }
      )
      return mutableFlagExpression
    }
  }
}
