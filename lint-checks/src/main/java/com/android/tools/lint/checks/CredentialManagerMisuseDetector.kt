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

import com.android.tools.lint.client.api.UElementHandler
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
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCatchClause
import org.jetbrains.uast.UTypeReferenceExpression

/**
 * Reports all calls to `getCredential` and `getCredentialAsync`, unless we see a reference to
 * `NoCredentialException`.
 */
class CredentialManagerMisuseDetector : Detector(), SourceCodeScanner {

  override fun getApplicableMethodNames() = listOf("getCredential", "getCredentialAsync")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val cls = method.containingClass ?: return
    if (
      !context.evaluator.implementsInterface(
        cls,
        "androidx.credentials.CredentialManager",
        strict = false,
      )
    )
      return

    // Skip if suppressed.
    if (context.driver.isSuppressed(context, ISSUE, node)) return

    // Otherwise, store the location.
    context
      .getPartialResults(ISSUE)
      // The LintMap for the current module.
      .map()
      .getOrPutLintMap(KEY_GET_CREDENTIAL_CALLS)
      .appendLocation(context.getLocation(node))
  }

  override fun getApplicableUastTypes() =
    listOf(UCatchClause::class.java, UTypeReferenceExpression::class.java)

  override fun createUastHandler(context: JavaContext) =
    object : UElementHandler() {

      private fun storeSawNoCredentialException() {
        context.getPartialResults(ISSUE).map().put(KEY_SAW_NO_CREDENTIAL_EXCEPTION, true)
      }

      override fun visitCatchClause(node: UCatchClause) {
        // UCatchClause.accept does not visit the type references, so we must check them manually.
        // UCatchClause.typeReferences includes the children of disjoint exception types, like in
        // `catch (A|B|C ex) { ... }`.
        for (typeReference in node.typeReferences) {
          if (
            typeReference.type.canonicalText ==
              "androidx.credentials.exceptions.NoCredentialException"
          ) {
            storeSawNoCredentialException()
            break
          }
        }
      }

      override fun visitTypeReferenceExpression(node: UTypeReferenceExpression) {
        // E.g.
        //  x is NoCredentialException
        //  x instanceof NoCredentialException
        //  when(x) { is NoCredentialException -> ... }
        if (node.type.canonicalText == "androidx.credentials.exceptions.NoCredentialException") {
          storeSawNoCredentialException()
        }
      }
    }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) return

    // If we saw a reference to the exception class in any project, then we are done.
    if (partialResults.maps().any { it.getBoolean(KEY_SAW_NO_CREDENTIAL_EXCEPTION) == true }) {
      return
    }

    // Otherwise: report all getCredential calls.
    val locations =
      partialResults
        .maps()
        // Each module's LintMap _might_ contain a LintMap of locations.
        .mapNotNull { it.getMap(KEY_GET_CREDENTIAL_CALLS) }
        // We can flatten these into a single list of locations.
        .flatMap { it.asLocationSequence() }

    for (location in locations) {
      context.report(
        ISSUE,
        location,
        "Call to `CredentialManager.getCredential` without use of `NoCredentialException`",
      )
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {

    private const val KEY_GET_CREDENTIAL_CALLS = "GET_CREDENTIAL_CALLS"
    private const val KEY_SAW_NO_CREDENTIAL_EXCEPTION = "SAW_NO_CREDENTIAL_EXCEPTION"

    private val IMPLEMENTATION =
      Implementation(CredentialManagerMisuseDetector::class.java, EnumSet.of(Scope.ALL_JAVA_FILES))

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredentialManagerMisuse",
        briefDescription = "Misuse of Credential Manager API",
        explanation =
          """
          When calling `CredentialManager.getCredential` or `CredentialManager.getCredentialAsync`, \
          you should handle `NoCredentialException` somewhere in your project.
          """,
        moreInfo =
          "https://developer.android.com/identity/sign-in/credential-manager#handle-exceptions",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}
