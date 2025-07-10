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

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.kotlin.asJava.unwrapped
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.toUElementOfType

/**
 * Reports secrets, such as API keys, that appear to have come from source code and are being passed
 * as arguments to recognized APIs. Only covers a few simple cases for the argument (string literal,
 * reference to a local variable with a string literal initializer, reference to a field with a
 * string literal initializer).
 */
class SecretDetector : Detector(), SourceCodeScanner {

  override fun getApplicableConstructorTypes() = listOf(FQN_MODEL)

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val keyArg =
      node.getArgumentForParameter(CONSTRUCTOR_API_KEY_PARAM_INDEX)?.skipParenthesizedExprDown()
        ?: return
    if (
      isLiteralStringKey(keyArg) ||
        isReferenceToLocalVariableStringKey(keyArg) ||
        isReferenceToFieldStringKey(keyArg, context)
    ) {
      context.report(
        ISSUE,
        keyArg,
        context.getLocation(keyArg),
        "This argument looks like an API key that has come from source code; API keys should not be included in source code",
        fix()
          .url("https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin")
          .build(),
      )
    }
  }

  private fun isLiteralStringKey(expression: UExpression): Boolean {
    // Even basic Kotlin string literals can appear as string templates.
    if (
      expression is UPolyadicExpression &&
        expression.sourcePsi is KtStringTemplateExpression &&
        expression.operands.size == 1
    ) {
      return isLiteralStringKey(expression.operands[0].skipParenthesizedExprDown())
    }
    val literal = expression as? ULiteralExpression ?: return false
    val value = literal.value ?: return false
    return value is String && value.length > 30 && value.startsWith(KEY_PREFIX)
  }

  private fun isReferenceToLocalVariableStringKey(expression: UExpression): Boolean {
    // Try to resolve the reference expression to a local variable that is initialized with a key
    // string literal.
    val refExpression = expression as? UReferenceExpression ?: return false
    val localVar = refExpression.resolve().toUElementOfType<ULocalVariable>() ?: return false
    val initializer = localVar.uastInitializer?.skipParenthesizedExprDown() ?: return false
    return isLiteralStringKey(initializer)
  }

  private fun isReferenceToFieldStringKey(expression: UExpression, context: JavaContext): Boolean {
    // Try to resolve the reference expression to a field that is initialized with a key string
    // literal.
    val refExpression = expression as? UReferenceExpression ?: return false
    // `unwrapped` needed if this resolves to a KtUltraLightMethodForSourceDeclaration representing
    // the getter for a property.
    val field = refExpression.resolve()?.unwrapped?.toUElementOfType<UField>() ?: return false
    val initializer = field.uastInitializer?.skipParenthesizedExprDown() ?: return false
    if (!isLiteralStringKey(initializer)) return false

    // We could now return true, but we want to avoid a few false-positive cases.

    // No warning if the field is within a file that looks like a BuildConfig file.
    val fieldFilePsi = field.sourcePsi?.containingFile ?: return false
    if (fieldFilePsi.name.startsWith("BuildConfig")) return false

    // No warning if the field has a suppression annotation.
    if (context.driver.isSuppressed(context, ISSUE, field as? UElement)) return false

    // Simple case: if the field is in the same file as the reference expression, then warn.
    if (expression.sourcePsi?.containingFile?.isEquivalentTo(fieldFilePsi) == true) return true

    // Otherwise, we give up if this isn't a Gradle project, as we want to use a heuristic based on
    // source folders.
    val project = context.project
    if (!project.isGradleProject) return false

    // Only warn if the file is within one of the (non-generated, non-test) source folders of the
    // current project.
    val fieldVirtualFile = fieldFilePsi.virtualFile ?: return false
    val fieldPath = fieldVirtualFile.fileSystem.getNioPath(fieldVirtualFile) ?: return false
    return project.javaSourceFolders.any { fieldPath.startsWith(it.toPath()) }
  }

  companion object {

    private const val FQN_MODEL = "com.google.ai.client.generativeai.GenerativeModel"

    /** The second parameter is the API key parameter. */
    private const val CONSTRUCTOR_API_KEY_PARAM_INDEX = 1

    private const val KEY_PREFIX = "AIza"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "SecretInSource",
        briefDescription = "Secret in source code",
        explanation =
          """
          Including secrets, such as API keys, in source code is a security risk. \
          It is generally best practice to not include API keys in source code, \
          and instead use something like the Secrets Gradle Plugin for Android.
          """,
        category = Category.SECURITY,
        priority = 9,
        severity = Severity.WARNING,
        implementation = Implementation(SecretDetector::class.java, Scope.JAVA_FILE_SCOPE),
        moreInfo =
          "https://developers.google.com/maps/documentation/android-sdk/secrets-gradle-plugin",
      )
  }
}
