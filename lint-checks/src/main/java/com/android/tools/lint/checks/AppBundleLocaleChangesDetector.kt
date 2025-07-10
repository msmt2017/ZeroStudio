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
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.GradleContext
import com.android.tools.lint.detector.api.GradleScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.tryResolve

class AppBundleLocaleChangesDetector : Detector(), SourceCodeScanner, GradleScanner {

  private var localeChangeLocation: Location? = null
  private var playCoreLanguageRequestFound = false
  private var bundleLanguageSplittingDisabled = false

  override fun getApplicableReferenceNames(): List<String> {
    return listOf(REF_LOCALE)
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(REF_SETLOCALE, REF_SETLOCALES, REF_ADDLANGUAGE, REF_REQUESTINSTALL)
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    when (method.name) {
      REF_SETLOCALE,
      REF_SETLOCALES -> {
        if (
          localeChangeLocation == null &&
            context.evaluator.isMemberInClass(method, CLASS_CONFIGURATION) &&
            (!isLocationSuppressed(context, node))
        ) {
          localeChangeLocation = context.getLocation(node)
        }
      }
      REF_ADDLANGUAGE -> {
        if (
          !playCoreLanguageRequestFound &&
            context.evaluator.isMemberInClass(method, CLASS_SPLITINSTALLREQUEST_BUILDER)
        ) {
          playCoreLanguageRequestFound = true
        }
      }
      REF_REQUESTINSTALL -> {
        val evaluator = context.evaluator
        with(method.parameterList) {
          if (
            !playCoreLanguageRequestFound &&
              parameters.size == 4 &&
              evaluator.typeMatches(parameters[0].type, CLASS_SPLITINSTALLMANAGER) &&
              evaluator.isSuspend(method) &&
              (node.valueArgumentCount == 2 ||
                node.sourcePsi?.collectDescendantsOfType<KtValueArgumentName>()?.any {
                  it.text == "languages"
                } == true)
          ) {
            playCoreLanguageRequestFound = true
          }
        }
      }
    }
  }

  @Suppress("LintImplPsiEquals")
  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    if (
      localeChangeLocation == null &&
        referenced is PsiField &&
        context.evaluator.isMemberInClass(referenced, CLASS_CONFIGURATION)
    ) {
      // Check if we're assigning to the `locale` field
      val binaryExpr = reference.getParentOfType(UBinaryExpression::class.java)
      if (
        binaryExpr != null &&
          binaryExpr.operator == UastBinaryOperator.ASSIGN &&
          binaryExpr.leftOperand.tryResolve() == referenced &&
          (!isLocationSuppressed(context, reference))
      ) {
        localeChangeLocation = context.getLocation(reference)
      }
    }
  }

  /** Checks whether [ISSUE] is suppressed at the given location. */
  private fun isLocationSuppressed(context: JavaContext, expression: UExpression): Boolean {
    if (context.isSuppressedWithComment(expression, ISSUE)) {
      return true
    }
    if (context.isGlobalAnalysis()) {
      // We'll check for this when reporting the incident later. For partial
      // analysis, we need to check now since we won't have access to the sources
      // during reporting.
      return false
    }
    return context.driver.isSuppressed(context, ISSUE, expression)
  }

  override fun checkDslPropertyAssignment(
    context: GradleContext,
    property: String,
    value: String,
    parent: String,
    parentParent: String?,
    propertyCookie: Any,
    valueCookie: Any,
    statementCookie: Any,
  ) {
    if (
      property == "enableSplit" &&
        parent == "language" &&
        parentParent == "bundle" &&
        value == "false"
    ) {
      bundleLanguageSplittingDisabled = true
    }
  }

  override fun afterCheckEachProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      if (context.project === context.mainProject) {
        checkConditionsAndReport(
          context,
          localeChangeLocation,
          playCoreLanguageRequestFound = playCoreLanguageRequestFound,
          bundleLanguageSplittingDisabled = bundleLanguageSplittingDisabled,
        )
      }
    } else {
      val partialResults = context.getPartialResults(ISSUE).map()
      localeChangeLocation?.let { location ->
        partialResults.put(KEY_LOCALE_CHANGE_LOCATION, location)
      }
      if (playCoreLanguageRequestFound) {
        partialResults.put(KEY_PLAYCORE_LANGUAGE_REQUEST_FOUND, true)
      }
      if (bundleLanguageSplittingDisabled) {
        partialResults.put(KEY_BUNDLE_LANGUAGE_SPLITTING_DISABLED, true)
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project === context.mainProject) {
      with(partialResults.map()) {
        checkConditionsAndReport(
          context,
          getLocation(KEY_LOCALE_CHANGE_LOCATION),
          playCoreLanguageRequestFound = getBoolean(KEY_PLAYCORE_LANGUAGE_REQUEST_FOUND) ?: false,
          bundleLanguageSplittingDisabled =
            getBoolean(KEY_BUNDLE_LANGUAGE_SPLITTING_DISABLED) ?: false,
        )
      }
    }
  }

  private fun checkConditionsAndReport(
    context: Context,
    localeChangeLocation: Location?,
    playCoreLanguageRequestFound: Boolean,
    bundleLanguageSplittingDisabled: Boolean,
  ) {
    if (
      localeChangeLocation != null &&
        !(playCoreLanguageRequestFound || bundleLanguageSplittingDisabled)
    ) {
      Incident(context)
        .issue(ISSUE)
        .message(
          "Found dynamic locale changes, but did not find corresponding Play Core " +
            "library calls for downloading languages and splitting by language " +
            "is not disabled in the `bundle` configuration"
        )
        .location(localeChangeLocation)
        .report()
    }
  }

  companion object {
    @JvmField
    val ISSUE =
      Issue.create(
        id = "AppBundleLocaleChanges",
        briefDescription = "App Bundle handling of runtime locale changes",
        explanation =
          """
                When changing locales at runtime (e.g. to provide an in-app language switcher), \
                the Android App Bundle must be configured to not split by locale or the Play Core \
                library must be used to download additional locales at runtime.
                """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(
            AppBundleLocaleChangesDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.GRADLE_FILE),
          ),
        moreInfo =
          "https://developer.android.com/guide/app-bundle/configure-base#handling_language_changes",
      )

    private const val REF_SETLOCALE = "setLocale"
    private const val REF_SETLOCALES = "setLocales"
    private const val REF_LOCALE = "locale"
    private const val REF_ADDLANGUAGE = "addLanguage"
    private const val REF_REQUESTINSTALL = "requestInstall"
    private const val CLASS_CONFIGURATION = "android.content.res.Configuration"
    private const val CLASS_SPLITINSTALLREQUEST_BUILDER =
      "com.google.android.play.core.splitinstall.SplitInstallRequest.Builder"
    private const val CLASS_SPLITINSTALLMANAGER =
      "com.google.android.play.core.splitinstall.SplitInstallManager"
    private const val KEY_LOCALE_CHANGE_LOCATION = "localeChangeLocation"
    private const val KEY_PLAYCORE_LANGUAGE_REQUEST_FOUND = "playCoreLanguageRequestFound"
    private const val KEY_BUNDLE_LANGUAGE_SPLITTING_DISABLED = "bundleLanguageSplittingDisabled"
  }
}
