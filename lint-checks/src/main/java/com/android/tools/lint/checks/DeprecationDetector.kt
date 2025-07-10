/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.SdkConstants.ABSOLUTE_LAYOUT
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_AUTO_TEXT
import com.android.SdkConstants.ATTR_CAPITALIZE
import com.android.SdkConstants.ATTR_EDITABLE
import com.android.SdkConstants.ATTR_INPUT_METHOD
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_NUMERIC
import com.android.SdkConstants.ATTR_PASSWORD
import com.android.SdkConstants.ATTR_PERMISSION
import com.android.SdkConstants.ATTR_PHONE_NUMBER
import com.android.SdkConstants.ATTR_SINGLE_LINE
import com.android.SdkConstants.CLASS_WATCHFACE_ANDROIDX
import com.android.SdkConstants.CLASS_WATCHFACE_WSL
import com.android.SdkConstants.EDIT_TEXT
import com.android.SdkConstants.PreferenceClasses.CLASS_PREFERENCE
import com.android.SdkConstants.TAG_META_DATA
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.SdkConstants.VALUE_FALSE
import com.android.SdkConstants.VALUE_TRUE
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.XML
import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Scope.Companion.JAVA_FILE_SCOPE
import com.android.tools.lint.detector.api.Scope.Companion.MANIFEST_SCOPE
import com.android.tools.lint.detector.api.Scope.Companion.RESOURCE_FILE_SCOPE
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.minSdkAtLeast
import com.android.tools.lint.detector.api.nameFromSource
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.w3c.dom.Attr
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Check which looks for usage of deprecated tags, attributes, etc. */
class DeprecationDetector : ResourceXmlDetector(), SourceCodeScanner {

