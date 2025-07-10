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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UReferenceExpression

/**
 * Reports all references to `GetGoogleIdOption` and `GetSignInWithGoogleOption`, unless we see a
 * reference to `GoogleIdTokenCredential[.Companion].createFrom`.
 */
class CredentialManagerSignInWithGoogleDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = listOf("createFrom")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val qualifiedName = method.containingClass?.qualifiedName ?: return
    if (!qualifiedName.startsWith(GOOGLE_ID_PACKAGE_DOT)) return
    // We don't check the qualified name exactly, as the method could be within the companion object
    // or not.
    if (!qualifiedName.contains("GoogleIdTokenCredential")) return
    val partialResults = context.getPartialResults(ISSUE).map()
    partialResults.put(KEY_SAW_CREATE_FROM, true)
  }

  override fun getApplicableReferenceNames() =
    listOf("GetGoogleIdOption", "GetSignInWithGoogleOption")

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    val qualifiedName = (referenced as? PsiClass)?.qualifiedName ?: return
    if (!qualifiedName.startsWith(GOOGLE_ID_PACKAGE_DOT)) return
    // The class reference might actually be a reference to the companion object, so we try to
    // remove this part.
    val className = qualifiedName.removePrefix(GOOGLE_ID_PACKAGE_DOT).removeSuffix(".Companion")
    // Avoid something like: "${GOOGLE_ID_PACKAGE_DOT}aaaaaaaaa.GetGoogleIdOption"
    if (className !in getApplicableReferenceNames()) return

    val partialResults = context.getPartialResults(ISSUE).map()
    // Skip if suppressed.
    if (context.driver.isSuppressed(context, ISSUE, reference)) return
    // Otherwise, we store the location.
    partialResults.getOrPutLintMap(KEY_OPTION_REFS).appendLocation(context.getLocation(reference))
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) return

    // If we saw a reference to createFrom in any project, then we are done.
    if (partialResults.maps().any { it.getBoolean(KEY_SAW_CREATE_FROM) == true }) {
      return
    }

    // Otherwise: report all references to Google ID classes.
    val locations =
      partialResults
        .maps()
        // Each module's LintMap _might_ contain a LintMap of locations.
        .mapNotNull { it.getMap(KEY_OPTION_REFS) }
        // We can flatten these into a single list of locations.
        .flatMap { it.asLocationSequence() }

    for (location in locations) {
      context.report(
        ISSUE,
        location,
        "Use of `:googleid` classes without use of `GoogleIdTokenCredential.createFrom`",
      )
    }
  }

  override fun sameMessage(issue: Issue, new: String, old: String): Boolean {
    return true
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {
    // intentional missing backtick
    private const val MESSAGE_PREFIX =
      "Use of `:googleid` classes without use of `GoogleIdTokenCredential"

    private const val KEY_OPTION_REFS = "OPTION_REFS"
    private const val KEY_SAW_CREATE_FROM = "SAW_CREATE_FROM"
    private const val GOOGLE_ID_PACKAGE_DOT = "com.google.android.libraries.identity.googleid."

    private val IMPLEMENTATION =
      Implementation(
        CredentialManagerSignInWithGoogleDetector::class.java,
        EnumSet.of(Scope.ALL_JAVA_FILES),
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredentialManagerSignInWithGoogle",
        briefDescription = "Misuse of Sign in with Google API",
        explanation =
          """
          When using `:googleid` classes like `GetGoogleIdOption` and `GetSignInWithGoogleOption`, \
          you must handle the response using `GoogleIdTokenCredential.createFrom`.

          This check reports all uses of these `:googleid` classes if there are no \
          references to `GoogleIdTokenCredential[.Companion].createFrom`.
          """,
        moreInfo =
          "https://developer.android.com/identity/sign-in/credential-manager-siwg#create-sign",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}
