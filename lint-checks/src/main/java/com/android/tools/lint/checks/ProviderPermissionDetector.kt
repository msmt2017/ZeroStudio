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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.ATTR_READ_PERMISSION
import com.android.SdkConstants.ATTR_WRITE_PERMISSION
import com.android.SdkConstants.CLASS_CONTENTPROVIDER
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PROVIDER
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.callNeverReturns
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.next
import com.android.utils.subtag
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.skipParenthesizedExprDown
import org.w3c.dom.Element

/**
 * Looks for an issue related to manifests declaring only a readPermission for ContentProviders that
 * implement any of the write APIs (insert, update, and delete), thereby exposing these write APIs
 * to other apps with no permission check.
 */
class ProviderPermissionDetector : Detector(), SourceCodeScanner {
  override fun applicableSuperClasses(): List<String> = listOf(CLASS_CONTENTPROVIDER)

  /**
   * For each ContentProvider implementation, if any of its write APIs are implemented, adds its
   * location and implemented write methods into the lint map. Here, implemented API is defined by
   * [isImplemented].
   */
  override fun visitClass(context: JavaContext, declaration: UClass) {
    val providerName = declaration.qualifiedName ?: return
    val implWriteMethods =
      declaration.methods.filter { it.isProviderAbstractWriteMethod() && it.isImplemented() }
    if (implWriteMethods.isEmpty()) return
    val implementedWriteMethodNames =
      implWriteMethods.joinToString(prefix = "{", separator = ", ", postfix = "}") {
        "`${it.name}`"
      }
    val providerMap = LintMap()
    providerMap.put(KEY_LOCATION, context.getNameLocation(declaration))
    providerMap.put(KEY_IMPL_WRITE_METHODS, implementedWriteMethodNames)
    context.getPartialResults(PROVIDER_READ_PERMISSION_ONLY).map().put(providerName, providerMap)
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.isGlobalAnalysis()) {
      checkPartialResults(context, context.getPartialResults(PROVIDER_READ_PERMISSION_ONLY))
    }
  }

  /**
   * Only considers the main app. Iterates over all provider tags and reports
   * [PROVIDER_READ_PERMISSION_ONLY] issue if it occurs.
   */
  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    if (!context.driver.isIsolated() && context.project.isLibrary) return
    val mergedManifest = context.mainProject.mergedManifest ?: return
    val root = mergedManifest.documentElement ?: return
    val application = root.subtag(TAG_APPLICATION) ?: return
    var provider = application.subtag(TAG_PROVIDER)
    // Combine lint maps from all partial analysis runs into one lint map
    // of providerName -> Map(implementedWriteMethods: String, location: Location)
    val combinedMap = LintMap()
    partialResults.maps().forEach { combinedMap.putAll(it) }
    while (provider != null) {
      reportIfProviderReadPermissionOnlyOccurs(context, provider, combinedMap)
      provider = provider.next(TAG_PROVIDER)
    }
  }

  /**
   * [PROVIDER_READ_PERMISSION_ONLY] issue occurs if a provider satisfies all of these conditions:
   * - It has a readPermission attribute.
   * - It doesn't have permission and writePermission attributes.
   * - It has at least one implemented write API which is identified by the provider's existence in
   *   the lint map.
   *
   * If the detector is running "on-the-fly", the issue will be reported in the location of the
   * corresponding ContentProvider.
   *
   * If the detector isn't running "on-the-fly", the issue will be reported in the location of the
   * provider's manifest entry.
   */
  private fun reportIfProviderReadPermissionOnlyOccurs(
    context: Context,
    provider: Element,
    providersMap: LintMap,
  ) {
    val readPermission = provider.getAttributeNodeNS(ANDROID_URI, ATTR_READ_PERMISSION) ?: return
    provider.getAttributeNodeNS(ANDROID_URI, ATTR_WRITE_PERMISSION)?.let {
      return
    }
    provider.getAttributeNodeNS(ANDROID_URI, ATTR_PERMISSION)?.let {
      return
    }
    val providerName = resolveManifestName(provider, context.project)
    val providerMap = providersMap.getMap(providerName) ?: return
    val classLocation = providerMap.getLocation(KEY_LOCATION) ?: return
    val implementedWriteMethods = providerMap.getString(KEY_IMPL_WRITE_METHODS) ?: return
    val manifestLocation = context.getLocation(readPermission, LocationType.NAME)
    val reportLocation = if (context.driver.isIsolated()) classLocation else manifestLocation
    context.report(
      Incident(
        PROVIDER_READ_PERMISSION_ONLY,
        reportLocation,
        "$providerName implements $implementedWriteMethods write APIs but " +
          "does not protect them with a permission. Update the <provider> tag to use " +
          "android:permission or android:writePermission",
        fix()
          .replace()
          .text(ATTR_READ_PERMISSION)
          .with(ATTR_PERMISSION)
          .range(manifestLocation)
          .build(),
      )
    )
  }

  /**
   * A method is considered to be implemented if any of these conditions is true:
   * - Its body has more than 1 statement.
   * - Its body has 1 statement and that statement is neither of these:
   *     - a throw expression
   *     - a return of a literal (String, Number or null)
   *     - a constructor call to error
   *     - a constructor call to to do
   */
  private fun UMethod.isImplemented(): Boolean {
    val body = this.uastBody ?: return false
    val expressions = if (body is UBlockExpression) body.expressions else listOf(body)
    val first = expressions.firstOrNull()?.skipParenthesizedExprDown()
    return expressions.size > 1 ||
      !(first.isThrowExpression() || first.isReturnLiteral() || first.isNeverReturningCall())
  }

  private fun UExpression?.isThrowExpression(): Boolean {
    return this.getInsideReturnOrThis() is UThrowExpression
  }

  private fun UExpression?.isReturnLiteral(): Boolean {
    return this is UReturnExpression && this.returnExpression is ULiteralExpression
  }

  private fun UExpression?.getInsideReturnOrThis(): UExpression? {
    return (this as? UReturnExpression)?.returnExpression?.skipParenthesizedExprDown() ?: this
  }

  private fun UExpression?.isNeverReturningCall(): Boolean {
    val call = this.getInsideReturnOrThis()?.findSelector() as? UCallExpression ?: return false
    return callNeverReturns(call)
  }

  private fun UMethod.isProviderAbstractWriteMethod(): Boolean {
    val paramSize = this.uastParameters.size
    return when (this.name) {
      "insert" -> paramSize == 2
      "delete" -> paramSize == 3
      "update" -> paramSize == 4
      else -> false
    }
  }

  companion object {
    @JvmField
    val PROVIDER_READ_PERMISSION_ONLY: Issue =
      Issue.create(
        id = "ProviderReadPermissionOnly",
        briefDescription = "Provider with readPermission only and implemented write APIs",
        explanation =
          """
                This check looks for Content Providers that only have the `readPermission` \
                attribute but implement write APIs.

                If `android:readPermission` is specified and both `android:permission` and \
                `android:writePermission` are omitted, other apps can access any write operations \
                that this provider exposes with no permission check. For a quick fix, changing the \
                existing `android:readPermission` to `android:permission` will protect both read \
                and write access with the same permission. Alternatively, declaring a separate \
                `android:writePermission` can protect write access with a different permission.
            """,
        category = Category.SECURITY,
        priority = 5,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation =
          Implementation(ProviderPermissionDetector::class.java, Scope.JAVA_FILE_SCOPE),
      )

    const val KEY_LOCATION = "location"
    const val KEY_IMPL_WRITE_METHODS = "implementedWriteMethods"
  }
}
