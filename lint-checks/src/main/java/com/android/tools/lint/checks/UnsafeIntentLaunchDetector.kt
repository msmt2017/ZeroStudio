/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_EXPORTED
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.TAG_ACTIVITY
import com.android.SdkConstants.TAG_RECEIVER
import com.android.SdkConstants.TAG_SERVICE
import com.android.tools.lint.checks.BroadcastReceiverUtils.BROADCAST_RECEIVER_METHOD_NAMES
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findConstruction
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isReturningContext
import com.android.tools.lint.detector.api.isReturningLambdaResult
import com.android.utils.iterator
import com.android.utils.subtag
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSwitchLabelStatementBase
import com.intellij.psi.PsiVariable
import java.util.EnumSet
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedName
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Element

class UnsafeIntentLaunchDetector : Detector(), SourceCodeScanner, XmlScanner {

  private val registerReceiverMethods = BROADCAST_RECEIVER_METHOD_NAMES

  override fun getApplicableMethodNames() =
    listOf("getParcelableExtra", "getParcelable", "getIntent", "parseUri") + registerReceiverMethods

  override fun applicableSuperClasses() =
    listOf("android.app.Activity", "android.content.BroadcastReceiver", "android.app.Service")

  override fun getApplicableElements() = listOf(TAG_ACTIVITY, TAG_SERVICE, TAG_RECEIVER)

  override fun visitElement(context: XmlContext, element: Element) {
    storeUnprotectedComponents(context, getProtectedComponent(context, element) ?: return)
  }

  private fun isComponentExported(
    context: Context,
    root: Element,
    incidentComponent: String?,
  ): Boolean {
    val application = root.subtag(SdkConstants.TAG_APPLICATION) ?: return false
    for (component in application) {
      when (component.tagName) {
        TAG_ACTIVITY,
        TAG_RECEIVER,
        TAG_SERVICE -> {
          if (incidentComponent == getProtectedComponent(context, component)) return true
        }
      }
    }
    return false
  }

  // Returns the fully qualified component name if the component is protected; otherwise, null
  // The element passed in is guaranteed to be one of the activity, receiver or service tag.
  private fun getProtectedComponent(context: Context, component: Element): String? {
    val exportedAttr = component.getAttributeNS(ANDROID_URI, ATTR_EXPORTED)
    if (
      "true" == exportedAttr ||
        exportedAttr.isEmpty() && component.getElementsByTagName("intent-filter").length > 0
    ) {
      val permission = component.getAttributeNS(ANDROID_URI, ATTR_PERMISSION)
      if (!isProbablyProtectedBySignaturePermission(permission)) {
        var componentName = component.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (componentName.startsWith(".")) componentName = context.project.`package` + componentName
        return componentName
      }
    }
    return null
  }

