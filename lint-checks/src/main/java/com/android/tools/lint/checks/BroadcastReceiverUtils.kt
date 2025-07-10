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

import com.android.tools.lint.client.api.JavaEvaluator
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findConstruction
import com.android.tools.lint.detector.api.getMethodName
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve

@SuppressWarnings("WrongTerminology")
object BroadcastReceiverUtils {
  @JvmStatic
  fun checkIsProtectedReceiverAndReturnUnprotectedActions(
    filterArg: UExpression,
    node: UCallExpression,
    javaEvaluator: JavaEvaluator,
  ): Pair<Boolean, List<String>> { // isProtected, unprotectedActions
    val constantEvaluator = ConstantEvaluator().allowFieldInitializers()
    val actions = mutableSetOf<String>()

    val filterField = filterArg.tryResolve() as? PsiField
    if (filterField != null && javaEvaluator.isPrivate(filterField)) {
      // Special case for class fields.
      // If a private class field is used for the intent filter, try to evaluate
      // all the calls in the class, gathering the intent filter's actions but exiting
      // if the field escapes the class's scope.
      val clazz = node.getContainingUClass() ?: return Pair(false, listOf())
      val dfa = IntentFilterFieldDataFlowAnalyzer(filterField, javaEvaluator, constantEvaluator)
      clazz.accept(dfa)
      actions.addAll(dfa.actions)
      val isProtected = actions.all(::isProtectedBroadcast) && !dfa.escaped
      val unprotected = actions.filterNot(::isProtectedBroadcast)
      return Pair(isProtected, unprotected)
    }

    val construction = findIntentFilterConstruction(filterArg, node) ?: return Pair(false, listOf())

    val constructorActionArg = construction.getArgumentForParameter(0)
    (constructorActionArg?.let(constantEvaluator::evaluate) as? String)?.let(actions::add)

    val actionCollectorVisitor =
      ActionCollectorVisitor(setOf(construction), node, constantEvaluator)

    val parent = node.getContainingUMethod()
    parent?.accept(actionCollectorVisitor)
    actions.addAll(actionCollectorVisitor.actions)

    val isProtected = actions.all(::isProtectedBroadcast) && !actionCollectorVisitor.escaped
    val unprotectedActionsList = actions.filterNot(::isProtectedBroadcast)
    return Pair(isProtected, unprotectedActionsList)
  }

  /**
   * For the supplied expression (e.g. intent filter argument), attempts to find its construction.
   * This will be an `IntentFilter()` constructor, an `IntentFilter.create()` call, or `null`. It
   * will only be found if the intent filter is constructed within the body of the supplied call
   * expression (as in, it is a local variable defined within the function).
   */
  private fun findIntentFilterConstruction(
    expression: UExpression,
    endAt: UElement,
  ): UCallExpression? {
    return findConstruction("android.content.IntentFilter", expression, endAt)
  }

  private fun isIntentFilterFactoryMethod(method: PsiMethod?) =
    method != null &&
      (method.containingClass?.qualifiedName == "android.content.IntentFilter" &&
        (method.returnType?.canonicalText == "android.content.IntentFilter" ||
          method.isConstructor))

  private fun addActionArg(
    call: UCallExpression,
    evaluator: ConstantEvaluator,
    actions: MutableSet<String>,
  ) {
    val actionArg = call.getArgumentForParameter(0) ?: return
    val action = evaluator.evaluate(actionArg) as? String ?: return
    actions.add(action)
  }

  private class ActionCollectorVisitor(
    start: Collection<UElement>,
    val functionCall: UCallExpression,
    val evaluator: ConstantEvaluator,
  ) : EscapeCheckingDataFlowAnalyzer(start) {
    private var finished = false
    val actions = mutableSetOf<String>()

    override fun argument(call: UCallExpression, reference: UElement) {
      when {
        finished -> return
        // We've reached the registerReceiver*() call in question.
        call == functionCall -> finished = true
        // Suppress escape checking if the intentFilter is passed to a registerReceiver method
        getMethodName(call)!! !in BROADCAST_RECEIVER_METHOD_NAMES -> super.argument(call, reference)
      }
    }

    override fun receiver(call: UCallExpression) {
      if (!finished && getMethodName(call) == "addAction") {
        addActionArg(call, evaluator, actions)
      }
    }
  }

  /**
   * Visits the provided class, accumulating the actions added to `intentFilterField`. Detects if
   * `intentFilterField` escapes the class's scope.
   */
  private class IntentFilterFieldDataFlowAnalyzer(
    val intentFilterField: PsiField,
    val javaEvaluator: JavaEvaluator,
    val constantEvaluator: ConstantEvaluator,
  ) : EscapeCheckingDataFlowAnalyzer(listOfNotNull(intentFilterField.toUElement())) {
    val actions: MutableSet<String> = mutableSetOf()

    override fun returns(expression: UReturnExpression) {
      val method = expression.jumpTarget?.tryResolve() as? PsiMethod
      if (method != null && javaEvaluator.isPrivate(method)) return
      super.returns(expression)
    }

    override fun ignoreArgument(call: UCallExpression, reference: UElement): Boolean {
      val resolved = call.resolve() ?: return super.ignoreArgument(call, reference)
      // We don't care about registerReceiver methods (the overarching logic starts with those)
      if (
        getMethodName(call) == "registerReceiver" &&
          javaEvaluator.isMemberInSubClassOf(resolved, "android.content.Context")
      ) {
        return true
      }
      return super.ignoreArgument(call, reference)
    }

    override fun returnsSelf(call: UCallExpression): Boolean {
      if (getMethodName(call) == "addAction") {
        addActionArg(call, constantEvaluator, actions)
      }
      return super.returnsSelf(call)
    }

    /**
     * If `intentFilterField` can be set via a public method's parameter, then it escapes the class'
     * scope because we don't know what actions may have been added externally before passing in
     * said parameter.
     */
    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (escaped) return super.visitBinaryExpression(node)
      if (
        node.operator == UastBinaryOperator.ASSIGN &&
          node.leftOperand.tryResolve() == intentFilterField
      ) {
        val parameter = node.rightOperand.tryResolve() as? PsiParameter
        val method = parameter?.let { it.declarationScope as? PsiMethod }
        if (
          method?.containingClass == intentFilterField.containingClass &&
            !javaEvaluator.isPrivate(method)
        ) {
          escaped = true
        }
      }

      return super.visitBinaryExpression(node)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (isIntentFilterFieldConstruction(node)) {
        addActionArg(node, constantEvaluator, actions)
      }

      return super.visitCallExpression(node)
    }

