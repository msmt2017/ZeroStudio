/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.SdkConstants.ATTR_REQUIRED
import com.android.SdkConstants.CLASS_INTENT
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_PERMISSION
import com.android.SdkConstants.TAG_USES_LIBRARY
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_23
import com.android.SdkConstants.TAG_USES_PERMISSION_SDK_M
import com.android.SdkConstants.VALUE_FALSE
import com.android.sdklib.AndroidVersion
import com.android.tools.lint.checks.NotificationPermissionDetector.Issues.POST_NOTIFICATIONS_PERMISSION
import com.android.tools.lint.checks.PermissionFinder.Operation.ACTION
import com.android.tools.lint.checks.PermissionFinder.Operation.READ
import com.android.tools.lint.checks.PermissionFinder.Operation.WRITE
import com.android.tools.lint.checks.PermissionRequirement.ATTR_PROTECTION_LEVEL
import com.android.tools.lint.checks.PermissionRequirement.VALUE_DANGEROUS
import com.android.tools.lint.client.api.LintDriver.Companion.KEY_CONDITION
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Constraint
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.targetSdkAtLeast
import com.android.utils.XmlUtils
import com.google.common.collect.Sets
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isMethodCall
import org.jetbrains.uast.visitor.AbstractUastVisitor
import org.w3c.dom.Document