  // Any permission that is not declared by the system as normal permission is considered as a
  // signature protected permission.
  // It could be hard to actually check if the permission is actually a signature permission.
  private fun isProbablyProtectedBySignaturePermission(permission: String?): Boolean {
    return !permission.isNullOrBlank() && !KNOWN_NORMAL_PERMISSIONS.contains(permission)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    // This method handles unsafe intent passed in as parameter to certain methods by the platform.
    val evaluator = context.evaluator
    val methodNames =
      when {
        evaluator.extendsClass(declaration.javaPsi, ACTIVITY_CLASS, true) ->
          UNSAFE_INTENT_AS_PARAMETER_METHODS[ACTIVITY_CLASS]
        evaluator.extendsClass(declaration.javaPsi, SERVICE_CLASS, true) ->
          UNSAFE_INTENT_AS_PARAMETER_METHODS[SERVICE_CLASS]
        evaluator.extendsClass(declaration.javaPsi, BROADCAST_RECEIVER_CLASS, true) ->
          UNSAFE_INTENT_AS_PARAMETER_METHODS[BROADCAST_RECEIVER_CLASS]
        else -> return
      } ?: return
    for (methodName in methodNames) {
      for (psiMethod in declaration.javaPsi.findMethodsByName(methodName, false)) {
        val method = psiMethod.toUElementOfType<UMethod>()
        val intentParam =
          method
            ?.javaPsi
            ?.parameterList
            ?.parameters
            ?.firstOrNull { it.type.canonicalText == INTENT_CLASS }
            ?.toUElementOfType<UParameter>()
        val visitor =
          IntentLaunchChecker(
            initial = setOf(intentParam ?: return),
            context = context,
            location = context.getLocation(intentParam.sourcePsi),
            checkProtectedBroadcast =
              UNSAFE_INTENT_AS_PARAMETER_METHODS[BROADCAST_RECEIVER_CLASS]?.contains(methodName) ==
                true,
          )
        method.accept(visitor)
        if (visitor.launched) {
          reportIncident(context, visitor)
        }
      }
    }
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (
      method.name in registerReceiverMethods &&
        evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)
    ) {
      // register receiver at runtime methods, figure out if it is registered as unprotected.
      processRuntimeReceiver(context, node, method)
    } else if (
      isUnParcellingIntentMethods(evaluator, method) or isParseUnsafeUri(evaluator, node, method)
    ) {
      // methods that launch Intent. Figure out if the Intent is launched.
      val visitor =
        IntentLaunchChecker(
          initial = setOf(node),
          context = context,
          location = context.getLocation(node),
        )
      val containingMethod = node.getParentOfType(UMethod::class.java)
      containingMethod?.accept(visitor)
      if (visitor.launched) {
        if (visitor.unprotectedReceiver) {
          // The anonymous component registered-at-runtime receiver case, report the issue
          // immediately.
          reportIssue(context, null, visitor.location)
        } else {
          reportIncident(context, visitor)
        }
      }
    }
  }

  private fun isParseUnsafeUri(
    evaluator: JavaEvaluator,
    call: UCallExpression,
    method: PsiMethod,
  ): Boolean {
    if (method.name == "parseUri" && evaluator.isMemberInClass(method, INTENT_CLASS)) {
      val intentArg = call.getArgumentForParameter(0)?.skipParenthesizedExprDown()
      val getUriStringCall =
        if (intentArg is USimpleNameReferenceExpression) {
          findLastAssignment(intentArg.resolve() as? PsiVariable ?: return false, call)
        } else intentArg

      val getUriStringMethod =
        (getUriStringCall?.findSelector() as? UCallExpression)?.resolve() ?: return false
      return isUnParcellingStringMethods(evaluator, getUriStringMethod)
    } else return false
  }

  private fun isUnParcellingIntentMethods(evaluator: JavaEvaluator, method: PsiMethod): Boolean {
    return when (method.name) {
      "getParcelableExtra" ->
        evaluator.isMemberInSubClassOf(method, INTENT_CLASS) ||
          evaluator.isMemberInClass(method, INTENT_COMPAT_CLASS)
      "getParcelable" ->
        evaluator.isMemberInSubClassOf(method, BUNDLE_CLASS) ||
          evaluator.isMemberInClass(method, BUNDLE_COMPAT_CLASS)
      "getIntent" -> evaluator.isMemberInSubClassOf(method, CONTEXT_CLASS)
      else -> false
    }
  }

  private fun isUnParcellingStringMethods(evaluator: JavaEvaluator, method: PsiMethod): Boolean {
    return (method.name == "getStringExtra") &&
      evaluator.isMemberInSubClassOf(method, INTENT_CLASS) ||
      method.name == "getString" && evaluator.isMemberInSubClassOf(method, BUNDLE_CLASS)
  }

  private fun processRuntimeReceiver(
    context: JavaContext,
    call: UCallExpression,
    method: PsiMethod,
  ) {
    val receiverArg = UastLintUtils.findArgument(call, method, BROADCAST_RECEIVER_CLASS) ?: return
    if (receiverArg.isNullLiteral()) return

    if (!isRuntimeReceiverProtected(call, method, context.evaluator)) {
      val receiverConstructor = findConstruction(BROADCAST_RECEIVER_CLASS, receiverArg, call, true)
      val unprotectedReceiverClassName =
        receiverConstructor?.classReference.getQualifiedName() ?: return
      storeUnprotectedComponents(context, unprotectedReceiverClassName)
    }
  }

  fun isRuntimeReceiverProtected(
    call: UCallExpression,
    method: PsiMethod,
    javaEvaluator: JavaEvaluator,
  ): Boolean {
    // The parameter positions vary across the various registerReceiver*() methods, so rather
    // than hardcode them we simply look them up based on the parameter name and type.

    val flagsArg = UastLintUtils.findArgument(call, method, TYPE_INT)
    val evaluator = ConstantEvaluator().allowFieldInitializers()
    val flags = evaluator.evaluate(flagsArg) as? Int
    if (flags != null && (flags and RECEIVER_NOT_EXPORTED) != 0) return true

    val permissionArg = UastLintUtils.findArgument(call, method, TYPE_STRING)
    val permission = evaluator.evaluate(permissionArg) as? String
    if (isProbablyProtectedBySignaturePermission(permission)) return true

    val filterArg =
      UastLintUtils.findArgument(call, method, "android.content.IntentFilter") ?: return true
    val (isProtected, _) =
      BroadcastReceiverUtils.checkIsProtectedReceiverAndReturnUnprotectedActions(
        filterArg,
        call,
        javaEvaluator,
      )

    return isProtected
  }

  private fun storeUnprotectedComponents(context: Context, unprotectedComponentName: String) {
    val lintMap = context.getPartialResults(ISSUE).map()
    val unprotectedComponents =
      lintMap.getMap(KEY_UNPROTECTED) ?: map().also { lintMap.put(KEY_UNPROTECTED, it) }
    // the value of the lintMap is not used. only the key is used later.
    unprotectedComponents.put(unprotectedComponentName, true)
  }

  private fun reportIncident(context: Context, visitor: IntentLaunchChecker) {
    if (context.isGlobalAnalysis()) {
      val incidentComponent = visitor.incidentClass
      if (
        isComponentExported(
          context,
          context.mainProject.mergedManifest?.documentElement ?: return,
          incidentComponent,
        )
      ) {
        reportIssue(context, incidentComponent, visitor.location)
      }
    } else {
      val lintMap = context.getPartialResults(ISSUE).map()
      val incidents = lintMap.getMap(KEY_INCIDENTS) ?: map().also { lintMap.put(KEY_INCIDENTS, it) }
      // key is not important. so the size of the map is used to make it unique.
      incidents.put(
        incidents.size.toString(),
        map().apply {
          put(KEY_LOCATION, visitor.location)
          put(KEY_SECONDARY_LOCATION, visitor.location.secondary ?: return)
          put(KEY_INCIDENT_CLASS, visitor.incidentClass ?: return)
        },
      )
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    val incidents = partialResults.map().getMap(KEY_INCIDENTS) ?: return
    val unprotectedComponents = partialResults.map().getMap(KEY_UNPROTECTED) ?: return
    for (key in incidents) {
      val incidentMap = incidents.getMap(key)
      val incidentComponent = incidentMap?.get(KEY_INCIDENT_CLASS) ?: continue
      if (unprotectedComponents.containsKey(incidentComponent)) {
        val location = incidentMap.getLocation(KEY_LOCATION) ?: continue
        location.secondary = incidentMap.getLocation(KEY_SECONDARY_LOCATION)
        reportIssue(context, incidentComponent, location)
      }
    }
  }

  private fun reportIssue(context: Context, incidentComponent: String?, location: Location) {
    val component = if (incidentComponent.isNullOrBlank()) "" else " $incidentComponent"
    val message =
      """
          This intent could be coming from an untrusted source. It is later launched by \
          an unprotected component$component. You could either make the component$component \
          protected; or sanitize this intent using androidx.core.content.IntentSanitizer.
      """
        .trimIndent()
    context.report(Incident(ISSUE, location, message))
  }

  private inner class IntentLaunchChecker(
    initial: Collection<UElement>,
    var context: JavaContext,
    var location: Location,
    var incidentClass: String? = null,
    var launched: Boolean = false,
    var returned: Boolean = false,
    var unprotectedReceiver: Boolean = false,
    var resolveCallDepth: Int = 0,
    var checkProtectedBroadcast: Boolean = false,
  ) : DataFlowAnalyzer(initial) {

    override fun returnsSelf(call: UCallExpression): Boolean {
      // intent = getIntent().getParcelableExtra() could have been considered chained builder
      // without this override
      // and falsely identify the getIntent() calls as an issue.
      if (
        call.methodName in INTENT_METHODS_RETURNS_INTENT_BUT_NOT_SELF &&
          call.receiverType?.canonicalText == INTENT_CLASS
      ) {
        return false
      }

      if (isReturningLambdaResult(call)) {
        for (lambda in call.valueArguments) {
          if (lambda !is ULambdaExpression) break
          // call's arguments could either be empty (in case of run, with, apply) or the context (in
          // case of let, also)
          val tracked =
            (if (lambda.valueParameters.isEmpty()) getThisExpression(lambda.body)
            else lambda.valueParameters[0]) ?: break
          val returnsTracker = ReturnsTracker(context, tracked)
          lambda.body.accept(returnsTracker)
          if (returnsTracker.returned) return true
        }
      }

      return super.returnsSelf(call)
    }

    override fun argument(call: UCallExpression, reference: UElement) {
      if (incidentClass == null) {
        // This method could be called recursively, see else branch below. The top level method call
        // would be in the incident class.
        incidentClass = call.getParentOfType(UClass::class.java)?.qualifiedName
        if (incidentClass == null) {
          // must be an anonymous BroadcastReceiver. It has to be registered at runtime.
          unprotectedReceiver = handleAnonymousBroadcastReceiver(call)
        }
      }
      if (isIntentLaunchedBySystem(context.evaluator, call)) {
        if (!checkProtectedBroadcast || !inProtectedBroadcastBranch(context, call, reference)) {
          launched = true
          location.secondary = context.getLocation(call)
          location.secondary?.message = "The unsafe intent is launched here."
        }
      } else {
        if (resolveCallDepth > MAX_CALL_DEPTH) return
        // escaped to another method call. check the method recursively.
        val containingMethod = call.resolve()?.toUElementOfType<UMethod>() ?: return
        val intentParameter =
          context.evaluator.computeArgumentMapping(call, containingMethod.javaPsi)[reference]
        val visitor =
          IntentLaunchChecker(
            initial = setOf(intentParameter.toUElement() ?: return),
            context = context,
            location = location,
            incidentClass = incidentClass,
            resolveCallDepth = resolveCallDepth + 1,
          )
        containingMethod.accept(visitor)
        if (visitor.launched) {
          reportIncident(context, visitor)
        } else if (visitor.returned) {
          // if the visited method returns the passed-in unsafe Intent, add this call to track it.
          instances.add(call)
        }
      }
    }

    /** Returns if the expression is evaluated to a protected broadcast action. */
    private fun isProtectedBroadcastAction(expression: UExpression?): Boolean {
      val action =
        (ConstantEvaluator().allowFieldInitializers().evaluate(expression)) as? String
          ?: return false
      return BroadcastReceiverUtils.isProtectedBroadcast(action)
    }

    /**
     * Check if the call is within a branch of code that is protected by a protected broadcast
     * action. It could either be an if statement that checks if the action of the intent is equal
     * to a protected action; or an equivalent of a switch case statement.
     */
    private fun inProtectedBroadcastBranch(
      context: JavaContext,
      call: UCallExpression,
      reference: UElement,
    ): Boolean {
      return inProtectedBroadcastIfBranch(context, call, reference) ||
        inProtectedBroadcastSwitchCase(call, reference)
    }

    private fun inProtectedBroadcastIfBranch(
      context: JavaContext,
      call: UCallExpression,
      reference: UElement,
    ): Boolean {
      var ifExp = call.getParentOfType<UIfExpression>()
      while (ifExp != null) {
        var op1: UExpression? = null
        var op2: UExpression? = null
        val condition = ifExp.condition
        if (condition is UBinaryExpression && condition.operator === UastBinaryOperator.EQUALS) {
          // handle kotlin ==
          op1 = condition.leftOperand
          op2 = condition.rightOperand
        } else if (condition is UQualifiedReferenceExpression) {
          // handle java equals method.
          val methodCall = condition.selector as? UCallExpression
          if (methodCall?.methodName == "equals") {
            op1 = condition.receiver
            op2 = methodCall.valueArguments[0]
          }
        }
        if (
          op1 != null &&
            op2 != null &&
            ((isIntentAction(op1, reference) && isProtectedBroadcastAction(op2)) ||
              (isIntentAction(op2, reference) && isProtectedBroadcastAction(op1)))
        ) {
          return context.getLocation(call) in context.getLocation(ifExp.thenExpression)
        }
        ifExp = ifExp.getParentOfType()
      }
      return false
    }

    private fun inProtectedBroadcastSwitchCase(
      call: UCallExpression,
      reference: UElement,
    ): Boolean {
      var switchExp = call.getParentOfType<USwitchExpression>()
      while (switchExp != null) {
        val subject = switchExp.expression as? UReferenceExpression
        val caseExpression = call.getParentOfType<USwitchClauseExpression>() ?: return false
        val caseValue = caseExpression.caseValues.firstOrNull() ?: return false
        if ((caseValue.sourcePsi as? PsiSwitchLabelStatementBase)?.isDefaultCase == true)
          return false
        if (isIntentAction(subject, reference) && isProtectedBroadcastAction(caseValue)) return true
        switchExp = switchExp.getParentOfType()
      }
      return false
    }

    private fun isIntentAction(expression: UExpression?, intentRef: UElement): Boolean {
      val actionAssignmentCall = findIntentActionAssignmentCall(expression)
      return actionAssignmentCall?.receiver?.skipParenthesizedExprDown()?.tryResolve() ===
        intentRef.tryResolve()
    }

    private fun findIntentActionAssignmentCall(expression: UExpression?): UCallExpression? {
      val actionExpr = expression?.skipParenthesizedExprDown()
      val resolved = actionExpr?.tryResolve()

      if (resolved is PsiVariable) {
        val assignment = findLastAssignment(resolved, actionExpr) ?: return null
        return findIntentActionAssignmentCall(assignment)
      }

      if (actionExpr is UQualifiedReferenceExpression) {
        val call = actionExpr.selector as? UCallExpression ?: return null
        return if (isReturningContext(call)) {
          // eg. intent.apply { setAction("abc") } --> use filter variable.
          findIntentActionAssignmentCall(actionExpr.receiver)
        } else {
          // eg. intent.getAction("abc") --> use getAction("abc") UCallExpression.
          findIntentActionAssignmentCall(call)
        }
      }

      val method = resolved as? PsiMethod ?: return null
      return if ("getAction" == method.name) {
        actionExpr as? UCallExpression
      } else {
        null
      }
    }

    override fun returns(expression: UReturnExpression) {
      // indicates the passed-in unsafe intent is returned.
      returned = true
    }

    private fun isIntentLaunchedBySystem(evaluator: JavaEvaluator, call: UCallExpression): Boolean {
      val method = call.resolve() ?: return false
      return isIntentLaunchedByContextMethods(evaluator, method) ||
        isIntentLaunchedByActivityMethods(evaluator, method) ||
        isIntentLaunchedByBroadcastReceiver(evaluator, method) ||
        isIntentLaunchedByPendingIntentMethods(evaluator, method)
    }

    private fun isIntentLaunchedByContextMethods(
      evaluator: JavaEvaluator,
      method: PsiMethod,
    ): Boolean {
      return method.containingClass?.qualifiedName == CONTEXT_CLASS ||
        method.containingClass?.qualifiedName == CONTEXT_COMPAT_CLASS ||
        method.findSuperMethods(evaluator.findClass(CONTEXT_CLASS)).isNotEmpty()
    }

    private fun isIntentLaunchedByActivityMethods(
      evaluator: JavaEvaluator,
      method: PsiMethod,
    ): Boolean {
      return method.name in ACTIVITY_INTENT_LAUNCH_METHODS &&
        (evaluator.isMemberInSubClassOf(method, ACTIVITY_CLASS) ||
          evaluator.isMemberInClass(method, ACTIVITY_COMPAT_CLASS))
    }

    private fun isIntentLaunchedByBroadcastReceiver(
      evaluator: JavaEvaluator,
      method: PsiMethod,
    ): Boolean {
      return method.name == "peekService" &&
        evaluator.isMemberInSubClassOf(method, BROADCAST_RECEIVER_CLASS)
    }

    private fun isIntentLaunchedByPendingIntentMethods(
      evaluator: JavaEvaluator,
      method: PsiMethod,
    ): Boolean {
      return method.name in PENDING_INTENT_LAUNCH_METHODS &&
        evaluator.isMemberInClass(method, PENDING_INTENT_CLASS)
    }

    private fun handleAnonymousBroadcastReceiver(call: UCallExpression): Boolean {
      // The call is an Intent launching call. First find the anonymous class which is the class
      // that contains this call.
      val anonymousClass = call.getParentOfType(UClass::class.java, true)
      // The parent of the anonymous class is the constructor call of the anonymous receiver. Use a
      // DataFlowAnalyzer to keep track
      // of it to see if it is registered as an unprotected receiver.
      val parent = anonymousClass?.uastParent ?: return false
      var result = false
      val visitor =
        object : DataFlowAnalyzer(setOf(parent)) {
          override fun argument(call: UCallExpression, reference: UElement) {
            if (call.methodName in registerReceiverMethods) {
              val method = call.resolve() ?: return

              if (!isRuntimeReceiverProtected(call, method, context.evaluator)) {
                result = true
              }
            }
          }
        }
      // We will only handle the case the receiver instance did not escape the class where it is
      // instantiated.
      parent.getParentOfType(UMethod::class.java)?.accept(visitor)
      return result
    }

    /** Get the "this" instance expression from an expression - typically a block of code. */
    private fun getThisExpression(block: UExpression): UThisExpression? {
      var result: UThisExpression? = null
      block.accept(
        object : AbstractUastVisitor() {
          override fun visitThisExpression(node: UThisExpression): Boolean {
            result = node
            return super.visitThisExpression(node)
          }
        }
      )
      return result
    }

    /**
     * check if the tracked is returned from the visited method. It will follow the tracked if it is
     * passed down to another method.
     */
    inner class ReturnsTracker(
      val context: JavaContext,
      tracked: UElement,
      var returned: Boolean = false,
      var resolveCallDepth: Int = 0,
    ) : DataFlowAnalyzer(setOf(tracked)) {
      override fun returns(expression: UReturnExpression) {
        returned = true
      }

      override fun argument(call: UCallExpression, reference: UElement) {
        if (resolveCallDepth > MAX_CALL_DEPTH) return
        val containingMethod = call.resolve()?.toUElementOfType<UMethod>() ?: return
        val tracked =
          context.evaluator
            .computeArgumentMapping(call, containingMethod.javaPsi)[reference]
            .toUElement() ?: return
        val returnsTracker =
          ReturnsTracker(context, tracked, resolveCallDepth = resolveCallDepth + 1)
        call.resolve()?.toUElementOfType<UMethod>()?.accept(returnsTracker)
        returned = returnsTracker.returned
      }
    }
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(
        UnsafeIntentLaunchDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
        Scope.JAVA_FILE_SCOPE,
      )

    /** Issue describing the problem and pointing to the detector implementation. */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "UnsafeIntentLaunch",
        briefDescription = "Launched Unsafe Intent",
        explanation =
          """
                    Intent that potentially could come from an untrusted source should not be \
                    launched from an unprotected component without first being sanitized. See \
                    this support FAQ for details: https://support.google.com/faqs/answer/9267555
                    """,
        category = Category.SECURITY,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val RECEIVER_NOT_EXPORTED = 0x4
    private const val KEY_UNPROTECTED = "unprotected"
    private const val KEY_INCIDENTS = "incidents"
    private const val KEY_INCIDENT_CLASS = "incidentClass"
    private const val KEY_LOCATION = "location"
    private const val KEY_SECONDARY_LOCATION = "secondaryLocation"
    private const val CONTEXT_CLASS = "android.content.Context"
    private const val ACTIVITY_CLASS = "android.app.Activity"
    private const val SERVICE_CLASS = "android.app.Service"
    private const val BROADCAST_RECEIVER_CLASS = "android.content.BroadcastReceiver"
    private const val PENDING_INTENT_CLASS = "android.app.PendingIntent"
    private const val INTENT_CLASS = "android.content.Intent"
    private const val BUNDLE_CLASS = "android.os.Bundle"
    private const val INTENT_COMPAT_CLASS = "androidx.core.content.IntentCompat"
    private const val BUNDLE_COMPAT_CLASS = "androidx.core.os.BundleCompat"
    private const val CONTEXT_COMPAT_CLASS = "androidx.core.content.ContextCompat"
    private const val ACTIVITY_COMPAT_CLASS = "androidx.core.app.ActivityCompat"

    private const val MAX_CALL_DEPTH = 3

    private val UNSAFE_INTENT_AS_PARAMETER_METHODS =
      mapOf(
        BROADCAST_RECEIVER_CLASS to arrayOf("onReceive"),
        ACTIVITY_CLASS to arrayOf("onNewIntent", "onActivityResult", "onActivityReenter"),
        SERVICE_CLASS to
          arrayOf("onBind", "onUnbind", "onRebind", "onTaskRemoved", "onStartCommand", "onStart"),
      )

    private val ACTIVITY_INTENT_LAUNCH_METHODS =
      listOf(
        "createPendingResult",
        "navigateUpTo",
        "navigateUpToFromChild",
        "startActivityIfNeeded",
        "startActivityForResult",
        "startActivityFromChild",
        "startActivityFromFragment",
        "startIntentSender",
        "startIntentSenderFromChild",
        "startIntentSenderForResult",
        "startNextMatchingActivity",
        "setResult",
      )

    private val PENDING_INTENT_LAUNCH_METHODS =
      listOf("getActivity", "getBroadcast", "getService", "getForegroundService")

    private val INTENT_METHODS_RETURNS_INTENT_BUT_NOT_SELF =
      arrayOf("cloneFilter", "getOriginalIntent", "getSelector", "getParcelableExtra")

    private val KNOWN_NORMAL_PERMISSIONS =
      listOf(
        "android.permission.READ_BASIC_PHONE_STATE",
        "android.permission.MANAGE_OWN_CALLS",
        "android.permission.CALL_COMPANION_APP",
        "android.permission.HIGH_SAMPLING_RATE_SENSORS",
        "android.permission.USE_FINGERPRINT",
        "android.permission.USE_BIOMETRIC",
        "android.permission.READ_PROFILE",
        "android.permission.WRITE_PROFILE",
        "android.permission.READ_SOCIAL_STREAM",
        "android.permission.WRITE_SOCIAL_STREAM",
        "android.permission.READ_USER_DICTIONARY",
        "android.permission.WRITE_USER_DICTIONARY",
        "android.permission.WRITE_SMS",
        "com.android.browser.permission.READ_HISTORY_BOOKMARKS",
        "com.android.browser.permission.WRITE_HISTORY_BOOKMARKS",
        "android.permission.AUTHENTICATE_ACCOUNTS",
        "android.permission.MANAGE_ACCOUNTS",
        "android.permission.USE_CREDENTIALS",
        "android.permission.SUBSCRIBED_FEEDS_READ",
        "android.permission.SUBSCRIBED_FEEDS_WRITE",
        "android.permission.FLASHLIGHT",
        "com.android.alarm.permission.SET_ALARM",
        "android.permission.ACCESS_LOCATION_EXTRA_COMMANDS",
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.BLUETOOTH",
        "android.permission.BLUETOOTH_ADMIN",
        "android.permission.NFC",
        "android.permission.NFC_TRANSACTION_EVENT",
        "android.permission.NFC_PREFERRED_PAYMENT_INFO",
        "android.permission.CHANGE_WIFI_MULTICAST_STATE",
        "android.permission.VIBRATE",
        "android.permission.WAKE_LOCK",
        "android.permission.TRANSMIT_IR",
        "android.permission.TURN_SCREEN_ON",
        "android.permission.MODIFY_AUDIO_SETTINGS",
        "android.permission.DISABLE_KEYGUARD",
        "android.permission.REQUEST_PASSWORD_COMPLEXITY",
        "android.permission.GET_TASKS",
        "android.permission.REORDER_TASKS",
        "android.permission.RESTART_PACKAGES",
        "android.permission.KILL_BACKGROUND_PROCESSES",
        "android.permission.REQUEST_COMPANION_RUN_IN_BACKGROUND",
        "android.permission.REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND",
        "android.permission.REQUEST_COMPANION_USE_DATA_IN_BACKGROUND",
        "android.permission.REQUEST_COMPANION_PROFILE_WATCH",
        "android.permission.HIDE_OVERLAY_WINDOWS",
        "android.permission.SET_WALLPAPER",
        "android.permission.SET_WALLPAPER_HINTS",
        "android.permission.EXPAND_STATUS_BAR",
        "com.android.launcher.permission.INSTALL_SHORTCUT",
        "com.android.launcher.permission.UNINSTALL_SHORTCUT",
        "android.permission.READ_SYNC_SETTINGS",
        "android.permission.WRITE_SYNC_SETTINGS",
        "android.permission.READ_SYNC_STATS",
        "android.permission.PERSISTENT_ACTIVITY",
        "android.permission.GET_PACKAGE_SIZE",
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.BROADCAST_STICKY",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.SCHEDULE_EXACT_ALARM",
        "android.permission.USE_EXACT_ALARM",
        "android.permission.REQUEST_DELETE_PACKAGES",
        "android.permission.REQUEST_OBSERVE_COMPANION_DEVICE_PRESENCE",
        "android.permission.DELIVER_COMPANION_MESSAGES",
        "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        "android.permission.ACCESS_NOTIFICATION_POLICY",
        "android.permission.READ_INSTALL_SESSIONS",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.USE_FULL_SCREEN_INTENT",
        "android.permission.QUERY_ALL_PACKAGES",
        "android.permission.READ_NEARBY_STREAMING_POLICY",
        "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION",
      )
  }
}
