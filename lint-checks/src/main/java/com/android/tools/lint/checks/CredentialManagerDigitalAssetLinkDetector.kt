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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_RESOURCE
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_META_DATA
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.client.api.ResourceRepositoryScope
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.utils.iterator
import com.android.utils.subtag
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression

/**
 * Reports the `<application>` element or `<meta-data>` element in the app's merged manifest if the
 * following conditions all hold:
 * - Kotlin/Java code (in any module) calls the CreatePasswordRequest constructor.
 * - The `<meta-data android:name="asset_statements"
 *   android:resource="@string/myAssetStatementString" />` element is missing, or the resource
 *   attribute is missing, or the string content is missing "include" or missing
 *   ".well-known/assetlinks.json".
 */
class CredentialManagerDigitalAssetLinkDetector : Detector(), SourceCodeScanner {

  override fun getApplicableConstructorTypes() =
    listOf("androidx.credentials.CreatePasswordRequest")

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    // Record that we have seen a call to the CreatePasswordRequest constructor.
    context.getPartialResults(ISSUE).map().put(CREATE_PASSWORD_REQUEST_SEEN_KEY, true)
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (context.project.isLibrary) return

    val createPasswordRequestSeen =
      partialResults.maps().stream().anyMatch {
        it.getBoolean(CREATE_PASSWORD_REQUEST_SEEN_KEY) == true
      }

    if (!createPasswordRequestSeen) return

    val manifest = context.project.mergedManifest?.documentElement ?: return
    val application = manifest.subtag(TAG_APPLICATION) ?: return
    val assetMetadata =
      application.iterator().asSequence().lastOrNull {
        it.localName == TAG_META_DATA &&
          it.getAttributeNS(ANDROID_URI, ATTR_NAME) == "asset_statements"
      }
    if (assetMetadata == null) {
      context.report(
        Incident(
          ISSUE,
          "Missing `<meta-data>` tag for asset statements for Credential Manager",
          context.getLocation(application),
        )
      )
      return
    }
    val resourceRef = assetMetadata.getAttributeNS(ANDROID_URI, ATTR_RESOURCE)
    if (resourceRef.isEmpty()) {
      context.report(
        Incident(
          ISSUE,
          "Missing `android:resource` attribute for asset statements string resource",
          context.getLocation(assetMetadata),
        )
      )
      return
    }
    val resourceUrl = ResourceUrl.parse(resourceRef) ?: return
    if (resourceUrl.type != ResourceType.STRING) return

    val projectResources =
      context.client.getResources(context.project, ResourceRepositoryScope.ALL_DEPENDENCIES)
    val statementsResources =
      projectResources.getResources(ResourceNamespace.TODO(), resourceUrl.type, resourceUrl.name)
    if (statementsResources.isEmpty()) return
    // Shortest key.
    val statementsResource = statementsResources.minByOrNull { it.key } ?: return
    val statementsString = statementsResource.resourceValue?.value ?: return
    if (!statementsString.contains("include")) {
      context.report(
        Incident(
          ISSUE,
          "Could not find \"include\" in asset statements string resource",
          context.getLocation(assetMetadata),
        )
      )
      return
    }
    if (!statementsString.contains(".well-known/assetlinks.json")) {
      context.report(
        Incident(
          ISSUE,
          "Could not find `.well-known/assetlinks.json` in asset statements string resource",
          context.getLocation(assetMetadata),
        )
      )
      return
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(ISSUE))
    }
  }

  companion object {
    const val CREATE_PASSWORD_REQUEST_SEEN_KEY = "CreatePasswordRequestSeen"

    private val IMPLEMENTATION =
      Implementation(
        CredentialManagerDigitalAssetLinkDetector::class.java,
        EnumSet.of(Scope.ALL_JAVA_FILES),
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "CredManMissingDal",
        briefDescription = "Missing Digital Asset Link for Credential Manager",
        explanation =
          """
          When using password sign-in through Credential Manager, \
          an asset statements string resource file \
          that includes the `assetlinks.json` files to load \
          must be declared in the manifest using a `<meta-data>` element.
          """,
        moreInfo =
          "https://developer.android.com/identity/sign-in/credential-manager#add-support-dal",
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}