class PermissionDetector : AbstractAnnotationDetector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> =
    listOf(
      PERMISSION_ANNOTATION.oldName(),
      PERMISSION_ANNOTATION.newName(),
      PERMISSION_ANNOTATION_READ.oldName(),
      PERMISSION_ANNOTATION_READ.newName(),
      PERMISSION_ANNOTATION_WRITE.oldName(),
      PERMISSION_ANNOTATION_WRITE.newName(),
    )

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val type = usageInfo.type
    val method = usageInfo.referenced as? PsiMethod
    if (type == AnnotationUsageType.METHOD_CALL) {
      // Permission annotation specified on method:
      val requirement = PermissionRequirement.create(annotationInfo.annotation)
      checkPermission(context, element, method, null, requirement)
    } else {
      // PERMISSION_ANNOTATION, PERMISSION_ANNOTATION_READ, PERMISSION_ANNOTATION_WRITE
      // When specified on a parameter, that indicates that we're dealing with
      // a permission requirement on this *method* which depends on the value
      // supplied by this parameter
      if (element is UExpression && method != null) {
        checkParameterPermission(context, annotationInfo.qualifiedName, method, element)
      }
    }
  }

  private fun checkParameterPermission(
    context: JavaContext,
    signature: String,
    method: PsiMethod,
    argument: UExpression,
  ) {
    var operation: PermissionFinder.Operation? = null

    if (PERMISSION_ANNOTATION_READ.isEquals(signature)) {
      operation = READ
    } else if (PERMISSION_ANNOTATION_WRITE.isEquals(signature)) {
      operation = WRITE
    } else {
      val type = argument.getExpressionType()
      if (type != null && CLASS_INTENT == type.canonicalText) {
        operation = ACTION
      }
    }
    if (operation == null) {
      return
    }
    val result = PermissionFinder.findRequiredPermissions(context, operation, argument)
    if (result != null) {
      checkPermission(context, argument, method, result, result.requirement)
    }
  }

  /**
   * A "conditional" requirement means that there are some conditions for whether the permission
   * requirement applies, which isn't encoded in the annotation. However, we know of a couple of
   * cases which we can check here. This method looks up the given permission requirement found for
   * the given called method and checks whether the condition it's listing is met.
   *
   * We should audit all the conditional permissions and consider adding some extra metadata
   * annotations, such as requiring a targetSdkVersion, right into the API to avoid hardcoding here.
   */
  private fun conditionMet(
    context: JavaContext,
    requirement: PermissionRequirement,
    method: PsiMethod?,
  ): Boolean {
    method ?: return false

    if (isExactAlarmRequirement(requirement, context, method)) {
      return true
    }

    for (permission in TARGET_33_CONDITIONAL_PERMISSIONS) {
      if (requirement.contains(permission)) {
        return true
      }
    }

    // Not a known case: leave uncertain
    return false
  }

  private fun isExactAlarmRequirement(
    requirement: PermissionRequirement,
    context: JavaContext,
    method: PsiMethod?,
  ): Boolean {
    return requirement.isSingle &&
      requirement.toString() == "android.permission.SCHEDULE_EXACT_ALARM" &&
      context.evaluator.isMemberInClass(method, "android.app.AlarmManager")
  }

  private fun PermissionRequirement.needsNotifyPermission(): Boolean =
    contains(POST_NOTIFICATIONS_PERMISSION)

  private fun checkPermission(
    context: JavaContext,
    node: UElement,
    method: PsiMethod?,
    result: PermissionFinder.Result?,
    requirement: PermissionRequirement,
  ) {
    if (requirement.isConditional && !conditionMet(context, requirement, method)) {
      return
    }
    val manifestPermissions = getPermissions(context)
    checkMissingManifestPermission(context, requirement, manifestPermissions, node, result, method)
    checkMissingPermissionCheck(requirement, manifestPermissions, context, node)
  }

  private fun checkMissingManifestPermission(
    context: JavaContext,
    requirement: PermissionRequirement,
    manifestPermissions: PermissionHolder,
    node: UElement,
    result: PermissionFinder.Result?,
    method: PsiMethod?,
  ) {
    var permissions = manifestPermissions
    if (!requirement.isSatisfied(permissions)) {
      // See if it looks like we're holding the permission implicitly by @RequirePermission
      // annotations in the surrounding context
      val localPermissionRequirements =
        getLocalPermissions(node, requirement.needsNotifyPermission())
      permissions = mergePermissions(permissions, localPermissionRequirements)
      if (!requirement.isSatisfied(permissions)) {
        val operation: PermissionFinder.Operation
        val name: String
        if (result != null) {
          name = result.name
          operation = result.operation
        } else {
          assert(method != null)
          val containingClass = method!!.containingClass
          name =
            if (containingClass != null) {
              containingClass.name + "." + method.name
            } else {
              method.name
            }

          if (
            name == "Builder.setPersisted" &&
              node is UCallExpression &&
              node.valueArguments.size == 1 &&
              ConstantEvaluator.evaluate(context, node.valueArguments[0]) == false
          ) {
            // Special case the JobInfo.Builder permission requirement: it only
            // applies if the argument is true. If we're not sure, default to
            // flagging it.
            return
          }
          operation = PermissionFinder.Operation.CALL
        }
        // Report locations on the call, not just the flown parameter
        var location = context.getLocation(node)
        val expressionNode =
          if (node is UCallExpression) node
          else node.getParentOfType(UCallExpression::class.java, true)
        if (expressionNode != null && node !== expressionNode) {
          val callIdentifier = expressionNode.methodIdentifier
          if (callIdentifier != null && callIdentifier != node) {
            location = context.getRangeLocation(callIdentifier, 0, node, 0)
          }
        }

        val missingPermissions = requirement.getMissingPermissions(permissions)
        var constraint: Constraint? = null

        if (
          method?.name == "getAllCellInfo" &&
            missingPermissions.size == 1 &&
            missingPermissions.first() == "android.permission.ACCESS_COARSE_LOCATION" &&
            permissions.hasPermission("android.permission.ACCESS_FINE_LOCATION")
        ) {
          // Special case for issue 63962416:
          // TelephonyManager.getAllCellInfo is incorrectly annotated as requiring
          // ACCESS_COARSE_LOCATION or ACCESS_FINE_LOCATION; instead it's annotated
          // as just requiring ACCESS_COARSE_LOCATION but clearly holding
          // ACCESS_FINE_LOCATION is acceptable too.
          return
        }

        var messageFormat = "Missing permissions required ${operation.prefix()} $name: %1\$s"
        val operator =
          when (val op = requirement.operator) {
            null,
            JavaTokenType.ANDAND -> "&"
            JavaTokenType.OROR -> "|"
            JavaTokenType.XOR -> "^"
            else -> throw IllegalArgumentException("Unsupported operator type: $op")
          }
        val incident =
          Incident(
            MISSING_PERMISSION,
            node,
            location,
            "",
            // Pass data to IDE quickfix: names to add, and max applicable API version
            fix()
              .data(
                KEY_MESSAGE,
                messageFormat,
                KEY_MISSING_PERMISSIONS,
                missingPermissions.toList(),
                KEY_LAST_API,
                requirement.lastApplicableApi,
                KEY_OPERATOR,
                operator,
              ),
          )

        if (isExactAlarmRequirement(requirement, context, method)) {
          constraint = targetSdkAtLeast(31)
          incident.overrideSeverity(Severity.WARNING)
          val where = method?.name?.let { " with `$it`" } ?: ""
          messageFormat =
            "Setting Exact alarms$where requires the `SCHEDULE_EXACT_ALARM` permission or " +
              "power exemption from user; it is intended for applications where the user knowingly " +
              "schedules actions to happen at a precise time such as alarms, clocks, calendars, etc. " +
              "Check out the javadoc on this permission to make sure your use case is valid."
        } else if (requirement.isConditional) {
          // Normally we skip conditional permissions, but there are a handful of special
          // permissions
          // recently added (listed in TARGET_33_CONDITIONAL_PERMISSIONS) where we make the
          // assumption
          // that the specific condition is "targetSdkVersion >= 33", which we can check.
          if (
            missingPermissions.size == 1 &&
              TARGET_33_CONDITIONAL_PERMISSIONS.contains(missingPermissions.first())
          ) {
            constraint = targetSdkAtLeast(33)
          } else {
            return
          }
        }

        if (context.isGlobalAnalysis()) {
          if (constraint?.accept(context, incident) == false) {
            return
          }

          incident.message = getMissingMessage(messageFormat, requirement, permissions)
          context.report(incident)
        } else {
          val map = map()
          map.put(KEY_REQUIREMENT, requirement.serialize())
          map.put(KEY_MESSAGE, messageFormat)
          constraint?.let { map.put(KEY_CONDITION, it) }

          // Store local permissions too
          if (localPermissionRequirements.isNotEmpty()) {
            map.put(
              KEY_LOCAL_PERMISSION,
              localPermissionRequirements.joinToString(separator = ";") { it.serialize() },
            )
          }
          context.report(incident, map)
        }
        return
      }
    }
  }

  private fun checkMissingPermissionCheck(
    requirement: PermissionRequirement,
    manifestPermissions: PermissionHolder,
    context: JavaContext,
    node: UElement,
  ) {
    if (requirement.isRevocable(manifestPermissions) && requirement.lastApplicableApi >= 23) {
      // Revocable permissions only apply when targeting 23 and above.
      if (
        context.driver.isGlobalAnalysis() && context.mainProject.targetSdkVersion.featureLevel < 23
      ) {
        return
      }

      var handlesMissingPermission = handlesSecurityException(node)

      // If not, check to see if the code is deliberately checking to see if the
      // given permission is available.
      if (!handlesMissingPermission) {

        // See if the requirement is passed on via surrounding requires permissions
        val localPermissionRequirements =
          getLocalPermissions(node, requirement.needsNotifyPermission())
        val localRequirements =
          mergePermissions(
            PermissionHolder.SetPermissionLookup(
              mutableSetOf(),
              mutableSetOf(),
              manifestPermissions.minSdkVersion,
              manifestPermissions.targetSdkVersion,
            ),
            localPermissionRequirements,
          )
        if (requirement.isSatisfied(localRequirements)) {
          return
        }

        val methodNode = node.getParentOfType(UMethod::class.java, true)
        if (methodNode != null) {
          val visitor = CheckPermissionVisitor(node)
          methodNode.accept(visitor)
          handlesMissingPermission = visitor.checksPermission()
        }
      }

      if (!handlesMissingPermission) {
        val message =
          "Call requires permission which may be rejected by user: code should explicitly " +
            "check to see if permission is available (with `checkPermission`) or explicitly " +
            "handle a potential `SecurityException`"
        val location = context.getLocation(node)
        val incident =
          Incident(
            MISSING_PERMISSION,
            node,
            location,
            message,
            // Pass data to IDE quickfix: revocable names, and permission requirement
            fix()
              .data(
                KEY_MISSING_PERMISSIONS,
                requirement.getRevocablePermissions(manifestPermissions).toList(),
                KEY_REQUIREMENT,
                requirement.serialize(),
              ),
          )
        context.report(incident, map())
      }
    }
  }

  private fun getMissingMessage(
    messageFormat: String,
    requirement: PermissionRequirement,
    permissions: PermissionHolder,
  ): String {
    return String.format(messageFormat, requirement.describeMissingPermissions(permissions))
      .replace(
        "carrier privileges",
        "carrier privileges (see TelephonyManager#hasCarrierPrivileges)",
      )
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val requirementString = map.getString(KEY_REQUIREMENT, null)
    if (requirementString == null) {
      // This is a missing permission check incident (not a missing manifest permission incident).

      // Revocable permissions only apply when targeting 23 and above.
      if (context.project.targetSdkVersion.featureLevel < 23) return false

      if (isAndroidThingsProject(context)) return false

      return true
    }

    val requirement = PermissionRequirement.deserialize(requirementString)
    var permissions = getPermissions(context, accessMergedManifest = true)
    if (requirement.isSatisfied(permissions)) {
      return false
    }

    map.getString(KEY_LOCAL_PERMISSION, null)?.let {
      it.splitToSequence(';').forEach { serialized ->
        val r = PermissionRequirement.deserialize(serialized)
        permissions = mergePermissions(permissions, listOf(r))
      }
      if (requirement.isSatisfied(permissions)) {
        return false
      }
    }

    if (map.getConstraint(KEY_CONDITION)?.accept(context, incident) == false) {
      return false
    }

    val messageFormat = map.getString(KEY_MESSAGE, "")!!
    incident.message = getMissingMessage(messageFormat, requirement, permissions)
    return true
  }

  private fun getLocalPermissions(
    node: UElement,
    isNotifyPermission: Boolean,
  ): List<PermissionRequirement> {
    // Accumulate @RequirePermissions available in the local context
    val method = node.getParentOfType(UMethod::class.java, true) ?: return emptyList()
    val methodAnnotation =
      method.findAnnotation(PERMISSION_ANNOTATION.oldName())
        ?: method.findAnnotation(PERMISSION_ANNOTATION.newName())
        ?: method.findAnnotation(AOSP_PERMISSION_ANNOTATION)

    val containingClass = method.getContainingUClass()
    val classAnnotation =
      containingClass?.findAnnotation(PERMISSION_ANNOTATION.oldName())
        ?: containingClass?.findAnnotation(PERMISSION_ANNOTATION.newName())
        ?: containingClass?.findAnnotation(AOSP_PERMISSION_ANNOTATION)

    val requirements =
      if (methodAnnotation == null && classAnnotation == null) emptyList()
      else listOfNotNull(methodAnnotation, classAnnotation).map { PermissionRequirement.create(it) }

    if (isNotifyPermission) {
      // Hardcoded knowledge about the NotificationManager/NotificationManagerCompat specialized
      // permission
      // lookup methods
      if (NotificationPermissionDetector.isNotificationPermissionChecked(node)) {
        return requirements + PermissionRequirement.create(POST_NOTIFICATIONS_PERMISSION)
      }
    }

    return requirements
  }

  private fun mergePermissions(
    permissions: PermissionHolder,
    requirements: List<PermissionRequirement>,
  ): PermissionHolder {
    var merged = permissions
    for (requirement in requirements) {
      merged = PermissionHolder.SetPermissionLookup.join(merged, requirement)
    }
    return merged
  }

  /**
   * Visitor which looks through a method, up to a given call (the one requiring a permission) and
   * checks whether it's preceded by a call to checkPermission or checkCallingPermission or
   * enforcePermission etc.
   *
   * Currently it only looks for the presence of this check; it does not perform flow analysis to
   * determine whether the check actually affects program flow up to the permission call, or whether
   * the check permission is checking for permissions sufficient to satisfy the permission
   * requirement of the target call, or whether the check return value (== PERMISSION_GRANTED vs !=
   * PERMISSION_GRANTED) is handled correctly, etc.
   */
  private class CheckPermissionVisitor(private val mTarget: UElement) : AbstractUastVisitor() {
    private var mChecksPermission: Boolean = false
    private var mDone: Boolean = false

    override fun visitElement(node: UElement): Boolean {
      return mDone || super.visitElement(node)
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (node.isMethodCall()) {
        visitMethodCallExpression(node)
      }
      return super.visitCallExpression(node)
    }

    private fun visitMethodCallExpression(node: UCallExpression) {
      if (node === mTarget) {
        mDone = true
      }

      val name = node.methodName
      if (
        name != null &&
          (name.startsWith("check") || name.startsWith("enforce")) &&
          name.endsWith("Permission")
      ) {
        mChecksPermission = true
        mDone = true
      }
    }

    fun checksPermission(): Boolean {
      return mChecksPermission
    }
  }

  private fun handlesSecurityException(node: UElement): Boolean {
    // allowSuperClass = false:
    // In earlier versions we checked not just for java.lang.SecurityException but
    // any super type as well, however that probably hides warnings in cases where
    // users don't want that; see http://b.android.com/182165
    return handlesException(node, null, allowSuperClass = false, SECURITY_EXCEPTION)
  }

  private var mPermissions: PermissionHolder? = null

  private fun getPermissions(
    context: Context,
    accessMergedManifest: Boolean = false,
  ): PermissionHolder {
    return mPermissions
      ?: if (accessMergedManifest || context.isGlobalAnalysis())
        createMergedManifestPermissionHolder(context).also { mPermissions = it }
      else createLocalPermissionHolder(context).also { mPermissions = it }
  }

  private fun createLocalPermissionHolder(context: Context): PermissionHolder {
    val project = context.project
    val minSdkVersion = project.minSdkVersion
    val targetSdkVersion = project.targetSdkVersion

    val dom = project.manifestDom
    return if (dom != null) {
      createManifestPermissionHolder(dom, minSdkVersion, targetSdkVersion)
    } else {
      PermissionHolder.SetPermissionLookup(emptySet(), emptySet(), minSdkVersion, targetSdkVersion)
    }
  }

  private fun createMergedManifestPermissionHolder(context: Context): PermissionHolder {
    val mainProject = context.mainProject
    val mergedManifest = mainProject.mergedManifest
    val minSdkVersion = mainProject.minSdkVersion
    val targetSdkVersion = mainProject.targetSdkVersion
    return createManifestPermissionHolder(mergedManifest, minSdkVersion, targetSdkVersion)
  }

  private fun createManifestPermissionHolder(
    manifest: Document?,
    minSdkVersion: AndroidVersion,
    targetSdkVersion: AndroidVersion,
  ): PermissionHolder {
    val permissions = Sets.newHashSetWithExpectedSize<String>(30)
    val revocable = Sets.newHashSetWithExpectedSize<String>(4)
    if (manifest != null) {
      for (element in XmlUtils.getSubTags(manifest.documentElement)) {
        val nodeName = element.nodeName
        if (
          TAG_USES_PERMISSION == nodeName ||
            TAG_USES_PERMISSION_SDK_23 == nodeName ||
            TAG_USES_PERMISSION_SDK_M == nodeName
        ) {
          val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (name.isNotEmpty()) {
            permissions.add(name)
          }
        } else if (nodeName == TAG_PERMISSION) {
          val protectionLevel = element.getAttributeNS(ANDROID_URI, ATTR_PROTECTION_LEVEL)
          if (VALUE_DANGEROUS == protectionLevel) {
            val name = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
            if (name.isNotEmpty()) {
              revocable.add(name)
            }
          }
        }
      }
    }
    return PermissionHolder.SetPermissionLookup(
      permissions,
      revocable,
      minSdkVersion,
      targetSdkVersion,
    )
  }

  private var mIsAndroidThingsProject: Boolean? = null

  private fun isAndroidThingsProject(context: Context): Boolean {
    if (mIsAndroidThingsProject == null) {
      val project = context.mainProject
      val mergedManifest = project.mergedManifest ?: return false
      val manifest = mergedManifest.documentElement ?: return false

      val application = XmlUtils.getFirstSubTagByName(manifest, TAG_APPLICATION) ?: return false
      var usesLibrary = XmlUtils.getFirstSubTagByName(application, TAG_USES_LIBRARY)

      while (usesLibrary != null && mIsAndroidThingsProject == null) {
        val name = usesLibrary.getAttributeNS(ANDROID_URI, ATTR_NAME)
        var isThingsLibraryRequired = true
        val required = usesLibrary.getAttributeNS(ANDROID_URI, ATTR_REQUIRED)
        if (VALUE_FALSE == required) {
          isThingsLibraryRequired = false
        }

        if (THINGS_LIBRARY == name && isThingsLibraryRequired) {
          mIsAndroidThingsProject = true
        }

        usesLibrary = XmlUtils.getNextTagByName(usesLibrary, TAG_USES_LIBRARY)
      }
      if (mIsAndroidThingsProject == null) {
        mIsAndroidThingsProject = false
      }
    }
    return mIsAndroidThingsProject!!
  }

  companion object {
    const val KEY_MISSING_PERMISSIONS = "missing"
    const val KEY_LAST_API = "lastApi"
    const val KEY_REQUIREMENT = "requirement"
    const val KEY_LOCAL_PERMISSION = "local"
    const val KEY_MESSAGE = "message"
    const val KEY_OPERATOR = "operator"

    private val IMPLEMENTATION =
      Implementation(PermissionDetector::class.java, Scope.JAVA_FILE_SCOPE)

    private const val THINGS_LIBRARY = "com.google.android.things"
    const val AOSP_PERMISSION_ANNOTATION = "android.annotation.RequiresPermission"

    /**
     * These permissions are required if (1) the `targetSdkVersion` is >= 33, and (2) the permission
     * requirement is marked conditional and includes one of these permissions as one of its terms.
     */
    private val TARGET_33_CONDITIONAL_PERMISSIONS =
      listOf("android.permission.NEARBY_WIFI_DEVICES", "android.permission.BODY_SENSORS_BACKGROUND")

    /** Method result should be used. */
    @JvmField
    val MISSING_PERMISSION =
      Issue.create(
        id = "MissingPermission",
        briefDescription = "Missing Permissions",
        explanation =
          """
                This check scans through your code and libraries and looks at the APIs being \
                used, and checks this against the set of permissions required to access \
                those APIs. If the code using those APIs is called at runtime, then the \
                program will crash.

                Furthermore, for permissions that are revocable (with `targetSdkVersion` 23), \
                client code must also be prepared to handle the calls throwing an exception \
                if the user rejects the request for permission at runtime.""",
        category = Category.CORRECTNESS,
        priority = 9,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    fun handlesException(
      node: UElement,
      exceptionClass: PsiClass?,
      allowSuperClass: Boolean,
      exceptionClassName: String,
    ): Boolean {
      // Ensure that the caller is handling a security exception
      // First check to see if we're inside a try/catch which catches a SecurityException
      // (or some wider exception than that). Check for nested try/catches too.
      var parent = node
      while (true) {
        val tryCatch = parent.getParentOfType(UTryExpression::class.java, true)
        if (tryCatch == null) {
          break
        } else {
          for (catchClause in tryCatch.catchClauses) {
            if (
              containsException(
                catchClause.types,
                exceptionClass,
                allowSuperClass,
                exceptionClassName,
              )
            ) {
              return true
            }
          }

          parent = tryCatch
        }
      }

      // If not, check to see if the method itself declares that it throws a
      // SecurityException or something wider.
      val declaration = parent.getParentOfType(UMethod::class.java, false)
      if (declaration != null) {
        val thrownTypes = declaration.javaPsi.throwsList.referencedTypes
        if (
          containsException(
            listOf<PsiClassType>(*thrownTypes),
            exceptionClass,
            allowSuperClass,
            exceptionClassName,
          )
        ) {
          return true
        }
      }

      return false
    }

    private fun containsException(
      types: List<PsiType>,
      exceptionClass: PsiClass?,
      allowSuperClass: Boolean,
      exceptionClassName: String?,
    ): Boolean {
      for (type in types) {
        if (type is PsiClassType) {
          val cls =
            type.resolve()?.qualifiedName ?: return true // on resolve failures, assume it's handled
          if (allowSuperClass && exceptionClass != null) {
            return InheritanceUtil.isInheritor(exceptionClass, false, cls)
          } else if (exceptionClassName == cls) {
            return true
          }
        }
      }

      return false
    }
  }
}
