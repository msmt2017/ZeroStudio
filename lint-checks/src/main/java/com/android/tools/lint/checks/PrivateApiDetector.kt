/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.sdklib.AndroidVersion
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.ApiConstraint
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TypeEvaluator
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.VersionChecks
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClassLiteralExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown

/** Checks that the code is not using reflection to access hidden Android APIs. */
class PrivateApiDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    /** Using hidden/private APIs. */
    @JvmField
    val PRIVATE_API =
      Issue.create(
        id = "PrivateApi",
        briefDescription = "Using Private APIs",
        explanation =
          """
            Using reflection to access hidden/private Android APIs is not safe; it will often not work on \
            devices from other vendors, and it may suddenly stop working (if the API is removed) or crash \
            spectacularly (if the API behavior changes, since there are no guarantees for compatibility).
            """,
        moreInfo = "https://developer.android.com/preview/restrictions-non-sdk-interfaces",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(PrivateApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    @JvmField
    val DISCOURAGED_PRIVATE_API =
      Issue.create(
        id = "DiscouragedPrivateApi",
        briefDescription = "Using Discouraged Private API",
        explanation =
          """
            Usage of restricted non-SDK interface may throw an exception at runtime. Accessing \
            non-SDK methods or fields through reflection has a high likelihood to break your app \
            between versions, and is being restricted to facilitate future app compatibility.
            """,
        moreInfo = "https://developer.android.com/preview/restrictions-non-sdk-interfaces",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(PrivateApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    @JvmField
    val SOON_BLOCKED_PRIVATE_API =
      Issue.create(
        id = "SoonBlockedPrivateApi",
        briefDescription = "Using Soon-to-Be Blocked Private API",
        explanation =
          """
            Usage of restricted non-SDK interface will throw an exception at runtime. Accessing \
            non-SDK methods or fields through reflection has a high likelihood to break your app \
            between versions, and is being restricted to facilitate future app compatibility.
            """,
        moreInfo = "https://developer.android.com/preview/restrictions-non-sdk-interfaces",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = Implementation(PrivateApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    @JvmField
    val BLOCKED_PRIVATE_API =
      Issue.create(
        id = "BlockedPrivateApi",
        briefDescription = "Using Blocked Private API",
        explanation =
          """
            Usage of restricted non-SDK interface is forbidden for this targetSDK. Accessing \
            non-SDK methods or fields through reflection has a high likelihood to break your app \
            between versions, and is being restricted to facilitate future app compatibility.
            """,
        moreInfo = "https://developer.android.com/preview/restrictions-non-sdk-interfaces",
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.FATAL,
        androidSpecific = true,
        implementation = Implementation(PrivateApiDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    private const val LOAD_CLASS = "loadClass"
    private const val FOR_NAME = "forName"
    private const val GET_CLASS = "getClass"
    private const val GET_DECLARED_CONSTRUCTOR = "getDeclaredConstructor"
    private const val GET_DECLARED_METHOD = "getDeclaredMethod"
    private const val GET_DECLARED_FIELD = "getDeclaredField"
    private val KOTLIN_REFLECTION_METHODS =
      listOf(
        "members", // this is available in kotlin-stdlib, the rest are in kotlin-reflect
        "declaredMembers",
        "declaredFunctions",
        "declaredMemberFunctions",
        "declaredMemberProperties",
      )
    private const val ERROR_MESSAGE =
      "Accessing internal APIs via reflection is not " +
        "supported and may not work on all devices or in the future"
  }

  private var client: LintClient? = null
  private var psiFactory: PsiElementFactory? = null

  // Only initialize private API database on demand, at most once per Lint session.
  private var cachedApiDatabase: Boolean = false
  private var privateApiDatabase: PrivateApiLookup? = null
  private val apiDatabase: PrivateApiLookup?
    get() {
      if (!cachedApiDatabase && privateApiDatabase == null && client != null) {
        privateApiDatabase = PrivateApiLookup.get(client!!)
        cachedApiDatabase = true
      }
      return privateApiDatabase
    }

  override fun beforeCheckRootProject(context: Context) {
    client = context.client
    cachedApiDatabase = false
    psiFactory = PsiElementFactory.getInstance(context.project.ideaProject)
  }

  // ---- Implements JavaPsiScanner ----

  override fun getApplicableMethodNames(): List<String> =
    listOf(FOR_NAME, LOAD_CLASS, GET_DECLARED_CONSTRUCTOR, GET_DECLARED_METHOD, GET_DECLARED_FIELD)

  override fun getApplicableReferenceNames(): List<String> = KOTLIN_REFLECTION_METHODS

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    val name = method.name
    if (LOAD_CLASS == name) {
      if (
        evaluator.isMemberInClass(method, "java.lang.ClassLoader") ||
          evaluator.isMemberInClass(method, "dalvik.system.DexFile")
      ) {
        checkLoadClass(context, node)
      }
    } else {
      if (!evaluator.isMemberInClass(method, "java.lang.Class")) {
        return
      }
      when (name) {
        GET_DECLARED_METHOD -> checkGetDeclaredMethod(context, node)
        GET_DECLARED_FIELD -> checkGetDeclaredField(context, node)
        else -> checkLoadClass(context, node)
      }
    }
  }

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    // Kotlin reflection is harder to analyze statically, there are multiple ways of
    // finally matching a method from the collection of declared members, etc. We heuristically
    // try to match the strings we see with method and field names from the private API list.
    // TODO: implement once Lint supports resolving property access.
  }

  private fun checkGetDeclaredMethod(context: JavaContext, call: UCallExpression) {
    val cls = getJavaClassFromMemberLookup(call) ?: return

    val arguments = call.valueArguments
    if (arguments.isEmpty()) {
      return
    }
    val methodName = ConstantEvaluator.evaluateString(context, arguments[0], false)

    val aClass = context.evaluator.findClass(cls)
    if (
      aClass != null &&
        methodName != null &&
        aClass.findMethodsByName(methodName, true).isNotEmpty()
    ) {
      // Hidden and deleted methods aren't part of android.jar, so use the private API database
      // directly
      val desc = getMethodDescriptor(arguments, context, methodName) ?: return
      val restriction = apiDatabase?.getMethodRestriction(cls, methodName, desc)
      if (restriction != null) {
        reportIssue(context, restriction, methodName, call)
      }

      return
    }

    val targetSdk = context.project.targetSdk
    if (targetSdk < AndroidVersion.VersionCodes.O) {
      reportUnknownMember(cls, context, call)
    } else if (methodName != null) {
      // When targetSDK is at least 28, we perform stricter checks against the API deny list.
      val desc = getMethodDescriptor(arguments, context, methodName) ?: return
      val restriction = apiDatabase?.getMethodRestriction(cls, methodName, desc)
      // if restriction is Restriction.UNKNOWN, Consider calling
      //   reportUnknownMember(cls, context, call)
      // instead of reportIssue to flag unknown methods as suspicious.
      reportIssue(context, restriction, methodName, call)
    }
  }

  private fun getMethodDescriptor(
    arguments: List<UExpression>,
    context: JavaContext,
    methodName: String,
  ): String? {
    val argTypes =
      if (arguments.size >= 2)
        arguments.subList(1, arguments.size).mapNotNull { getJavaClassType(it) }.toTypedArray()
      else emptyArray()
    return context.evaluator.constructMethodDescription(
      method = methodName,
      argumentTypes = argTypes,
    )
  }

  private fun checkGetDeclaredField(context: JavaContext, call: UCallExpression) {
    val cls = getJavaClassFromMemberLookup(call) ?: return

    val arguments = call.valueArguments
    if (arguments.isEmpty()) {
      return
    }
    val fieldName = ConstantEvaluator.evaluateString(context, arguments[0], false)

    val aClass = context.evaluator.findClass(cls)
    if (aClass != null && fieldName != null && aClass.findFieldByName(fieldName, true) != null) {
      // Hidden and deleted methods aren't part of android.jar, so use the private API database
      // directly
      val restriction = apiDatabase?.getFieldRestriction(cls, fieldName)
      if (restriction != null) {
        reportIssue(context, restriction, fieldName, call)
      }

      return
    }

    val targetSdk = context.project.targetSdk
    if (targetSdk < AndroidVersion.VersionCodes.O) {
      reportUnknownMember(cls, context, call)
    } else if (fieldName != null) {
      // When targetSDK is at least 28, we perform stricter checks against the API deny list.
      val restriction = apiDatabase?.getFieldRestriction(cls, fieldName)
      // if restriction is Restriction.UNKNOWN, Consider calling
      //   reportUnknownMember(cls, context, call)
      // instead of reportIssue to flag unknown methods as suspicious.
      reportIssue(context, restriction, fieldName, call)
    }
  }

  private fun checkLoadClass(context: JavaContext, call: UCallExpression) {
    val arguments = call.valueArguments
    if (arguments.isEmpty()) {
      return
    }
    val value = ConstantEvaluator.evaluate(context, arguments[0]) as? String ?: return

    var isInternal = false
    if (value.startsWith("com.android.internal.")) {
      isInternal = true
    } else if (
      value.startsWith("com.android.") ||
        value.startsWith("android.") && !value.startsWith("android.support.")
    ) {
      // Attempting to access internal API? Look in two places:
      //  (1) SDK class
      //  (2) API database
      val aClass = context.evaluator.findClass(value)

      if (aClass != null) { // Found in SDK: not internal
        return
      }
      val apiLookup = ApiLookup.getOrNull(context.client, context.project.buildTarget) ?: return
      isInternal = !apiLookup.containsClass(value)
    }

    if (isInternal) {
      val location = context.getLocation(call)
      context.report(PRIVATE_API, call, location, ERROR_MESSAGE)
    }
  }

  /**
   * Given a Class#getMethodDeclaration or getFieldDeclaration etc call, figure out the
   * corresponding class name the method is being invoked on
   *
   * @param call the [Class.getDeclaredMethod] or [Class.getDeclaredField] call
   * @return the fully qualified name of the class, if found
   */
  private fun getJavaClassFromMemberLookup(call: UCallExpression): String? =
    getJavaClassType(call.receiver)?.canonicalText

  /** We know [element] has type java.lang.Class<T> and we try to find out the PsiType for T. */
  private fun getJavaClassType(element: UElement?): PsiType? {
    if (element is UExpression) {
      if (element is UParenthesizedExpression) {
        return getJavaClassType(element.expression)
      }

      // First try the type inferred from the Psi, in case it's a known class reference.
      val type = element.getExpressionType()

      if (type is PsiClassType && type.parameterCount == 1) {
        var clazz = type.parameters[0]

        if (clazz is PsiClassType) {
          PsiPrimitiveType.getUnboxedType(clazz)?.let {
            // Make sure we extract the primitive type (int.class, Integer.TYPE in Java,
            // Int::class.javaPrimitiveType in Kotlin)
            if (element is UQualifiedReferenceExpression) {
              val identifier =
                (element.selector.skipParenthesizedExprDown() as? USimpleNameReferenceExpression)
                  ?.identifier
              if (identifier == "javaPrimitiveType" || identifier == "TYPE") {
                clazz = it
              }
            }
            if (element is UClassLiteralExpression && element.evaluate() is PsiPrimitiveType) {
              clazz = it
            }
          }
          return clazz
        }
        // Here we might have a wildcard type, most likely an unbounded Class<?> coming from
        // a loadClass or Class.forName() call. We can also have a bounded <? extends Foo>,
        // from foo.getClass(), but if Foo is not final we cannot statically guarantee the
        // receiver is indeed class Foo. So we fall-through to the handling below.
      }
      if (element is UReferenceExpression) {
        val resolved = element.resolve()
        if (resolved is PsiVariable) {
          // Follow the indirection and inspect the actual definition
          UastLintUtils.findLastAssignment(resolved, element)?.let { expression ->
            return getJavaClassType(expression)
          }
        }

        if (
          element is UQualifiedReferenceExpression &&
            element.selector.skipParenthesizedExprDown() is UCallExpression
        ) {
          val call = element.selector.skipParenthesizedExprDown() as UCallExpression
          val name = call.methodName

          if (FOR_NAME == name || LOAD_CLASS == name) {
            val arguments = call.valueArguments
            if (arguments.isNotEmpty()) {
              return ConstantEvaluator.evaluateString(null, arguments[0], false)?.let {
                psiFactory!!.createTypeFromText(it, null)
              }
            }
          } else if (GET_CLASS == name) {
            return TypeEvaluator.evaluate(element.receiver)
          }

          // TODO: Are there any other common reflection utility methods (from reflection
          // libraries etc) ?
        }
      }
    }
    return TypeEvaluator.evaluate(element)
  }

  private fun reportIssue(
    context: JavaContext,
    restriction: Restriction?,
    api: String,
    call: UCallExpression,
  ) {
    val targetSdk = context.project.targetSdk

    fun fatal() {
      context.report(
        BLOCKED_PRIVATE_API,
        call,
        context.getLocation(call),
        "Reflective access to $api is forbidden when targeting API $targetSdk and above",
      )
    }

    fun error() {
      context.report(
        SOON_BLOCKED_PRIVATE_API,
        call,
        context.getLocation(call),
        "Reflective access to $api will throw an exception when targeting API $targetSdk and above",
      )
    }

    fun warning() {
      context.report(
        DISCOURAGED_PRIVATE_API,
        call,
        context.getLocation(call),
        "Reflective access to $api, which is not part of the public SDK and therefore likely to change in future Android releases",
      )
    }

    when (restriction) {
      Restriction.DENY -> fatal()
      Restriction.MAYBE_MAX_O ->
        if (isAllowed(context, call, targetSdk, AndroidVersion.VersionCodes.O)) {
          warning()
        } else {
          error()
        }
      Restriction.MAYBE_MAX_P ->
        if (isAllowed(context, call, targetSdk, AndroidVersion.VersionCodes.P)) {
          warning()
        } else {
          error()
        }
      Restriction.MAYBE_MAX_Q ->
        if (isAllowed(context, call, targetSdk, AndroidVersion.VersionCodes.Q)) {
          warning()
        } else {
          error()
        }
      Restriction.MAYBE_MAX_R ->
        if (isAllowed(context, call, targetSdk, AndroidVersion.VersionCodes.R)) {
          warning()
        } else {
          error()
        }
      Restriction.MAYBE -> warning()
      else -> return // nothing to report
    }
  }

  /**
   * This method checks whether the targetSdkVersion is less than the given [apiLevel], or whether
   * the code is guarded by a runtime SDK_INT check which guarantees that we are running on
   * [apiLevel] or older. This is true if the [ApiConstraint] does not include any higher API
   * levels.
   */
  private fun isAllowed(
    context: JavaContext,
    element: UCallExpression,
    targetSdk: Int,
    apiLevel: Int,
  ): Boolean {
    if (targetSdk <= apiLevel) {
      return true
    }
    val constraint = VersionChecks.getOuterVersionCheckConstraint(context, element) ?: return false
    return !constraint.everHigher(apiLevel)
  }

  private fun reportUnknownMember(cls: String, context: JavaContext, call: UCallExpression) {
    if (!(cls.startsWith("com.android.") || cls.startsWith("android."))) {
      return
    }
    val location = context.getLocation(call)
    context.report(PRIVATE_API, call, location, ERROR_MESSAGE)
  }
}