    /**
     * detect if the call is the construction of `intentFilterField` itself, e.g. field = new
     * IntentFilter("action")
     */
    private fun isIntentFilterFieldConstruction(node: UCallExpression): Boolean {
      if (!isIntentFilterFactoryMethod(node.resolve())) return false
      val parent =
        if (node.uastParent is UParenthesizedExpression) {
          skipParenthesizedExprUp(node.uastParent as? UExpression)
        } else node.uastParent
      if (parent?.sourcePsi == intentFilterField) return true
      return (parent as? UBinaryExpression)?.let {
        it.operator == UastBinaryOperator.ASSIGN && it.leftOperand.tryResolve() == intentFilterField
      } ?: false
    }
  }

  val BROADCAST_RECEIVER_METHOD_NAMES =
    listOf("registerReceiver", "registerReceiverAsUser", "registerReceiverForAllUsers")

  /**
   * Returns whether the given [actionName] corresponds to a protected broadcast. Protected
   * broadcast strings are defined by <protected-broadcast> entries in the manifest of system-level
   * components or applications. The below list is copied from
   * frameworks/base/core/res/AndroidManifest.xml and
   * packages/services/Telephony/AndroidManifest.xml . It should be periodically updated. This list
   * will likely not be complete, since protected-broadcast entries can be defined elsewhere, but
   * should address most situations.
   *
   * To update this, run BroadcastReceiverUtilsTest.testDbUpToDate (with update in place enabled)
   */
  @SuppressWarnings("WrongTerminology") // external constants: can't rename
  @JvmStatic
  fun isProtectedBroadcast(actionName: String): Boolean {
    return when (actionName) {
      "EventConditionProvider.EVALUATE",
      "NotificationHistoryDatabase.CLEANUP",
      "NotificationManagerService.TIMEOUT",
      "ScheduleConditionProvider.EVALUATE",
      "SnoozeHelper.EVALUATE",
      "action.cne.started",
      "android.accounts.LOGIN_ACCOUNTS_CHANGED",
      "android.accounts.action.ACCOUNT_REMOVED",
      "android.accounts.action.VISIBLE_ACCOUNTS_CHANGED",
      "android.app.action.ACTION_PASSWORD_CHANGED",
      "android.app.action.ACTION_PASSWORD_EXPIRING",
      "android.app.action.ACTION_PASSWORD_FAILED",
      "android.app.action.ACTION_PASSWORD_SUCCEEDED",
      "android.app.action.AFFILIATED_PROFILE_TRANSFER_OWNERSHIP_COMPLETE",
      "android.app.action.APPLICATION_DELEGATION_SCOPES_CHANGED",
      "android.app.action.APP_BLOCK_STATE_CHANGED",
      "android.app.action.AUTOMATIC_ZEN_RULE_STATUS_CHANGED",
      "android.app.action.BUGREPORT_FAILED",
      "android.app.action.BUGREPORT_SHARE",
      "android.app.action.BUGREPORT_SHARING_DECLINED",
      "android.app.action.CHOOSE_PRIVATE_KEY_ALIAS",
      "android.app.action.CLOSE_NOTIFICATION_HANDLER_PANEL",
      "android.app.action.COMPLIANCE_ACKNOWLEDGEMENT_REQUIRED",
      "android.app.action.CONSOLIDATED_NOTIFICATION_POLICY_CHANGED",
      "android.app.action.DEVICE_ADMIN_DISABLED",
      "android.app.action.DEVICE_ADMIN_DISABLE_REQUESTED",
      "android.app.action.DEVICE_ADMIN_ENABLED",
      "android.app.action.DEVICE_OWNER_CHANGED",
      "android.app.action.DEVICE_POLICY_CONSTANTS_CHANGED",
      "android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED",
      "android.app.action.DEVICE_POLICY_RESOURCE_UPDATED",
      "android.app.action.ENTER_CAR_MODE",
      "android.app.action.ENTER_CAR_MODE_PRIORITIZED",
      "android.app.action.ENTER_DESK_MODE",
      "android.app.action.EXIT_CAR_MODE",
      "android.app.action.EXIT_CAR_MODE_PRIORITIZED",
      "android.app.action.EXIT_DESK_MODE",
      "android.app.action.INTERRUPTION_FILTER_CHANGED",
      "android.app.action.INTERRUPTION_FILTER_CHANGED_INTERNAL",
      "android.app.action.KEYGUARD_PRIVATE_NOTIFICATIONS_CHANGED",
      "android.app.action.LOCK_TASK_ENTERING",
      "android.app.action.LOCK_TASK_EXITING",
      "android.app.action.LOST_MODE_LOCATION_UPDATE",
      "android.app.action.MANAGED_PROFILE_PROVISIONED",
      "android.app.action.MANAGED_USER_CREATED",
      "android.app.action.NETWORK_LOGS_AVAILABLE",
      "android.app.action.NEXT_ALARM_CLOCK_CHANGED",
      "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED",
      "android.app.action.NOTIFICATION_CHANNEL_GROUP_BLOCK_STATE_CHANGED",
      "android.app.action.NOTIFICATION_LISTENER_ENABLED_CHANGED",
      "android.app.action.NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED",
      "android.app.action.NOTIFICATION_POLICY_CHANGED",
      "android.app.action.NOTIFY_PENDING_SYSTEM_UPDATE",
      "android.app.action.PROFILE_OWNER_CHANGED",
      "android.app.action.PROVISIONING_COMPLETED",
      "android.app.action.RESET_PROTECTION_POLICY_CHANGED",
      "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
      "android.app.action.SECURITY_LOGS_AVAILABLE",
      "android.app.action.SHOW_DEVICE_MONITORING_DIALOG",
      "android.app.action.SHOW_NEW_USER_DISCLAIMER",
      "android.app.action.STATSD_STARTED",
      "android.app.action.SYSTEM_UPDATE_POLICY_CHANGED",
      "android.app.action.TRANSFER_OWNERSHIP_COMPLETE",
      "android.app.action.USER_ADDED",
      "android.app.action.USER_REMOVED",
      "android.app.action.USER_STARTED",
      "android.app.action.USER_STOPPED",
      "android.app.action.USER_SWITCHED",
      "android.app.admin.action.DEVICE_FINANCING_STATE_CHANGED",
      "android.app.admin.action.DEVICE_POLICY_CHANGED",
      "android.app.admin.action.DEVICE_POLICY_SET_RESULT",
      "android.app.backup.intent.CLEAR",
      "android.app.backup.intent.INIT",
      "android.appwidget.action.APPWIDGET_DELETED",
      "android.appwidget.action.APPWIDGET_DISABLED",
      "android.appwidget.action.APPWIDGET_ENABLED",
      "android.appwidget.action.APPWIDGET_ENABLE_AND_UPDATE",
      "android.appwidget.action.APPWIDGET_HOST_RESTORED",
      "android.appwidget.action.APPWIDGET_RESTORED",
      "android.appwidget.action.APPWIDGET_UPDATE_OPTIONS",
      "android.bluetooth.a2dp-sink.profile.action.AUDIO_CONFIG_CHANGED",
      "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED",
      "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED",
      "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED",
      "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED",
      "android.bluetooth.action.AUTO_ON_STATE_CHANGED",
      "android.bluetooth.action.CSIS_CONNECTION_STATE_CHANGED",
      "android.bluetooth.action.CSIS_DEVICE_AVAILABLE",
      "android.bluetooth.action.CSIS_SET_MEMBER_AVAILABLE",
      "android.bluetooth.action.HAP_CONNECTION_STATE_CHANGED",
      "android.bluetooth.action.LE_AUDIO_ACTIVE_DEVICE_CHANGED",
      "android.bluetooth.action.LE_AUDIO_CONF_CHANGED",
      "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED",
      "android.bluetooth.action.LE_AUDIO_GROUP_NODE_STATUS_CHANGED",
      "android.bluetooth.action.LE_AUDIO_GROUP_STATUS_CHANGED",
      "android.bluetooth.action.TETHERING_STATE_CHANGED",
      "android.bluetooth.adapter.action.BLE_ACL_CONNECTED",
      "android.bluetooth.adapter.action.BLE_ACL_DISCONNECTED",
      "android.bluetooth.adapter.action.BLE_STATE_CHANGED",
      "android.bluetooth.adapter.action.BLUETOOTH_ADDRESS_CHANGED",
      "android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.adapter.action.DISCOVERY_FINISHED",
      "android.bluetooth.adapter.action.DISCOVERY_STARTED",
      "android.bluetooth.adapter.action.LOCAL_NAME_CHANGED",
      "android.bluetooth.adapter.action.SCAN_MODE_CHANGED",
      "android.bluetooth.adapter.action.STATE_CHANGED",
      "android.bluetooth.avrcp-controller.profile.action.BROWSE_CONNECTION_STATE_CHANGED",
      "android.bluetooth.avrcp-controller.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.avrcp-controller.profile.action.FOLDER_LIST",
      "android.bluetooth.avrcp-controller.profile.action.TRACK_EVENT",
      "android.bluetooth.device.action.ACL_CONNECTED",
      "android.bluetooth.device.action.ACL_DISCONNECTED",
      "android.bluetooth.device.action.ACL_DISCONNECT_REQUESTED",
      "android.bluetooth.device.action.ALIAS_CHANGED",
      "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED",
      "android.bluetooth.device.action.BOND_STATE_CHANGED",
      "android.bluetooth.device.action.CLASS_CHANGED",
      "android.bluetooth.device.action.CONNECTION_ACCESS_CANCEL",
      "android.bluetooth.device.action.CONNECTION_ACCESS_REPLY",
      "android.bluetooth.device.action.CONNECTION_ACCESS_REQUEST",
      "android.bluetooth.device.action.FOUND",
      "android.bluetooth.device.action.KEY_MISSING",
      "android.bluetooth.device.action.MAS_INSTANCE",
      "android.bluetooth.device.action.NAME_CHANGED",
      "android.bluetooth.device.action.NAME_FAILED",
      "android.bluetooth.device.action.PAIRING_CANCEL",
      "android.bluetooth.device.action.PAIRING_REQUEST",
      "android.bluetooth.device.action.REMOTE_ISSUE_OCCURRED",
      "android.bluetooth.device.action.SDP_RECORD",
      "android.bluetooth.device.action.UUID",
      "android.bluetooth.devicepicker.action.DEVICE_SELECTED",
      "android.bluetooth.devicepicker.action.LAUNCH",
      "android.bluetooth.headset.action.HF_INDICATORS_VALUE_CHANGED",
      "android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT",
      "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED",
      "android.bluetooth.headset.profile.action.AUDIO_STATE_CHANGED",
      "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.headsetclient.profile.action.AG_CALL_CHANGED",
      "android.bluetooth.headsetclient.profile.action.AG_EVENT",
      "android.bluetooth.headsetclient.profile.action.AUDIO_STATE_CHANGED",
      "android.bluetooth.headsetclient.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.headsetclient.profile.action.LAST_VTAG",
      "android.bluetooth.headsetclient.profile.action.NETWORK_SERVICE_STATE_CHANGED",
      "android.bluetooth.headsetclient.profile.action.RESULT",
      "android.bluetooth.hearingaid.profile.action.ACTIVE_DEVICE_CHANGED",
      "android.bluetooth.hearingaid.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.hearingaid.profile.action.PLAYING_STATE_CHANGED",
      "android.bluetooth.hiddevice.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.input.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.input.profile.action.HANDSHAKE",
      "android.bluetooth.input.profile.action.IDLE_TIME_CHANGED",
      "android.bluetooth.input.profile.action.PROTOCOL_MODE_CHANGED",
      "android.bluetooth.input.profile.action.REPORT",
      "android.bluetooth.input.profile.action.VIRTUAL_UNPLUG_STATUS",
      "android.bluetooth.intent.DISCOVERABLE_TIMEOUT",
      "android.bluetooth.map.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.mapmce.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.mapmce.profile.action.MESSAGE_DELETED_STATUS_CHANGED",
      "android.bluetooth.mapmce.profile.action.MESSAGE_DELIVERED_SUCCESSFULLY",
      "android.bluetooth.mapmce.profile.action.MESSAGE_READ_STATUS_CHANGED",
      "android.bluetooth.mapmce.profile.action.MESSAGE_RECEIVED",
      "android.bluetooth.mapmce.profile.action.MESSAGE_SENT_SUCCESSFULLY",
      "android.bluetooth.pan.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.pbap.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.pbapclient.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.sap.profile.action.CONNECTION_STATE_CHANGED",
      "android.bluetooth.volume-control.profile.action.CONNECTION_STATE_CHANGED",
      "android.btopp.intent.action.ACCEPT",
      "android.btopp.intent.action.CONFIRM",
      "android.btopp.intent.action.DECLINE",
      "android.btopp.intent.action.HIDE",
      "android.btopp.intent.action.HIDE_COMPLETE",
      "android.btopp.intent.action.INCOMING_FILE_NOTIFICATION",
      "android.btopp.intent.action.LIST",
      "android.btopp.intent.action.OPEN",
      "android.btopp.intent.action.OPEN_INBOUND",
      "android.btopp.intent.action.OPEN_OUTBOUND",
      "android.btopp.intent.action.RETRY",
      "android.btopp.intent.action.STOP_HANDOVER_TRANSFER",
      "android.btopp.intent.action.TRANSFER_COMPLETE",
      "android.btopp.intent.action.USER_CONFIRMATION_TIMEOUT",
      "android.btopp.intent.action.WHITELIST_DEVICE",
      "android.companion.virtual.action.VIRTUAL_DEVICE_REMOVED",
      "android.content.action.PERMISSION_RESPONSE_RECEIVED",
      "android.content.action.REQUEST_PERMISSION",
      "android.content.jobscheduler.JOB_DEADLINE_EXPIRED",
      "android.content.jobscheduler.JOB_DELAY_EXPIRED",
      "android.content.pm.action.SESSION_COMMITTED",
      "android.content.pm.action.SESSION_UPDATED",
      "android.content.syncmanager.SYNC_ALARM",
      "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED",
      "android.hardware.hdmi.action.ON_ACTIVE_SOURCE_RECOVERED_DISMISS_UI",
      "android.hardware.hdmi.action.OSD_MESSAGE",
      "android.hardware.usb.action.USB_ACCESSORY_ATTACHED",
      "android.hardware.usb.action.USB_ACCESSORY_DETACHED",
      "android.hardware.usb.action.USB_ACCESSORY_HANDSHAKE",
      "android.hardware.usb.action.USB_DEVICE_ATTACHED",
      "android.hardware.usb.action.USB_DEVICE_DETACHED",
      "android.hardware.usb.action.USB_PORT_CHANGED",
      "android.hardware.usb.action.USB_PORT_COMPLIANCE_CHANGED",
      "android.hardware.usb.action.USB_STATE",
      "android.intent.action.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED",
      "android.intent.action.ACTION_DEFAULT_VOICE_SUBSCRIPTION_CHANGED",
      "android.intent.action.ACTION_IDLE_MAINTENANCE_END",
      "android.intent.action.ACTION_IDLE_MAINTENANCE_START",
      "android.intent.action.ACTION_MANAGED_ROAMING_IND",
      "android.intent.action.ACTION_MDN_STATE_CHANGED",
      "android.intent.action.ACTION_POWER_CONNECTED",
      "android.intent.action.ACTION_POWER_DISCONNECTED",
      "android.intent.action.ACTION_PREFERRED_ACTIVITY_CHANGED",
      "android.intent.action.ACTION_SET_RADIO_CAPABILITY_DONE",
      "android.intent.action.ACTION_SET_RADIO_CAPABILITY_FAILED",
      "android.intent.action.ACTION_SHUTDOWN",
      "android.intent.action.ACTION_UNSOL_RESPONSE_OEM_HOOK_RAW",
      "android.intent.action.ADVANCED_SETTINGS",
      "android.intent.action.AIRPLANE_MODE",
      "android.intent.action.ANR",
      "android.intent.action.ANY_DATA_STATE",
      "android.intent.action.APPLICATION_LOCALE_CHANGED",
      "android.intent.action.APPLICATION_RESTRICTIONS_CHANGED",
      "android.intent.action.BATTERY_CHANGED",
      "android.intent.action.BATTERY_LEVEL_CHANGED",
      "android.intent.action.BATTERY_LOW",
      "android.intent.action.BATTERY_OKAY",
      "android.intent.action.BOOT_COMPLETED",
      "android.intent.action.CALL",
      "android.intent.action.CALL_PRIVILEGED",
      "android.intent.action.CANCEL_ENABLE_ROLLBACK",
      "android.intent.action.CHARGING",
      "android.intent.action.CONFIGURATION_CHANGED",
      "android.intent.action.CONTENT_CHANGED",
      "android.intent.action.DATA_SMS_RECEIVED",
      "android.intent.action.DATA_STALL_DETECTED",
      "android.intent.action.DATE_CHANGED",
      "android.intent.action.DEVICE_CUSTOMIZATION_READY",
      "android.intent.action.DEVICE_LOCKED_CHANGED",
      "android.intent.action.DEVICE_STORAGE_FULL",
      "android.intent.action.DEVICE_STORAGE_LOW",
      "android.intent.action.DEVICE_STORAGE_NOT_FULL",
      "android.intent.action.DEVICE_STORAGE_OK",
      "android.intent.action.DISCHARGING",
      "android.intent.action.DISTRACTING_PACKAGES_CHANGED",
      "android.intent.action.DOCK_ACTIVE",
      "android.intent.action.DOCK_EVENT",
      "android.intent.action.DOCK_IDLE",
      "android.intent.action.DOMAINS_NEED_VERIFICATION",
      "android.intent.action.DREAMING_STARTED",
      "android.intent.action.DREAMING_STOPPED",
      "android.intent.action.DROPBOX_ENTRY_ADDED",
      "android.intent.action.DYNAMIC_SENSOR_CHANGED",
      "android.intent.action.EMERGENCY_CALLBACK_MODE_CHANGED",
      "android.intent.action.EMERGENCY_CALL_STATE_CHANGED",
      "android.intent.action.EXTERNAL_APPLICATIONS_AVAILABLE",
      "android.intent.action.EXTERNAL_APPLICATIONS_UNAVAILABLE",
      "android.intent.action.GET_RESTRICTION_ENTRIES",
      "android.intent.action.GLOBAL_BUTTON",
      "android.intent.action.HDMI_PLUGGED",
      "android.intent.action.HEADSET_PLUG",
      "android.intent.action.INCIDENT_REPORT_READY",
      "android.intent.action.INPUT_METHOD_CHANGED",
      "android.intent.action.INTENT_FILTER_NEEDS_VERIFICATION",
      "android.intent.action.LOCALE_CHANGED",
      "android.intent.action.LOCKED_BOOT_COMPLETED",
      "android.intent.action.MAIN_USER_LOCKSCREEN_KNOWLEDGE_FACTOR_CHANGED",
      "android.intent.action.MANAGED_PROFILE_ADDED",
      "android.intent.action.MANAGED_PROFILE_AVAILABLE",
      "android.intent.action.MANAGED_PROFILE_REMOVED",
      "android.intent.action.MANAGED_PROFILE_UNAVAILABLE",
      "android.intent.action.MANAGED_PROFILE_UNLOCKED",
      "android.intent.action.MASTER_CLEAR_NOTIFICATION",
      "android.intent.action.MEDIA_BAD_REMOVAL",
      "android.intent.action.MEDIA_CHECKING",
      "android.intent.action.MEDIA_EJECT",
      "android.intent.action.MEDIA_MOUNTED",
      "android.intent.action.MEDIA_NOFS",
      "android.intent.action.MEDIA_REMOVED",
      "android.intent.action.MEDIA_RESOURCE_GRANTED",
      "android.intent.action.MEDIA_SHARED",
      "android.intent.action.MEDIA_UNMOUNTABLE",
      "android.intent.action.MEDIA_UNMOUNTED",
      "android.intent.action.MEDIA_UNSHARED",
      "android.intent.action.MY_PACKAGE_REPLACED",
      "android.intent.action.MY_PACKAGE_SUSPENDED",
      "android.intent.action.MY_PACKAGE_UNSUSPENDED",
      "android.intent.action.NEW_OUTGOING_CALL",
      "android.intent.action.OVERLAY_ADDED",
      "android.intent.action.OVERLAY_CHANGED",
      "android.intent.action.OVERLAY_PRIORITY_CHANGED",
      "android.intent.action.OVERLAY_REMOVED",
      "android.intent.action.PACKAGES_SUSPENDED",
      "android.intent.action.PACKAGES_SUSPENSION_CHANGED",
      "android.intent.action.PACKAGES_UNSUSPENDED",
      "android.intent.action.PACKAGE_ADDED",
      "android.intent.action.PACKAGE_CHANGED",
      "android.intent.action.PACKAGE_DATA_CLEARED",
      "android.intent.action.PACKAGE_ENABLE_ROLLBACK",
      "android.intent.action.PACKAGE_FIRST_LAUNCH",
      "android.intent.action.PACKAGE_FULLY_LOADED",
      "android.intent.action.PACKAGE_FULLY_REMOVED",
      "android.intent.action.PACKAGE_INSTALL",
      "android.intent.action.PACKAGE_NEEDS_INTEGRITY_VERIFICATION",
      "android.intent.action.PACKAGE_NEEDS_VERIFICATION",
      "android.intent.action.PACKAGE_REMOVED",
      "android.intent.action.PACKAGE_REMOVED_INTERNAL",
      "android.intent.action.PACKAGE_REPLACED",
      "android.intent.action.PACKAGE_RESTARTED",
      "android.intent.action.PACKAGE_UNSTOPPED",
      "android.intent.action.PACKAGE_UNSUSPENDED_MANUALLY",
      "android.intent.action.PACKAGE_VERIFIED",
      "android.intent.action.PENDING_INCIDENT_REPORTS_CHANGED",
      "android.intent.action.PHONE_STATE",
      "android.intent.action.PRECISE_CALL_STATE",
      "android.intent.action.PRE_BOOT_COMPLETED",
      "android.intent.action.PROFILE_ACCESSIBLE",
      "android.intent.action.PROFILE_ADDED",
      "android.intent.action.PROFILE_AVAILABLE",
      "android.intent.action.PROFILE_INACCESSIBLE",
      "android.intent.action.PROFILE_REMOVED",
      "android.intent.action.PROFILE_UNAVAILABLE",
      "android.intent.action.PROXY_CHANGE",
      "android.intent.action.QUERY_PACKAGE_RESTART",
      "android.intent.action.RADIO_TECHNOLOGY",
      "android.intent.action.REBOOT",
      "android.intent.action.ROLLBACK_COMMITTED",
      "android.intent.action.SCREEN_OFF",
      "android.intent.action.SCREEN_ON",
      "android.intent.action.SERVICE_STATE",
      "android.intent.action.SHOW_FOREGROUND_SERVICE_MANAGER",
      "android.intent.action.SIG_STR",
      "android.intent.action.SIM_STATE_CHANGED",
      "android.intent.action.SPLIT_CONFIGURATION_CHANGED",
      "android.intent.action.SUBSCRIPTION_INFO_RECORD_ADDED",
      "android.intent.action.SUBSCRIPTION_PHONE_STATE",
      "android.intent.action.SUB_DEFAULT_CHANGED",
      "android.intent.action.THERMAL_EVENT",
      "android.intent.action.TIMEZONE_CHANGED",
      "android.intent.action.TIME_SET",
      "android.intent.action.TIME_TICK",
      "android.intent.action.TWILIGHT_CHANGED",
      "android.intent.action.UID_REMOVED",
      "android.intent.action.UNARCHIVE_PACKAGE",
      "android.intent.action.USER_ACTIVITY_NOTIFICATION",
      "android.intent.action.USER_ADDED",
      "android.intent.action.USER_BACKGROUND",
      "android.intent.action.USER_FOREGROUND",
      "android.intent.action.USER_INFO_CHANGED",
      "android.intent.action.USER_INITIALIZE",
      "android.intent.action.USER_PRESENT",
      "android.intent.action.USER_REMOVED",
      "android.intent.action.USER_STARTED",
      "android.intent.action.USER_STARTING",
      "android.intent.action.USER_STOPPED",
      "android.intent.action.USER_STOPPING",
      "android.intent.action.USER_SWITCHED",
      "android.intent.action.USER_UNLOCKED",
      "android.intent.action.WALLPAPER_CHANGED",
      "android.intent.action.internal_sim_state_changed",
      "android.internal.policy.action.BURN_IN_PROTECTION",
      "android.location.MODE_CHANGED",
      "android.location.PROVIDERS_CHANGED",
      "android.location.action.GNSS_CAPABILITIES_CHANGED",
      "android.media.ACTION_SCO_AUDIO_STATE_UPDATED",
      "android.media.AUDIO_BECOMING_NOISY",
      "android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION",
      "android.media.MASTER_BALANCE_CHANGED_ACTION",
      "android.media.MASTER_MONO_CHANGED_ACTION",
      "android.media.MASTER_MUTE_CHANGED_ACTION",
      "android.media.MASTER_VOLUME_CHANGED_ACTION",
      "android.media.RINGER_MODE_CHANGED",
      "android.media.SCO_AUDIO_STATE_CHANGED",
      "android.media.STREAM_DEVICES_CHANGED_ACTION",
      "android.media.STREAM_MUTE_CHANGED_ACTION",
      "android.media.VIBRATE_SETTING_CHANGED",
      "android.media.VOLUME_CHANGED_ACTION",
      "android.media.action.HDMI_AUDIO_PLUG",
      "android.media.action.MICROPHONE_MUTE_CHANGED",
      "android.media.action.SPEAKERPHONE_STATE_CHANGED",
      "android.media.tv.action.CHANNEL_BROWSABLE_REQUESTED",
      "android.media.tv.action.PARENTAL_CONTROLS_ENABLED_CHANGED",
      "android.media.tv.action.PREVIEW_PROGRAM_ADDED_TO_WATCH_NEXT",
      "android.media.tv.action.PREVIEW_PROGRAM_BROWSABLE_DISABLED",
      "android.media.tv.action.WATCH_NEXT_PROGRAM_BROWSABLE_DISABLED",
      "android.net.ConnectivityService.action.PKT_CNT_SAMPLE_INTERVAL_ELAPSED",
      "android.net.action.CLEAR_DNS_CACHE",
      "android.net.conn.BACKGROUND_DATA_SETTING_CHANGED",
      "android.net.conn.CAPTIVE_PORTAL",
      "android.net.conn.CAPTIVE_PORTAL_TEST_COMPLETED",
      "android.net.conn.CONNECTIVITY_CHANGE",
      "android.net.conn.CONNECTIVITY_CHANGE_IMMEDIATE",
      "android.net.conn.CONNECTIVITY_CHANGE_SUPL",
      "android.net.conn.DATA_ACTIVITY_CHANGE",
      "android.net.conn.INET_CONDITION_ACTION",
      "android.net.conn.NETWORK_CONDITIONS_MEASURED",
      "android.net.conn.RESTRICT_BACKGROUND_CHANGED",
      "android.net.conn.TETHER_STATE_CHANGED",
      "android.net.nsd.STATE_CHANGED",
      "android.net.proxy.PAC_REFRESH",
      "android.net.scoring.SCORER_CHANGED",
      "android.net.scoring.SCORE_NETWORKS",
      "android.net.sip.SIP_SERVICE_UP",
      "android.net.sip.action.SIP_CALL_OPTION_CHANGED",
      "android.net.sip.action.SIP_INCOMING_CALL",
      "android.net.sip.action.SIP_REMOVE_PROFILE",
      "android.net.sip.action.SIP_SERVICE_UP",
      "android.net.sip.action.START_SIP",
      "android.net.wifi.CONFIGURED_NETWORKS_CHANGE",
      "android.net.wifi.LINK_CONFIGURATION_CHANGED",
      "android.net.wifi.PASSPOINT_ICON_RECEIVED",
      "android.net.wifi.RSSI_CHANGED",
      "android.net.wifi.SCAN_RESULTS",
      "android.net.wifi.STATE_CHANGE",
      "android.net.wifi.WIFI_AP_STATE_CHANGED",
      "android.net.wifi.WIFI_CREDENTIAL_CHANGED",
      "android.net.wifi.WIFI_STATE_CHANGED",
      "android.net.wifi.action.NETWORK_SETTINGS_RESET",
      "android.net.wifi.action.PASSPOINT_DEAUTH_IMMINENT",
      "android.net.wifi.action.PASSPOINT_ICON",
      "android.net.wifi.action.PASSPOINT_LAUNCH_OSU_VIEW",
      "android.net.wifi.action.PASSPOINT_OSU_PROVIDERS_LIST",
      "android.net.wifi.action.PASSPOINT_SUBSCRIPTION_REMEDIATION",
      "android.net.wifi.action.REFRESH_USER_PROVISIONING",
      "android.net.wifi.action.WIFI_NETWORK_SUGGESTION_POST_CONNECTION",
      "android.net.wifi.action.WIFI_SCAN_AVAILABILITY_CHANGED",
      "android.net.wifi.aware.action.WIFI_AWARE_RESOURCE_CHANGED",
      "android.net.wifi.aware.action.WIFI_AWARE_STATE_CHANGED",
      "android.net.wifi.p2p.CONNECTION_STATE_CHANGE",
      "android.net.wifi.p2p.DISCOVERY_STATE_CHANGE",
      "android.net.wifi.p2p.PEERS_CHANGED",
      "android.net.wifi.p2p.STATE_CHANGED",
      "android.net.wifi.p2p.THIS_DEVICE_CHANGED",
      "android.net.wifi.p2p.action.WIFI_P2P_PERSISTENT_GROUPS_CHANGED",
      "android.net.wifi.rtt.action.WIFI_RTT_STATE_CHANGED",
      "android.net.wifi.supplicant.CONNECTION_CHANGE",
      "android.net.wifi.supplicant.STATE_CHANGE",
      "android.nfc.action.ADAPTER_STATE_CHANGED",
      "android.nfc.action.PREFERRED_PAYMENT_CHANGED",
      "android.nfc.action.REQUIRE_UNLOCK_FOR_NFC",
      "android.nfc.action.TRANSACTION_DETECTED",
      "android.nfc.handover.intent.action.HANDOVER_SEND",
      "android.nfc.handover.intent.action.HANDOVER_SEND_MULTIPLE",
      "android.nfc.handover.intent.action.HANDOVER_STARTED",
      "android.nfc.handover.intent.action.TRANSFER_DONE",
      "android.nfc.handover.intent.action.TRANSFER_PROGRESS",
      "android.os.UpdateLock.UPDATE_LOCK_CHANGED",
      "android.os.action.ACTION_EFFECTS_SUPPRESSOR_CHANGED",
      "android.os.action.CHARGING",
      "android.os.action.DEVICE_IDLE_MODE_CHANGED",
      "android.os.action.DISCHARGING",
      "android.os.action.ENHANCED_DISCHARGE_PREDICTION_CHANGED",
      "android.os.action.LIGHT_DEVICE_IDLE_MODE_CHANGED",
      "android.os.action.LOW_POWER_STANDBY_ENABLED_CHANGED",
      "android.os.action.LOW_POWER_STANDBY_POLICY_CHANGED",
      "android.os.action.LOW_POWER_STANDBY_PORTS_CHANGED",
      "android.os.action.POWER_SAVE_MODE_CHANGED",
      "android.os.action.POWER_SAVE_MODE_CHANGED_INTERNAL",
      "android.os.action.POWER_SAVE_TEMP_WHITELIST_CHANGED",
      "android.os.action.POWER_SAVE_WHITELIST_CHANGED",
      "android.os.action.SCREEN_BRIGHTNESS_BOOST_CHANGED",
      "android.os.action.SETTING_RESTORED",
      "android.os.action.USER_RESTRICTIONS_CHANGED",
      "android.os.storage.action.DISK_SCANNED",
      "android.os.storage.action.VOLUME_STATE_CHANGED",
      "android.permission.CLEAR_APP_GRANTED_URI_PERMISSIONS",
      "android.permission.GET_APP_GRANTED_URI_PERMISSIONS",
      "android.provider.Telephony.MMS_DOWNLOADED",
      "android.provider.Telephony.SECRET_CODE",
      "android.provider.Telephony.SIM_FULL",
      "android.provider.Telephony.SMS_CB_RECEIVED",
      "android.provider.Telephony.SMS_DELIVER",
      "android.provider.Telephony.SMS_RECEIVED",
      "android.provider.Telephony.SMS_REJECTED",
      "android.provider.Telephony.SMS_SERVICE_CATEGORY_PROGRAM_DATA_RECEIVED",
      "android.provider.Telephony.WAP_PUSH_DELIVER",
      "android.provider.Telephony.WAP_PUSH_RECEIVED",
      "android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED",
      "android.provider.action.DEFAULT_SMS_PACKAGE_CHANGED_INTERNAL",
      "android.provider.action.SMS_EMERGENCY_CB_RECEIVED",
      "android.provider.action.SMS_MMS_DB_CREATED",
      "android.provider.action.SMS_MMS_DB_LOST",
      "android.safetycenter.action.REFRESH_SAFETY_SOURCES",
      "android.safetycenter.action.SAFETY_CENTER_ENABLED_CHANGED",
      "android.scheduling.action.REBOOT_READY",
      "android.se.omapi.action.SECURE_ELEMENT_STATE_CHANGED",
      "android.search.action.SEARCHABLES_CHANGED",
      "android.security.STORAGE_CHANGED",
      "android.security.action.KEYCHAIN_CHANGED",
      "android.security.action.KEY_ACCESS_CHANGED",
      "android.security.action.TRUST_STORE_CHANGED",
      "android.server.notification.action.DISABLE_NAS",
      "android.server.notification.action.ENABLE_NAS",
      "android.server.notification.action.LEARNMORE_NAS",
      "android.service.autofill.action.DELAYED_FILL",
      "android.settings.action.GRAYSCALE_CHANGED",
      "android.telecom.action.CURRENT_TTY_MODE_CHANGED",
      "android.telecom.action.DEFAULT_DIALER_CHANGED",
      "android.telecom.action.NUISANCE_CALL_STATUS_CHANGED",
      "android.telecom.action.PHONE_ACCOUNT_REGISTERED",
      "android.telecom.action.PHONE_ACCOUNT_UNREGISTERED",
      "android.telecom.action.POST_CALL",
      "android.telecom.action.SHOW_MISSED_CALLS_NOTIFICATION",
      "android.telephony.action.ANOMALY_REPORTED",
      "android.telephony.action.CARRIER_CONFIG_CHANGED",
      "android.telephony.action.CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE",
      "android.telephony.action.CARRIER_SIGNAL_PCO_VALUE",
      "android.telephony.action.CARRIER_SIGNAL_REDIRECTED",
      "android.telephony.action.CARRIER_SIGNAL_REQUEST_NETWORK_FAILED",
      "android.telephony.action.CARRIER_SIGNAL_RESET",
      "android.telephony.action.DEFAULT_SMS_SUBSCRIPTION_CHANGED",
      "android.telephony.action.DEFAULT_SUBSCRIPTION_CHANGED",
      "android.telephony.action.MULTI_SIM_CONFIG_CHANGED",
      "android.telephony.action.NETWORK_COUNTRY_CHANGED",
      "android.telephony.action.PRIMARY_SUBSCRIPTION_LIST_CHANGED",
      "android.telephony.action.SECRET_CODE",
      "android.telephony.action.SERVICE_PROVIDERS_UPDATED",
      "android.telephony.action.SHOW_NOTICE_ECM_BLOCK_OTHERS",
      "android.telephony.action.SHOW_VOICEMAIL_NOTIFICATION",
      "android.telephony.action.SIM_APPLICATION_STATE_CHANGED",
      "android.telephony.action.SIM_CARD_STATE_CHANGED",
      "android.telephony.action.SIM_SLOT_STATUS_CHANGED",
      "android.telephony.action.SUBSCRIPTION_CARRIER_IDENTITY_CHANGED",
      "android.telephony.action.SUBSCRIPTION_PLANS_CHANGED",
      "android.telephony.action.SUBSCRIPTION_SPECIFIC_CARRIER_IDENTITY_CHANGED",
      "android.telephony.action.TOGGLE_PROVISION",
      "android.telephony.euicc.action.OTA_STATUS_CHANGED",
      "android.telephony.ims.action.RCS_SINGLE_REGISTRATION_CAPABILITY_UPDATE",
      "android.telephony.ims.action.WFC_IMS_REGISTRATION_ERROR",
      "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_DELIVERY",
      "com.android.bluetooth.BluetoothMapContentObserver.action.MESSAGE_SENT",
      "com.android.bluetooth.btservice.action.ALARM_WAKEUP",
      "com.android.bluetooth.gatt.REFRESH_BATCHED_SCAN",
      "com.android.bluetooth.map.USER_CONFIRM_TIMEOUT",
      "com.android.bluetooth.pbap.authcancelled",
      "com.android.bluetooth.pbap.authchall",
      "com.android.bluetooth.pbap.authresponse",
      "com.android.bluetooth.pbap.userconfirmtimeout",
      "com.android.bluetooth.sap.USER_CONFIRM_TIMEOUT",
      "com.android.bluetooth.sap.action.DISCONNECT_ACTION",
      "com.android.cellbroadcastreceiver.GET_LATEST_CB_AREA_INFO",
      "com.android.content.pm.action.CAN_INTERACT_ACROSS_PROFILES_CHANGED",
      "com.android.ims.ACTION_PRESENCE_CHANGED",
      "com.android.ims.ACTION_PUBLISH_STATUS_CHANGED",
      "com.android.ims.ACTION_RCS_SERVICE_AVAILABLE",
      "com.android.ims.ACTION_RCS_SERVICE_DIED",
      "com.android.ims.ACTION_RCS_SERVICE_UNAVAILABLE",
      "com.android.ims.IMS_INCOMING_CALL",
      "com.android.ims.IMS_SERVICE_DOWN",
      "com.android.ims.IMS_SERVICE_UP",
      "com.android.ims.internal.uce.UCE_SERVICE_DOWN",
      "com.android.ims.internal.uce.UCE_SERVICE_UP",
      "com.android.imsconnection.DISCONNECTED",
      "com.android.intent.action.IMS_CONFIG_CHANGED",
      "com.android.intent.action.IMS_FEATURE_CHANGED",
      "com.android.intent.isim_refresh",
      "com.android.internal.action.EUICC_FACTORY_RESET",
      "com.android.internal.action.EUICC_REMOVE_INVISIBLE_SUBSCRIPTIONS",
      "com.android.internal.intent.action.BUGREPORT_REQUESTED",
      "com.android.internal.intent.action.FLASH_NOTIFICATION_START_PREVIEW",
      "com.android.internal.intent.action.FLASH_NOTIFICATION_STOP_PREVIEW",
      "com.android.internal.location.ALARM_TIMEOUT",
      "com.android.internal.location.ALARM_WAKEUP",
      "com.android.internal.provider.action.VOICEMAIL_SMS_RECEIVED",
      "com.android.internal.stk.alpha_notify",
      "com.android.internal.stk.command",
      "com.android.internal.stk.icc_status_change",
      "com.android.internal.stk.session_end",
      "com.android.internal.telephony.ACTION_CARRIER_CERTIFICATE_DOWNLOAD",
      "com.android.internal.telephony.ACTION_LINE1_NUMBER_ERROR_DETECTED",
      "com.android.internal.telephony.ACTION_TEST_OVERRIDE_CARRIER_ID",
      "com.android.internal.telephony.ACTION_VOWIFI_ENABLED",
      "com.android.internal.telephony.CARRIER_SIGNAL_DEFAULT_NETWORK_AVAILABLE",
      "com.android.internal.telephony.CARRIER_SIGNAL_PCO_VALUE",
      "com.android.internal.telephony.CARRIER_SIGNAL_REDIRECTED",
      "com.android.internal.telephony.CARRIER_SIGNAL_REQUEST_NETWORK_FAILED",
      "com.android.internal.telephony.CARRIER_SIGNAL_RESET",
      "com.android.internal.telephony.CARRIER_VVM_PACKAGE_INSTALLED",
      "com.android.internal.telephony.OPEN_DEFAULT_SMS_APP",
      "com.android.internal.telephony.PROVISION",
      "com.android.internal.telephony.action.COUNTRY_OVERRIDE",
      "com.android.internal.telephony.carrier_key_download_alarm",
      "com.android.internal.telephony.cat.SMS_DELIVERY_ACTION",
      "com.android.internal.telephony.cat.SMS_SENT_ACTION",
      "com.android.internal.telephony.data-restart-trysetup",
      "com.android.internal.telephony.data-stall",
      "com.android.internal.telephony.data.ACTION_RETRY",
      "com.android.internal.telephony.provisioning_apn_alarm",
      "com.android.nfc.action.LLCP_DOWN",
      "com.android.nfc.action.LLCP_UP",
      "com.android.nfc.cardemulation.action.CLOSE_TAP_DIALOG",
      "com.android.nfc.handover.action.ALLOW_CONNECT",
      "com.android.nfc.handover.action.CANCEL_HANDOVER_TRANSFER",
      "com.android.nfc.handover.action.DENY_CONNECT",
      "com.android.nfc.handover.action.TIMEOUT_CONNECT",
      "com.android.nfc_extras.action.AID_SELECTED",
      "com.android.nfc_extras.action.RF_FIELD_OFF_DETECTED",
      "com.android.nfc_extras.action.RF_FIELD_ON_DETECTED",
      "com.android.phone.SIP_ADD_PHONE",
      "com.android.phone.settings.CARRIER_PROVISIONING",
      "com.android.phone.settings.TRIGGER_CARRIER_PROVISIONING",
      "com.android.phone.vvm.ACTION_VISUAL_VOICEMAIL_SERVICE_EVENT",
      "com.android.phone.vvm.omtp.sms.REQUEST_SENT",
      "com.android.server.ACTION_EXPIRED_PASSWORD_NOTIFICATION",
      "com.android.server.ACTION_PROFILE_OFF_DEADLINE",
      "com.android.server.ACTION_TRIGGER_IDLE",
      "com.android.server.ACTION_TURN_PROFILE_ON_NOTIFICATION",
      "com.android.server.Wifi.action.TOGGLE_PNO",
      "com.android.server.WifiManager.action.DELAYED_DRIVER_STOP",
      "com.android.server.WifiManager.action.DEVICE_IDLE",
      "com.android.server.WifiManager.action.START_PNO",
      "com.android.server.WifiManager.action.START_SCAN",
      "com.android.server.action.NETWORK_STATS_POLL",
      "com.android.server.action.NETWORK_STATS_UPDATED",
      "com.android.server.action.REMOTE_BUGREPORT_SHARING_ACCEPTED",
      "com.android.server.action.REMOTE_BUGREPORT_SHARING_DECLINED",
      "com.android.server.action.RESET_TWILIGHT_AUTO",
      "com.android.server.action.UPDATE_TWILIGHT_STATE",
      "com.android.server.adb.WIRELESS_DEBUG_PAIRED_DEVICES",
      "com.android.server.adb.WIRELESS_DEBUG_PAIRING_RESULT",
      "com.android.server.adb.WIRELESS_DEBUG_STATUS",
      "com.android.server.am.DELETE_DUMPHEAP",
      "com.android.server.audio.action.CHECK_MUSIC_ACTIVE",
      "com.android.server.biometrics.face.ACTION_LOCKOUT_RESET",
      "com.android.server.biometrics.fingerprint.ACTION_LOCKOUT_RESET",
      "com.android.server.connectivity.tethering.PROVISIONING_RECHECK_ALARM",
      "com.android.server.connectivityservice.CONNECTED_TO_PROVISIONING_NETWORK_ACTION",
      "com.android.server.device_idle.STEP_IDLE_STATE",
      "com.android.server.device_idle.STEP_LIGHT_IDLE_STATE",
      "com.android.server.fingerprint.ACTION_LOCKOUT_RESET",
      "com.android.server.jobscheduler.FORCE_IDLE",
      "com.android.server.jobscheduler.GARAGE_MODE_OFF",
      "com.android.server.jobscheduler.GARAGE_MODE_ON",
      "com.android.server.jobscheduler.UNFORCE_IDLE",
      "com.android.server.net.action.SNOOZE_RAPID",
      "com.android.server.net.action.SNOOZE_WARNING",
      "com.android.server.notification.CountdownConditionProvider",
      "com.android.server.notification.TimeToLiveHelper",
      "com.android.server.pm.DISABLE_QUIET_MODE_AFTER_UNLOCK",
      "com.android.server.retaildemo.ACTION_RESET_DEMO",
      "com.android.server.stats.action.TRIGGER_COLLECTION",
      "com.android.server.telecom.intent.action.CALLS_ADD_ENTRY",
      "com.android.server.timedetector.NetworkTimeUpdateService.action.POLL",
      "com.android.server.usb.ACTION_OPEN_IN_APPS",
      "com.android.server.wifi.ACTION_SHOW_SET_RANDOMIZATION_DETAILS",
      "com.android.server.wifi.ConnectToNetworkNotification.CONNECT_TO_NETWORK",
      "com.android.server.wifi.ConnectToNetworkNotification.PICK_NETWORK_AFTER_FAILURE",
      "com.android.server.wifi.ConnectToNetworkNotification.PICK_WIFI_NETWORK",
      "com.android.server.wifi.ConnectToNetworkNotification.USER_DISMISSED_NOTIFICATION",
      "com.android.server.wifi.action.CarrierNetwork.USER_ALLOWED_CARRIER",
      "com.android.server.wifi.action.CarrierNetwork.USER_DISALLOWED_CARRIER",
      "com.android.server.wifi.action.CarrierNetwork.USER_DISMISSED",
      "com.android.server.wifi.action.NetworkSuggestion.USER_ALLOWED_APP",
      "com.android.server.wifi.action.NetworkSuggestion.USER_DISALLOWED_APP",
      "com.android.server.wifi.action.NetworkSuggestion.USER_DISMISSED",
      "com.android.server.wifi.wakeup.DISMISS_NOTIFICATION",
      "com.android.server.wifi.wakeup.OPEN_WIFI_PREFERENCES",
      "com.android.server.wifi.wakeup.OPEN_WIFI_SETTINGS",
      "com.android.server.wifi.wakeup.TURN_OFF_WIFI_WAKE",
      "com.android.server.wm.ACTION_REVOKE_SYSTEM_ALERT_WINDOW_PERMISSION",
      "com.android.settings.bluetooth.ACTION_DISMISS_PAIRING",
      "com.android.settings.location.MODE_CHANGING",
      "com.android.settings.network.DELETE_SUBSCRIPTION",
      "com.android.settings.network.SWITCH_TO_SUBSCRIPTION",
      "com.android.settings.wifi.action.NETWORK_REQUEST",
      "com.android.sync.SYNC_CONN_STATUS_CHANGED",
      "intent.action.ACTION_RF_BAND_INFO",
      "wifi_scan_available" -> true
      else -> false
    }
  }
}
