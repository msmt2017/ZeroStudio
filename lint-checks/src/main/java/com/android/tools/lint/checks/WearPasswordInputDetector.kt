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

import com.android.SdkConstants.ATTR_INPUT_TYPE
import com.android.SdkConstants.EDIT_TEXT
import com.android.SdkConstants.FQCN_INPUT_TYPE
import com.android.SdkConstants.InputType.REF_TYPE_NUMBER_VARIATION_PASSWORD
import com.android.SdkConstants.InputType.REF_TYPE_TEXT_VARIATION_PASSWORD
import com.android.SdkConstants.InputType.REF_TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
import com.android.SdkConstants.InputType.REF_TYPE_TEXT_VARIATION_WEB_PASSWORD
import com.android.SdkConstants.InputType.VALUE_NUMBER_PASSWORD
import com.android.SdkConstants.InputType.VALUE_TEXT_PASSWORD
import com.android.SdkConstants.InputType.VALUE_TEXT_VISIBLE_PASSWORD
import com.android.SdkConstants.InputType.VALUE_TEXT_WEB_PASSWORD
import com.android.resources.ResourceFolderType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import org.jetbrains.uast.UReferenceExpression
import org.w3c.dom.Attr

class WearPasswordInputDetector : WearDetector(), XmlScanner, SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(
        WearPasswordInputDetector::class.java,
        Scope.JAVA_AND_RESOURCE_FILES,
        Scope.RESOURCE_FILE_SCOPE,
        Scope.JAVA_FILE_SCOPE,
      )

    @JvmField
    val ISSUE =
      Issue.create(
          id = "WearPasswordInput",
          briefDescription = "Wear: Using password input",
          explanation =
            """
          Your app must not ask the user to input password directly on the Wear device.
        """,
          category = Category.USABILITY,
          severity = Severity.ERROR,
          implementation = IMPLEMENTATION,
          enabledByDefault = true,
        )
        .addMoreInfo("https://developer.android.com/training/wearables/apps/auth-wear#auth-methods")

    private const val MESSAGE = "Don't ask Wear OS users for a password"

    private val VALUE_PASSWORD_INPUT_TYPES =
      setOf(
        VALUE_NUMBER_PASSWORD,
        VALUE_TEXT_PASSWORD,
        VALUE_TEXT_VISIBLE_PASSWORD,
        VALUE_TEXT_WEB_PASSWORD,
      )

    private val TYPE_PASSWORD_INPUT_TYPES =
      listOf(
        REF_TYPE_TEXT_VARIATION_PASSWORD,
        REF_TYPE_NUMBER_VARIATION_PASSWORD,
        REF_TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        REF_TYPE_TEXT_VARIATION_WEB_PASSWORD,
      )
  }

  override fun appliesTo(folderType: ResourceFolderType) = ResourceFolderType.LAYOUT == folderType

  override fun getApplicableAttributes() = listOf(ATTR_INPUT_TYPE)

  override fun getApplicableReferenceNames() = TYPE_PASSWORD_INPUT_TYPES

  override fun visitReference(
    context: JavaContext,
    reference: UReferenceExpression,
    referenced: PsiElement,
  ) {
    if (
      isWearProject && context.evaluator.isMemberInClass(referenced as? PsiField, FQCN_INPUT_TYPE)
    ) {
      context.report(Incident(ISSUE, context.getLocation(reference), MESSAGE))
    }
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (
      isWearProject &&
        attribute.ownerElement.tagName == EDIT_TEXT &&
        attribute.value.findAnyOf(VALUE_PASSWORD_INPUT_TYPES) != null
    ) {
      context.report(Incident(ISSUE, context.getLocation(attribute), MESSAGE))
    }
  }
}
