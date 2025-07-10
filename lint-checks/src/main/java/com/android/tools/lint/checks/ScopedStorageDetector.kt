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
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.VALUE_TRUE
import com.android.sdklib.AndroidVersion.VersionCodes
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.utils.iterator
import org.w3c.dom.Element

/**
 * Helps apps transition to using scoped storage, which is described at
 * https://developer.android.com/preview/privacy/storage#scoped-storage.
 *
 * Warns about WRITE_EXTERNAL_STORAGE, which no longer provides write access to files. Warns about
 * MANAGE_EXTERNAL_STORAGE, which is disallowed for most apps.
 */
class ScopedStorageDetector : Detector(), XmlScanner {
  private var cachedStoragePermissions: StoragePermissions? = null

  private data class StoragePermissions(
    val canManageStorage: Boolean,
    val requestedLegacyStorage: Boolean,
  )

  override fun getApplicableElements() = listOf(TAG_USES_PERMISSION)

  override fun visitElement(context: XmlContext, element: Element) {
    val permission = element.getAttributeNodeNS(ANDROID_URI, ATTR_NAME) ?: return
    val value = permission.value

    // READ_EXTERNAL_STORAGE or WRITE_EXTERNAL_STORAGE: report conditionally on targetSdkVersion
    if (value == WRITE_STORAGE || value == READ_STORAGE) {
      val maxSdk = getMaxSdk(element)
      val incident = Incident(ISSUE, context.getValueLocation(permission), "")
      context.report(
        incident,
        map().put(ATTR_MAX_SDK_VERSION, maxSdk).put(ATTR_READ, value == READ_STORAGE),
      )
    }

    // MANAGE_EXTERNAL_STORAGE.
    if (value == MANAGE_STORAGE) {
      val incident =
        Incident(
          ISSUE,
          context.getValueLocation(permission),
          "The Google Play store has a policy that limits usage of MANAGE_EXTERNAL_STORAGE",
        )
      context.report(incident)
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val maxSdk = map.getInt(ATTR_MAX_SDK_VERSION, Integer.MAX_VALUE) ?: return false
    val sdk = minOf(context.mainProject.targetSdk, maxSdk)
    if (sdk < VersionCodes.Q) {
      return false
    }
    val permissions = getStoragePermissions(context) ?: return false
    if (permissions.canManageStorage) {
      return false
    }
    if (sdk == VersionCodes.Q && permissions.requestedLegacyStorage) {
      return false
    }

    val isRead = map.getBoolean(ATTR_READ) ?: false
    if (sdk >= VersionCodes.TIRAMISU) {
      val name = if (isRead) READ_STORAGE else WRITE_STORAGE
      incident.message =
        "" +
          "${name.substringAfterLast('.')} is deprecated (and is not granted) when targeting Android 13+. " +
          if (isRead)
            "If you need to query or interact with MediaStore or media files on the " +
              "shared storage, you should instead use one or more new storage permissions: " +
              "`READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` or `READ_MEDIA_AUDIO`."
          else
            "If you need to write to shared storage, use the `MediaStore.createWriteRequest` intent."
      incident.fix =
        fix().set(ANDROID_URI, ATTR_MAX_SDK_VERSION, VersionCodes.S_V2.toString()).build()
      return true
    } else if (isRead) {
      return false
    }

    var msg = "WRITE_EXTERNAL_STORAGE no longer provides write access when targeting "
    msg +=
      when {
        permissions.requestedLegacyStorage ->
          "Android 11+, even when using `requestLegacyExternalStorage`"
        sdk == VersionCodes.Q -> "Android 10, unless you use `requestLegacyExternalStorage`"
        else -> "Android 10+"
      }
    incident.message = msg
    return true
  }

  // See https://developer.android.com/guide/topics/manifest/uses-permission-element#maxSdk.
  private fun getMaxSdk(element: Element): Int {
    val maxSdkAttr = element.getAttributeNodeNS(ANDROID_URI, ATTR_MAX_SDK_VERSION)
    return maxSdkAttr?.value?.toIntOrNull() ?: Integer.MAX_VALUE
  }

  private fun getStoragePermissions(context: Context): StoragePermissions? {
    cachedStoragePermissions?.let {
      return it
    }

    val manifest = context.mainProject.mergedManifest ?: return null
    var canManageStorage = false
    var requestedLegacyStorage = false

    for (tag in manifest.documentElement) {
      when (tag.nodeName) {
        TAG_APPLICATION -> {
          val legacy = tag.getAttributeNS(ANDROID_URI, "requestLegacyExternalStorage")
          if (legacy == VALUE_TRUE) {
            requestedLegacyStorage = true
          }
        }
        TAG_USES_PERMISSION -> {
          val permission = tag.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (permission == MANAGE_STORAGE) {
            canManageStorage = true
          }
        }
      }
    }

    return StoragePermissions(canManageStorage, requestedLegacyStorage).also {
      cachedStoragePermissions = it
    }
  }

  companion object {
    const val ATTR_MAX_SDK_VERSION = "maxSdkVersion"
    private const val ATTR_READ = "read"
    private const val READ_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    private const val WRITE_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"
    private const val MANAGE_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

    @JvmField
    val ISSUE =
      Issue.create(
        id = "ScopedStorage",
        briefDescription = "Affected by scoped storage",
        explanation =
          """
                Scoped storage is enforced on Android 10+ (or Android 11+ if using \
                `requestLegacyExternalStorage`). In particular, `WRITE_EXTERNAL_STORAGE` \
                will no longer provide write access to all files; it will provide the \
                equivalent of `READ_EXTERNAL_STORAGE` instead.

                As of Android 13, if you need to query or interact with MediaStore or media \
                files on the shared storage, you should be using instead one or more new \
                storage permissions:
                * `android.permission.READ_MEDIA_IMAGES`
                * `android.permission.READ_MEDIA_VIDEO`
                * `android.permission.READ_MEDIA_AUDIO`

                and then add `maxSdkVersion="33"` to the older permission. \
                See the developer guide for how to do this: https://developer.android.com/about/versions/13/behavior-changes-13#granular-media-permissions

                The `MANAGE_EXTERNAL_STORAGE` permission can be used to manage all files, but \
                it is rarely necessary and most apps on Google Play are not allowed to use it. \
                Most apps should instead migrate to use scoped storage. To modify or delete files, \
                apps should request write access from the user as described at \
                https://goo.gle/android-mediastore-createwriterequest.

                To learn more, read these resources: \
                Play policy: https://goo.gle/policy-storage-help \
                Allowable use cases: https://goo.gle/policy-storage-usecases
            """,
        category = Category.CORRECTNESS,
        priority = 8,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = Implementation(ScopedStorageDetector::class.java, Scope.MANIFEST_SCOPE),
        moreInfo = "https://goo.gle/android-storage-usecases",
      )
  }
}
