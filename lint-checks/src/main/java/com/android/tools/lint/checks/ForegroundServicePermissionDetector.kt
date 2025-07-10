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

import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_FOREGROUND_SERVICE_TYPE
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.subtags
import java.util.EnumSet
import org.w3c.dom.Element

/**
 * Checks each foregroundServiceType has corresponding permissions.
 *
 * For targetSdkVersion >= 34 only, each foregroundServiceType under <service> element requires
 * specific sets of permissions, which must be declared in the manifest file. Otherwise at runtime,
 * when the foreground service starts with a foregroundServiceType which has missing permission, the
 * app will get a SecurityException.
 */
class ForegroundServicePermissionDetector : ResourceXmlDetector(), SourceCodeScanner {
  override fun getApplicableElements(): Collection<String> {
    return listOf(TAG_SERVICE)
  }

  /**
   * "android.permission.FOREGROUND_SERVICE" is an overall permission for the app to run foreground
   * service. Without this permission, app can not run foreground service at any
   * foregroundServiceType. There is no need to check each foregroundServiceType's individual
   * permission if this overall permission is missing.
   */
  private fun hasForegroundServicePermission(element: Element): Boolean {
    val applicationTag = element?.parentNode as? Element ?: return false
    val manifestTag = applicationTag?.parentNode as? Element ?: return false
    for (usesPermissionTag in manifestTag.subtags(TAG_USES_PERMISSION)) {
      val name = usesPermissionTag.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: continue
      if ("android.permission.FOREGROUND_SERVICE" == name) {
        return true
      }
    }
    return false
  }

  /**
   * Each foregroundServiceType requires a combination of permissions. The app must have all
   * permissions from the [allPermissions] list AND (also) at least one permission from the
   * [anyPermission] list.
   *
   * @param context
   * @param element
   * @param type the foregroundServiceType.
   * @param allPermissions the app must have all permissions in this list.
   * @param anyPermission the app must have at least one permission in this list.
   */
  private fun checkPermission(
    context: XmlContext,
    element: Element,
    type: String,
    allPermissions: List<String>,
    anyPermission: List<String>?,
  ) {
    val wantAllPermissions = ArrayList(allPermissions)
    var hasAnyPermission = anyPermission == null

    val applicationTag = element?.parentNode as? Element ?: return
    val manifestTag = applicationTag?.parentNode as? Element ?: return
    for (usesPermissionTag in manifestTag.subtags(TAG_USES_PERMISSION)) {
      val name = usesPermissionTag.getAttributeNS(ANDROID_URI, ATTR_NAME) ?: continue
      if (wantAllPermissions.contains(name)) {
        // Check allPermissions until it is empty.
        wantAllPermissions.remove(name)
      } else if (anyPermission != null && anyPermission.contains(name)) {
        hasAnyPermission = true
      }
    }
    // Empty means allPermissions are satisfied.
    val hasAllPermissions = wantAllPermissions.isEmpty()
    if (!(hasAllPermissions && hasAnyPermission)) {
      // The foregroundServiceType does not have all its permission requirement meet.
      // The app will get a SecurityException at runtime, so it is an ERROR.
      var message =
        "foregroundServiceType:" +
          type +
          " requires permission:" +
          allPermissions +
          if (anyPermission == null) "" else " AND any permission in list:$anyPermission"
      val incident = Incident(ISSUE_PERMISSION, element, context.getLocation(element), message)
      context.report(incident, targetSdkAtLeast(34))
    }
  }

