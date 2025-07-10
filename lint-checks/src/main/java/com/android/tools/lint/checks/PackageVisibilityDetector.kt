/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.iterator
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.w3c.dom.Element

/**
 * Android 11 introduces new app visibility restrictions: apps must declare extra permissions when
 * they want to inspect other apps on the device. This detector helps increase visibility into the
 * new restrictions.
 */
class PackageVisibilityDetector : Detector(), XmlScanner, SourceCodeScanner {
  private var cachedQueryPermissions: QueryPermissions? = null

  private data class QueryPermissions(
    val canQuerySomePackages: Boolean,
    val canQueryAllPackages: Boolean,
  )

  // ---- Implements XmlScanner ----
  // Checks for usage of the QUERY_ALL_PACKAGES permission (discouraged for most apps).

  override fun getApplicableElements(): Collection<String> = listOf(TAG_USES_PERMISSION)

  override fun visitElement(context: XmlContext, element: Element) {
    val permission = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    if (permission.value == "android.permission.QUERY_ALL_PACKAGES") {
      val incident =
        Incident(
          QUERY_ALL_PACKAGES_PERMISSION,
          context.getLocation(permission),
          """
                A `<queries>` declaration should generally be used instead of QUERY_ALL_PACKAGES; \
                see https://g.co/dev/packagevisibility for details
                """
            .trimIndent(),
        )
      context.report(incident, targetSdkAtLeast(INITIAL_API))
    }
  }

  // ---- Implements SourceCodeScanner ----
  // Checks for usage of APIs requiring app visibility permissions.

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      // PackageManager.
      "getInstalledPackages",
      "getInstalledApplications",
      "queryBroadcastReceivers",
      "queryContentProviders",
      "queryIntentServices",
      "queryIntentActivities",
      // Intent.
      "resolveActivity",
      "resolveActivityInfo",
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.isEnabled(QUERY_PERMISSIONS_NEEDED)) return

    val methodName = node.methodName
    val intendedOwner =
      when (methodName) {
        // PackageManager.
        "getInstalledPackages",
        "getInstalledApplications",
        "queryBroadcastReceivers",
        "queryContentProviders",
        "queryIntentServices",
        "queryIntentActivities" -> "android.content.pm.PackageManager"
        // Intent.
        "resolveActivity",
        "resolveActivityInfo" -> "android.content.Intent"
        else -> error("Unexpected method name: $methodName")
      }
    if (!context.evaluator.isMemberInSubClassOf(method, intendedOwner)) return

    if (methodName == "getInstalledPackages" || methodName == "getInstalledApplications") {
      // Special case: these methods generally imply the ability to query *all* packages.
      val incident =
        Incident(
          QUERY_PERMISSIONS_NEEDED,
          node.methodIdentifier ?: node,
          context.getLocation(node.methodIdentifier ?: node),
          """
                As of Android 11, this method no longer returns information about all apps; \
                see https://g.co/dev/packagevisibility for details
                """
            .trimIndent(),
        )
      context.report(incident, map().put(KEY_REQ_QUERY_ALL, true))
    } else {
      val incident =
        Incident(
          QUERY_PERMISSIONS_NEEDED,
          node.methodIdentifier ?: node,
          context.getLocation(node.methodIdentifier ?: node),
          """
                Consider adding a `<queries>` declaration to your manifest when calling this \
                method; see https://g.co/dev/packagevisibility for details
                """
            .trimIndent(),
        )
      context.report(incident, map().put(KEY_REQ_QUERY_ALL, false))
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    if (context.mainProject.targetSdk >= INITIAL_API) {
      val requirePermissions = map.getBoolean(KEY_REQ_QUERY_ALL, null) ?: return true
      val permissions = getQueryPermissions(context) ?: return false
      return !(if (requirePermissions) permissions.canQueryAllPackages
      else permissions.canQuerySomePackages)
    }
    return false
  }

  private fun getQueryPermissions(context: Context): QueryPermissions? {
    cachedQueryPermissions?.let {
      return it
    }

    val manifest = context.mainProject.mergedManifest ?: return null
    var canQuerySomePackages = false
    var canQueryAllPackages = false

    for (tag in manifest.documentElement) {
      when (tag.nodeName) {
        "queries" -> {
          canQuerySomePackages = true
        }
        TAG_USES_PERMISSION -> {
          val permission = tag.getAttributeNodeNS(ANDROID_URI, ATTR_NAME)
          if (permission?.value == "android.permission.QUERY_ALL_PACKAGES") {
            canQuerySomePackages = true
            canQueryAllPackages = true
          }
        }
      }
    }

    return QueryPermissions(canQuerySomePackages, canQueryAllPackages).also {
      cachedQueryPermissions = it
    }
  }

  companion object {
    /** The API version in which package visibility restrictions were introduced. */
    private const val INITIAL_API = AndroidVersion.VersionCodes.R

    private const val KEY_REQ_QUERY_ALL = "queryAll"

    @JvmField
    val QUERY_ALL_PACKAGES_PERMISSION =
      Issue.create(
        id = "QueryAllPackagesPermission",
        briefDescription = "Using the QUERY_ALL_PACKAGES permission",
        explanation =
          """
            If you need to query or interact with other installed apps, you should be using a \
            `<queries>` declaration in your manifest. Using the QUERY_ALL_PACKAGES permission in \
            order to see all installed apps is rarely necessary, and most apps on Google Play are \
            not allowed to have this permission.
            """,
        category = Category.COMPLIANCE,
        priority = 8,
        severity = Severity.ERROR,
        implementation =
          Implementation(PackageVisibilityDetector::class.java, Scope.MANIFEST_SCOPE),
        androidSpecific = true,
        moreInfo = "https://g.co/dev/packagevisibility",
      )

    @JvmField
    val QUERY_PERMISSIONS_NEEDED =
      Issue.create(
        id = "QueryPermissionsNeeded",
        briefDescription = "Using APIs affected by query permissions",
        explanation =
          """
            Apps that target Android 11 cannot query or interact with other installed apps \
            by default. If you need to query or interact with other installed apps, you may need \
            to add a `<queries>` declaration in your manifest.

            As a corollary, the methods `PackageManager#getInstalledPackages` and \
            `PackageManager#getInstalledApplications` will no longer return information about all \
            installed apps. To query specific apps or types of apps, you can use methods like \
            `PackageManager#getPackageInfo` or `PackageManager#queryIntentActivities`.
            """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            PackageVisibilityDetector::class.java,
            EnumSet.of(Scope.JAVA_FILE, Scope.MANIFEST),
            Scope.JAVA_FILE_SCOPE,
          ),
        androidSpecific = true,
        moreInfo = "https://g.co/dev/packagevisibility",
      )
  }
}
