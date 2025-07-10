/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.android.SdkConstants
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.minSdkLessThan
import com.google.common.annotations.VisibleForTesting
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.getQualifiedParentOrThis
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.util.isTypeCast

/**
 * Detector looking for casts on the result of context.getSystemService which are suspect.
 *
 * TODO: As of O we can start looking for the @SystemService annotation on the target interface
 *   class, and the value attribute will map back to the expected constant. This should let us get
 *   rid of the hardcoded lookup table below.
 */
class ServiceCastDetector : Detector(), SourceCodeScanner {
  override fun getApplicableMethodNames(): List<String> = listOf("getSystemService")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val args = node.valueArguments
    if (args.size != 1) {
      return
    }

    val argument = args[0].skipParenthesizedExprDown() as? UReferenceExpression ?: return
    val resolvedServiceConst = argument.resolve() as? PsiField ?: return
    val name = resolvedServiceConst.name

    // Check WIFI_SERVICE context origin
    if (WIFI_SERVICE == name) {
      checkWifiService(context, node)
    }

    val parent = skipParenthesizedExprUp(node.getQualifiedParentOrThis().uastParent)
    if (parent != null && parent.isTypeCast()) {
      val cast = parent as UBinaryExpressionWithType

      // Check cast
      var expectedClass = getExpectedType(name)
      if (expectedClass != null) {
        val castType = cast.type.canonicalText
        if (castType.indexOf('.') == -1) {
          expectedClass = stripPackage(expectedClass)
        }
        if (castType != expectedClass) {
          // It's okay to mix and match
          // android.content.ClipboardManager and android.text.ClipboardManager
          if (isClipboard(castType) && isClipboard(expectedClass)) {
            return
          }

          var actual = stripPackage(castType)
          var expected: String? = stripPackage(expectedClass)
          if (actual == expected && expectedClass.contains(".")) {
            actual = castType
            expected = expectedClass
          }
          val message = "Suspicious cast to `$actual` for a `$name`: expected `$expected`"
          context.report(ISSUE, node, context.getLocation(cast), message)
        }
      }
    }
  }

  /**
   * Checks that the given call to `Context#getSystemService(WIFI_SERVICE)` is using the application
   * context
   */
  private fun checkWifiService(context: JavaContext, call: UCallExpression) {
    val evaluator = context.evaluator
    val qualifier = call.receiver
    val resolvedMethod = call.resolve()
    if (
      resolvedMethod != null &&
        (evaluator.isMemberInSubClassOf(resolvedMethod, SdkConstants.CLASS_ACTIVITY, false) ||
          (evaluator.isMemberInSubClassOf(resolvedMethod, SdkConstants.CLASS_VIEW, false)))
    ) {
      reportWifiServiceLeak(WIFI_MANAGER, context, call)
      return
    }
    if (qualifier == null) {
      // Implicit: check surrounding class
      val currentMethod = call.getParentOfType(UMethod::class.java, true)?.javaPsi
      if (
        currentMethod != null &&
          !evaluator.isMemberInSubClassOf(currentMethod, SdkConstants.CLASS_APPLICATION, true)
      ) {
        reportWifiServiceLeak(WIFI_MANAGER, context, call)
      }
    } else {
      checkContextReference(context, qualifier, call)
    }
  }

  /**
   * Given a reference to a context, check to see if the context is an application context (in which
   * case, return quietly), or known to not be an application context (in which case, report an
   * error), or is of an unknown context type (in which case, report a warning).
   *
   * @param context the lint analysis context
   * @param element the reference to be checked
   * @param call the original getSystemService call to report an error against
   */
  private fun checkContextReference(
    context: JavaContext,
    element: UElement?,
    call: UCallExpression,
  ): Boolean {
    if (element == null) {
      return false
    }
    if (element is UCallExpression) {
      val resolvedMethod = element.resolve()
      if (resolvedMethod != null && GET_APPLICATION_CONTEXT != resolvedMethod.name) {
        reportWifiServiceLeak(WIFI_MANAGER, context, call)
        return true
      }
    } else if (element is UQualifiedReferenceExpression) {
      val resolved = element.resolve()
      if (resolved is PsiMethod && GET_APPLICATION_CONTEXT != element.resolvedName) {
        reportWifiServiceLeak(WIFI_MANAGER, context, call)
        return true
      }
    } else if (element is UReferenceExpression) {
      // Check variable references backwards
      val resolved = element.resolve()
      if (resolved is PsiField) {
        val type = resolved.type
        return checkWifiContextType(context, call, type, true)
      } else if (resolved is PsiParameter) {
        // Parameter: is the parameter type something other than just "Context"
        // or some subclass of Application?
        val type = resolved.type
        return checkWifiContextType(context, call, type, true)
      } else if (resolved is PsiLocalVariable) {
        val type = resolved.type
        if (!type.isValid) {
          return false
        }
        if (checkWifiContextType(context, call, type, false)) {
          return true
        }

        // Walk backwards through assignments to find the most recent initialization
        // of this variable
        val lastAssignment = findLastAssignment(resolved, call)
        if (lastAssignment != null) {
          return checkContextReference(context, lastAssignment, call)
        }
      }
    } else if (element is UParenthesizedExpression) {
      return checkContextReference(context, element.expression, call)
    }

    return false
  }

  /**
   * Given a context type (of a parameter or field), check to see if that type implies that the
   * context is not the application context (for example because it's an Activity rather than a
   * plain context).
   *
   * Returns true if it finds and reports a problem.
   */
  private fun checkWifiContextType(
    context: JavaContext,
    call: UCallExpression,
    type: PsiType,
    flagPlainContext: Boolean,
  ): Boolean {
    val evaluator = context.evaluator
    if (type is PsiClassType) {
      val psiClass = type.resolve()
      if (evaluator.extendsClass(psiClass, SdkConstants.CLASS_APPLICATION, false)) {
        return false
      }
    }
    if (evaluator.typeMatches(type, SdkConstants.CLASS_CONTEXT)) {
      if (flagPlainContext) {
        reportWifiServiceLeak(WIFI_MANAGER_UNCERTAIN, context, call)
        return true
      }
      return false
    }

    reportWifiServiceLeak(WIFI_MANAGER, context, call)
    return true
  }

  private fun reportWifiServiceLeak(issue: Issue, context: JavaContext, call: UCallExpression) {
    if (context.project.minSdk >= 24) {
      // Bug is fixed in Nougat
      return
    }

    var message =
      "The WIFI_SERVICE must be looked up on the " +
        "Application context or memory will leak on devices < Android N. "

    val fix: LintFix
    if (call.receiver != null) {
      val qualifier = call.receiver!!.asSourceString()
      message += "Try changing `$qualifier` to `$qualifier.getApplicationContext()`"
      fix =
        fix()
          .name("Add getApplicationContext()")
          .replace()
          .text(qualifier)
          .with("$qualifier.getApplicationContext()")
          .build()
    } else {
      val qualifier = getMethodName(call)
      message += "Try changing `$qualifier` to `getApplicationContext().$qualifier`"
      fix =
        fix()
          .name("Add getApplicationContext()")
          .replace()
          .text(qualifier)
          .with("getApplicationContext().$qualifier")
          .build()
    }

    val incident = Incident(issue, call, context.getLocation(call), message, fix)
    context.report(incident, minSdkLessThan(24))
  }

  private fun isClipboard(cls: String): Boolean {
    return cls == "android.content.ClipboardManager" || cls == "android.text.ClipboardManager"
  }

  private fun stripPackage(fqcn: String): String = fqcn.substringAfterLast('.')

  companion object {
    val IMPLEMENTATION: Implementation =
      Implementation(ServiceCastDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Invalid cast to a type from the service constant */
    @JvmField
    val ISSUE: Issue =
      Issue.create(
        id = "ServiceCast",
        briefDescription = "Wrong system service casts",
        explanation =
          """
          When you call `Context#getSystemService()`, the result is typically cast to \
          a specific interface. This lint check ensures that the cast is compatible with \
          the expected type of the return value.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    /** Using Wi-Fi manager from the wrong context */
    @JvmField
    val WIFI_MANAGER: Issue =
      Issue.create(
        id = "WifiManagerLeak",
        briefDescription = "WifiManager Leak",
        explanation =
          """
          On versions prior to Android N (24), initializing the `WifiManager` via \
          `Context#getSystemService` can cause a memory leak if the context is not \
          the application context. Change `context.getSystemService(...)` to \
          `context.getApplicationContext().getSystemService(...)`.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    /** Using Wi-Fi manager from the wrong context: unknown Context origin */
    @JvmField
    val WIFI_MANAGER_UNCERTAIN: Issue =
      Issue.create(
        id = "WifiManagerPotentialLeak",
        briefDescription = "WifiManager Potential Leak",
        explanation =
          """
          On versions prior to Android N (24), initializing the `WifiManager` \
          via `Context#getSystemService` can cause a memory leak if the context \
          is not the application context.

          In many cases, it's not obvious from the code where the `Context` is \
          coming from (e.g. it might be a parameter to a method, or a field \
          initialized from various method calls). It's possible that the context \
          being passed in is the application context, but to be on the safe side, \
          you should consider changing `context.getSystemService(...)` to \
          `context.getApplicationContext().getSystemService(...)`.
          """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    private const val GET_APPLICATION_CONTEXT = "getApplicationContext"
    private const val WIFI_SERVICE = "WIFI_SERVICE"

    @VisibleForTesting
    fun getExpectedType(value: String?): String? {
      value ?: return null

      // See ServiceCastDataGenerator for helper code to update this database
      return when (value) {
        "ACCESSIBILITY_SERVICE" -> "android.view.accessibility.AccessibilityManager"
        "ACCOUNT_SERVICE" -> "android.accounts.AccountManager"
        "ACTIVITY_SERVICE" -> "android.app.ActivityManager"
        "ALARM_SERVICE" -> "android.app.AlarmManager"
        "APPWIDGET_SERVICE" -> "android.appwidget.AppWidgetManager"
        "APP_OPS_SERVICE" -> "android.app.AppOpsManager"
        "APP_SEARCH_SERVICE" -> "android.app.appsearch.AppSearchManager"
        "AUDIO_SERVICE" -> "android.media.AudioManager"
        "BATTERY_SERVICE" -> "android.os.BatteryManager"
        "BIOMETRIC_SERVICE" -> "android.hardware.biometrics.BiometricManager"
        "BLOB_STORE_SERVICE" -> "android.app.blob.BlobStoreManager"
        "BLUETOOTH_SERVICE" -> "android.bluetooth.BluetoothManager"
        "CAMERA_SERVICE" -> "android.hardware.camera2.CameraManager"
        "CAPTIONING_SERVICE" -> "android.view.accessibility.CaptioningManager"
        "CARRIER_CONFIG_SERVICE" -> "android.telephony.CarrierConfigManager"
        // also allow @Deprecated android.content.ClipboardManager, see isClipboard
        "CLIPBOARD_SERVICE" -> "android.text.ClipboardManager"
        "COMPANION_DEVICE_SERVICE" -> "android.companion.CompanionDeviceManager"
        "CONNECTIVITY_DIAGNOSTICS_SERVICE" -> "android.net.ConnectivityDiagnosticsManager"
        "CONNECTIVITY_SERVICE" -> "android.net.ConnectivityManager"
        "CONSUMER_IR_SERVICE" -> "android.hardware.ConsumerIrManager"
        "CONTACT_KEYS_SERVICE" -> "android.provider.E2eeContactKeysManager"
        "CREDENTIAL_SERVICE" -> "android.credentials.CredentialManager"
        "CROSS_PROFILE_APPS_SERVICE" -> "android.content.pm.CrossProfileApps"
        "DEVICE_LOCK_SERVICE" -> "android.devicelock.DeviceLockManager"
        "DEVICE_POLICY_SERVICE" -> "android.app.admin.DevicePolicyManager"
        "DISPLAY_HASH_SERVICE" -> "android.view.displayhash.DisplayHashManager"
        "DISPLAY_SERVICE" -> "android.hardware.display.DisplayManager"
        "DOMAIN_VERIFICATION_SERVICE" ->
          "android.content.pm.verify.domain.DomainVerificationManager"
        "DOWNLOAD_SERVICE" -> "android.app.DownloadManager"
        "DROPBOX_SERVICE" -> "android.os.DropBoxManager"
        "EUICC_SERVICE" -> "android.telephony.euicc.EuiccManager"
        "FILE_INTEGRITY_SERVICE" -> "android.security.FileIntegrityManager"
        "FINGERPRINT_SERVICE" -> "android.hardware.fingerprint.FingerprintManager"
        "GAME_SERVICE" -> "android.app.GameManager"
        "GRAMMATICAL_INFLECTION_SERVICE" -> "android.app.GrammaticalInflectionManager"
        "HARDWARE_PROPERTIES_SERVICE" -> "android.os.HardwarePropertiesManager"
        "HEALTHCONNECT_SERVICE" -> "android.health.connect.HealthConnectManager"
        "INPUT_METHOD_SERVICE" -> "android.view.inputmethod.InputMethodManager"
        "INPUT_SERVICE" -> "android.hardware.input.InputManager"
        "IPSEC_SERVICE" -> "android.net.IpSecManager"
        "JOB_SCHEDULER_SERVICE" -> "android.app.job.JobScheduler"
        "KEYGUARD_SERVICE" -> "android.app.KeyguardManager"
        "LAUNCHER_APPS_SERVICE" -> "android.content.pm.LauncherApps"
        "LAYOUT_INFLATER_SERVICE" -> "android.view.LayoutInflater"
        "LOCALE_SERVICE" -> "android.app.LocaleManager"
        "LOCATION_SERVICE" -> "android.location.LocationManager"
        "MEDIA_COMMUNICATION_SERVICE" -> "android.media.MediaCommunicationManager"
        "MEDIA_METRICS_SERVICE" -> "android.media.metrics.MediaMetricsManager"
        "MEDIA_PROJECTION_SERVICE" -> "android.media.projection.MediaProjectionManager"
        "MEDIA_ROUTER_SERVICE" -> "android.media.MediaRouter"
        "MEDIA_SESSION_SERVICE" -> "android.media.session.MediaSessionManager"
        "MIDI_SERVICE" -> "android.media.midi.MidiManager"
        "NETWORK_STATS_SERVICE" -> "android.app.usage.NetworkStatsManager"
        "NFC_SERVICE" -> "android.nfc.NfcManager"
        "NOTIFICATION_SERVICE" -> "android.app.NotificationManager"
        "NSD_SERVICE" -> "android.net.nsd.NsdManager"
        "OVERLAY_SERVICE" -> "android.content.om.OverlayManager"
        "PEOPLE_SERVICE" -> "android.app.people.PeopleManager"
        "PERFORMANCE_HINT_SERVICE" -> "android.os.PerformanceHintManager"
        "PERSISTENT_DATA_BLOCK_SERVICE" ->
          "android.service.persistentdata.PersistentDataBlockManager"
        "POWER_SERVICE" -> "android.os.PowerManager"
        "PRINT_SERVICE" -> "android.print.PrintManager"
        "PROFILING_SERVICE" -> "android.os.ProfilingManager"
        "RESTRICTIONS_SERVICE" -> "android.content.RestrictionsManager"
        "ROLE_SERVICE" -> "android.app.role.RoleManager"
        "SEARCH_SERVICE" -> "android.app.SearchManager"
        "SECURITY_STATE_SERVICE" -> "android.os.SecurityStateManager"
        "SENSOR_SERVICE" -> "android.hardware.SensorManager"
        "SHORTCUT_SERVICE" -> "android.content.pm.ShortcutManager"
        "STATUS_BAR_SERVICE" -> "android.app.StatusBarManager"
        "STORAGE_SERVICE" -> "android.os.storage.StorageManager"
        "STORAGE_STATS_SERVICE" -> "android.app.usage.StorageStatsManager"
        "SYSTEM_HEALTH_SERVICE" -> "android.os.health.SystemHealthManager"
        "TELECOM_SERVICE" -> "android.telecom.TelecomManager"
        "TELEPHONY_IMS_SERVICE" -> "android.telephony.ims.ImsManager"
        "TELEPHONY_SERVICE" -> "android.telephony.TelephonyManager"
        "TELEPHONY_SUBSCRIPTION_SERVICE" -> "android.telephony.SubscriptionManager"
        "TEXT_CLASSIFICATION_SERVICE" -> "android.view.textclassifier.TextClassificationManager"
        "TEXT_SERVICES_MANAGER_SERVICE" -> "android.view.textservice.TextServicesManager"
        "TV_AD_SERVICE" -> "android.media.tv.ad.TvAdManager"
        "TV_INPUT_SERVICE" -> "android.media.tv.TvInputManager"
        "TV_INTERACTIVE_APP_SERVICE" -> "android.media.tv.interactive.TvInteractiveAppManager"
        "UI_MODE_SERVICE" -> "android.app.UiModeManager"
        "USAGE_STATS_SERVICE" -> "android.app.usage.UsageStatsManager"
        "USB_SERVICE" -> "android.hardware.usb.UsbManager"
        "USER_SERVICE" -> "android.os.UserManager"
        "VIBRATOR_MANAGER_SERVICE" -> "android.os.VibratorManager"
        "VIBRATOR_SERVICE" -> "android.os.Vibrator"
        "VIRTUAL_DEVICE_SERVICE" -> "android.companion.virtual.VirtualDeviceManager"
        "VPN_MANAGEMENT_SERVICE" -> "android.net.VpnManager"
        "WALLPAPER_SERVICE" -> "android.app.WallpaperManager"
        "WIFI_AWARE_SERVICE" -> "android.net.wifi.aware.WifiAwareManager"
        "WIFI_P2P_SERVICE" -> "android.net.wifi.p2p.WifiP2pManager"
        "WIFI_RTT_RANGING_SERVICE" -> "android.net.wifi.rtt.WifiRttManager"
        "WIFI_SERVICE" -> "android.net.wifi.WifiManager"
        "WINDOW_SERVICE" -> "android.view.WindowManager"
        else -> null
      }
    }
  }
}