  override fun visitElement(context: XmlContext, element: Element) {
    if (element.tagName != TAG_SERVICE) return
    if (!hasForegroundServicePermission(element)) return

    val types = element.getAttributeNS(ANDROID_URI, ATTR_FOREGROUND_SERVICE_TYPE) ?: return
    // multiple foregroundServiceType can be specified, for example:
    // foregroundServiceType="location|camera|microphone"
    // check every individual type.
    for (type in types.split('|')) {
      when (type) {
        "dataSync" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_DATA_SYNC"),
            null,
          )
        "mediaPlayback" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK"),
            null,
          )
        "phoneCall" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_PHONE_CALL"),
            null,
            // It would be nice to check this, but the dev can alternatively
            // make their app the default dialer app through the ROLE_DIALER
            // role, so declaring this permission is not actually required.
            // listOf("android.permission.MANAGE_OWN_CALLS"),
          )
        "location" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_LOCATION"),
            listOf(
              "android.permission.ACCESS_COARSE_LOCATION",
              "android.permission.ACCESS_FINE_LOCATION",
            ),
          )
        "connectedDevice" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE"),
            null,
            // It would be nice to check this, but the dev can alternatively
            // call UsbManager.requestPermission() at runtime, so declaring one
            // of the permissions below is not actually required.
            /*
            listOf(
              "android.permission.BLUETOOTH_ADVERTISE",
              "android.permission.BLUETOOTH_CONNECT",
              "android.permission.BLUETOOTH_SCAN",
              "android.permission.CHANGE_NETWORK_STATE",
              "android.permission.CHANGE_WIFI_STATE",
              "android.permission.CHANGE_WIFI_MULTICAST_STATE",
              "android.permission.NFC",
              "android.permission.TRANSMIT_IR",
              "android.permission.UWB_RANGING",
            ),
            */
          )
        "mediaProjection" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"),
            null,
          )
        "camera" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_CAMERA"),
            listOf("android.permission.CAMERA", "android.permission.SYSTEM_CAMERA"),
          )
        "microphone" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_MICROPHONE"),
            listOf(
              "android.permission.CAPTURE_AUDIO_HOTWORD",
              "android.permission.CAPTURE_AUDIO_OUTPUT",
              "android.permission.CAPTURE_MEDIA_OUTPUT",
              "android.permission.CAPTURE_TUNER_AUDIO_INPUT",
              "android.permission.CAPTURE_VOICE_COMMUNICATION_OUTPUT",
              "android.permission.RECORD_AUDIO",
            ),
          )
        "health" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_HEALTH"),
            listOf(
              "android.permission.ACTIVITY_RECOGNITION",
              "android.permission.BODY_SENSORS",
              "android.permission.HIGH_SAMPLING_RATE_SENSORS",
            ),
          )
        "remoteMessaging" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING"),
            null,
          )
        "systemExempted" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_SYSTEM_EXEMPTED"),
            listOf("android.permission.SCHEDULE_EXACT_ALARM", "android.permission.USE_EXACT_ALARM"),
          )
        "fileManagement" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_FILE_MANAGEMENT"),
            null,
          )
        "specialUse" ->
          checkPermission(
            context,
            element,
            type,
            listOf("android.permission.FOREGROUND_SERVICE_SPECIAL_USE"),
            null,
          )
        else -> continue
      }
    }
  }

  companion object {
    val IMPLEMENTATION =
      Implementation(
        ForegroundServicePermissionDetector::class.java,
        EnumSet.of(Scope.MANIFEST, Scope.JAVA_FILE),
        Scope.MANIFEST_SCOPE,
        Scope.JAVA_FILE_SCOPE,
      )

    /** Foreground service type related issues */
    val ISSUE_PERMISSION =
      Issue.create(
        id = "ForegroundServicePermission",
        briefDescription = "Missing permissions required by foregroundServiceType",
        explanation =
          """
                For targetSdkVersion >= 34, each `foregroundServiceType` listed in the `<service>` element \
                requires specific sets of permissions to be declared in the manifest. If permissions are \
                missing, then when the foreground service is started with a `foregroundServiceType` that has \
                missing permissions, a `SecurityException` will be thrown.
          """,
        category = Category.CORRECTNESS,
        priority = 5,
        severity = Severity.ERROR, // It is an error, missing permission causes SecurityException.
        implementation = IMPLEMENTATION,
      )
  }
}