  // XML (resource and manifest) checks:

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == LAYOUT || folderType == XML
  }

  override fun visitDocument(context: XmlContext, document: Document) {
    if (context.resourceFolderType == XML) {
      val rootElement = document.documentElement
      val tagName = rootElement.tagName
      if (tagName.startsWith("android.preference.")) {
        context.report(
          ISSUE,
          rootElement,
          context.getNameLocation(rootElement),
          "The `android.preference` library is deprecated, it is " +
            "recommended that you migrate to the AndroidX Preference " +
            "library instead.",
        )
        return
      }
      if (tagName.startsWith("androidx.preference.")) {
        // Qualified androidx preference tags can skip inheritance checking.
        return
      }
      val parser = context.client.getUastParser(context.project)
      val tagClass = parser.evaluator.findClass(rootElement.tagName) ?: return
      if (parser.evaluator.inheritsFrom(tagClass, CLASS_PREFERENCE, false)) {
        context.report(
          ISSUE,
          rootElement,
          context.getNameLocation(rootElement),
          "`$tagName` inherits from `android.preference.Preference` which is " +
            "now deprecated, it is recommended that you migrate to the " +
            "AndroidX Preference library.",
        )
      }
    }
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(ABSOLUTE_LAYOUT, TAG_USES_PERMISSION_SDK_M)
  }

  override fun getApplicableAttributes(): Collection<String> {
    return listOf(
      ATTR_USER_SHARED_ID,
      // TODO: fill_parent is deprecated as of API 8.
      // We could warn about it, but it will probably be very noisy
      // and make people disable the deprecation check; let's focus on
      // some older flags for now
      // "fill_parent",
      ATTR_EDITABLE,
      ATTR_INPUT_METHOD,
      ATTR_AUTO_TEXT,
      ATTR_CAPITALIZE,
      ATTR_NAME,
      ATTR_NUMERIC,
      ATTR_PHONE_NUMBER,
      ATTR_PASSWORD,
      ATTR_PERMISSION,
      ATTR_AUTO_REVOKE_PERMISSIONS,
      // ATTR_SINGLE_LINE is marked deprecated, but (a) it's used a lot everywhere,
      // including in our own apps, and (b) replacing it with the suggested replacement
      // can lead to crashes; see issue 37137344
      // ATTR_ENABLED is marked deprecated in android.R.attr but apparently
      // using the suggested replacement of state_enabled doesn't work, see issue b/36943030
      // These attributes are also deprecated; not yet enabled until we
      // know the API level to apply the deprecation for:
      // "ignored as of ICS (but deprecated earlier)"
      // "fadingEdge",
      // "This attribute is not used by the Android operating system."
      // "restoreNeedsApplication",
      // "This will create a non-standard UI appearance, because the search bar UI is
      // changing to use only icons for its buttons."
      // "searchButtonText",
    )
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val tagName = element.tagName
    var message = "`$tagName` is deprecated"
    if (TAG_USES_PERMISSION_SDK_M == tagName) {
      message += ": Use `$TAG_USES_PERMISSION_SDK_23 instead"
    }
    context.report(ISSUE, element, context.getNameLocation(element), message)
  }

  override fun visitAttribute(context: XmlContext, attribute: Attr) {
    if (ANDROID_URI != attribute.namespaceURI) {
      return
    }
    val name = attribute.localName
    val fix: String
    var minSdk = 1
    when (name) {
      ATTR_USER_SHARED_ID -> {
        if (!attribute.ownerElement.hasAttributeNS(ANDROID_URI, ATTR_SHARED_USER_MAX_SDK_VERSION)) {
          fix =
            "Consider removing `$ATTR_USER_SHARED_ID` for new users by adding " +
              "`android:sharedUserMaxSdkVersion=\"32\"` to your manifest. " +
              "See https://developer.android.com/guide/topics/manifest/manifest-element for details."
          val addFix = fix().set(ANDROID_URI, ATTR_SHARED_USER_MAX_SDK_VERSION, "32").build()
          context.report(ISSUE, attribute, context.getLocation(attribute), fix, addFix)
        }
        return
      }
      ATTR_AUTO_REVOKE_PERMISSIONS -> {
        // Flag added in R previews (March 2020), removed/deprecated in June
        val message =
          "$name has no effect; this flag was only used in preview versions of Android 11"
        context.report(ISSUE, attribute, context.getLocation(attribute), message)
        return
      }
      ATTR_PERMISSION -> {
        if (
          TAG_SERVICE == attribute.ownerElement.tagName &&
            CHOOSER_TARGET_SERVICE_PERM == attribute.value
        ) {
          context.report(
            ISSUE,
            attribute,
            context.getLocation(attribute),
            "ChooserTargetService` is deprecated: Please see $SHARE_API_URL",
            fix().url(SHARE_API_URL).build(),
          )
        }
        return
      }
      ATTR_NAME -> {
        if (TAG_META_DATA == attribute.ownerElement.tagName && APP_ACTIONS == attribute.value) {
          val incident =
            Incident(
              ISSUE,
              attribute,
              context.getLocation(attribute),
              "App actions via actions.xml is deprecated; Please migrate to " +
                "shortcuts.xml. See $APP_ACTIONS_MIGRATION_URL.",
              fix().url(APP_ACTIONS_MIGRATION_URL).build(),
            )
          context.report(incident, targetSdkAtLeast(21))
        }
        return
      }
      ATTR_EDITABLE -> {
        fix =
          if (EDIT_TEXT != attribute.ownerElement.tagName) {
            "Use an `<EditText>` to make it editable"
          } else {
            if (VALUE_TRUE == attribute.value) {
              "`<EditText>` is already editable"
            } else {
              "Use `inputType` instead"
            }
          }
      }
      ATTR_SINGLE_LINE -> {
        fix =
          if (VALUE_FALSE == attribute.value) {
            "False is the default, so just remove the attribute"
          } else {
            "Use `maxLines=\"1\"` instead"
          }
      }
      else -> {
        fix = "Use `inputType` instead"
        // The inputType attribute was introduced in API 3 so don't warn about
        // deprecation if targeting older platforms
        minSdk = 3
      }
    }
    val incident =
      Incident(
        ISSUE,
        attribute,
        context.getLocation(attribute),
        "`${attribute.name}` is deprecated: $fix",
      )
    context.report(incident, minSdkAtLeast(minSdk))
  }

  // Kotlin and Java deprecation checks:

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(FIREBASE_JOB_DISPATCHER_CLASS)
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val url = "https://developer.android.com/topic/libraries/architecture/workmanager/migrating-fb"
    context.report(
      ISSUE,
      node,
      context.getCallLocation(node, includeReceiver = false, includeArguments = false),
      "Job scheduling with `FirebaseJobDispatcher` is deprecated: Use AndroidX `WorkManager` instead",
      fix().url(url).build(),
    )
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf("getInstance")
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (!context.evaluator.isMemberInClass(method, GCM_NETWORK_MANAGER_CLASS)) {
      return
    }
    val url = "https://developer.android.com/topic/libraries/architecture/workmanager/migrating-gcm"
    context.report(
      ISSUE,
      node,
      context.getCallLocation(node, includeReceiver = false, includeArguments = false),
      "Job scheduling with `GcmNetworkManager` is deprecated: Use AndroidX `WorkManager` instead",
      fix().url(url).build(),
    )
  }

  override fun applicableSuperClasses(): List<String> {
    return listOf(CHOOSER_TARGET_SERVICE_CLASS, CLASS_WATCHFACE_ANDROIDX, CLASS_WATCHFACE_WSL)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val location = context.getNameLocation(declaration)
    if (context.evaluator.inheritsFrom(declaration.javaPsi, CHOOSER_TARGET_SERVICE_CLASS)) {
      context.report(
        ISSUE,
        declaration,
        location,
        "`${declaration.nameFromSource}` extends the deprecated `ChooserTargetService`: Use the Share API instead",
        fix().url(SHARE_API_URL).build(),
      )
    } else if (context.evaluator.inheritsFromWatchFaceService(declaration)) {
      context.report(
        ISSUE,
        declaration,
        location,
        "`${declaration.nameFromSource}` extends the deprecated `WatchFaceService`: Use Watch Face Format instead",
        fix().url(WATCH_FACE_FORMAT_URL).build(),
      )
    }
  }

  private fun JavaEvaluator.inheritsFromWatchFaceService(declaration: UClass) =
    inheritsFrom(declaration.javaPsi, CLASS_WATCHFACE_ANDROIDX, true) ||
      inheritsFrom(declaration.javaPsi, CLASS_WATCHFACE_WSL, true)

  companion object {
    @Suppress("SpellCheckingInspection")
    private const val FIREBASE_JOB_DISPATCHER_CLASS =
      "com.firebase.jobdispatcher.FirebaseJobDispatcher"

    private const val GCM_NETWORK_MANAGER_CLASS = "com.google.android.gms.gcm.GcmNetworkManager"

    private const val CHOOSER_TARGET_SERVICE_CLASS = "android.service.chooser.ChooserTargetService"

    private const val CHOOSER_TARGET_SERVICE_PERM = "android.permission.BIND_CHOOSER_TARGET_SERVICE"

    private const val SHARE_API_URL =
      "https://developer.android.com/training/sharing/receive.html?source=studio#providing-direct-share-targets"

    private const val APP_ACTIONS = "com.google.android.actions"

    private const val APP_ACTIONS_MIGRATION_URL =
      "https://developers.google.com/assistant/app/legacy/migration-guide"

    private const val ATTR_USER_SHARED_ID = "sharedUserId"
    private const val ATTR_SHARED_USER_MAX_SDK_VERSION = "sharedUserMaxSdkVersion"
    private const val ATTR_AUTO_REVOKE_PERMISSIONS = "autoRevokePermissions"

    private const val WATCH_FACE_FORMAT_URL = "https://developer.android.com/training/wearables/wff"

    /** Usage of deprecated views or attributes. */
    @JvmField
    val ISSUE =
      create(
        id = "Deprecated",
        briefDescription = "Using deprecated resources",
        explanation =
          """
                Deprecated views, attributes and so on are deprecated because there \
                is a better way to do something. Do it that new way. You've been warned.
                """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.WARNING,
        implementation =
          Implementation(
            DeprecationDetector::class.java,
            EnumSet.of(Scope.MANIFEST, Scope.RESOURCE_FILE, Scope.JAVA_FILE),
            MANIFEST_SCOPE,
            RESOURCE_FILE_SCOPE,
            JAVA_FILE_SCOPE,
          ),
      )
  }
}
