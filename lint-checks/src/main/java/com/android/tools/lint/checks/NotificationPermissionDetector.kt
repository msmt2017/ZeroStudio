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
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_ACTION
import com.android.SdkConstants.TAG_APPLICATION
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_SERVICE
import com.android.SdkConstants.TAG_USES_PERMISSION
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ClassContext
import com.android.tools.lint.detector.api.ClassScanner
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Project
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isUnconditionalReturn
import com.android.utils.findGradleBuildFile
import com.android.utils.subtag
import com.android.utils.subtags
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import java.util.EnumSet
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UUnaryExpression
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.UastPrefixOperator
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.tryResolve
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MethodNode
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Makes sure that when targeting T or above an application which posts notifications also declares
 * the POST_NOTIFICATIONS permission.
 */
class NotificationPermissionDetector : Detector(), SourceCodeScanner, ClassScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(
        NotificationPermissionDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.JAVA_LIBRARIES),
        Scope.JAVA_FILE_SCOPE,
      )

    @JvmField
    val ISSUE =
      Issue.create(
        id = "NotificationPermission",
        briefDescription = "Notifications Without Permission",
        explanation =
          """
                When targeting Android 13 and higher, posting permissions requires holding the runtime permission \
                `android.permission.POST_NOTIFICATIONS`.
                """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Boolean property: whether we've found at least one notification call in the source code. */
    private const val KEY_SOURCE = "source"

    /** Location property: the first location of a .class file notification. */
    private const val KEY_CLASS = "class"

    /** The name of the class referencing the notification manager */
    private const val KEY_CLASS_NAME = "className"

    const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
    private const val NOTIFICATION_MANAGER = "android.app.NotificationManager"
    private const val NOTIFICATION_MANAGER_COMPAT = "androidx.core.app.NotificationManagerCompat"
    private const val ENABLED_CHECK_METHOD = "areNotificationsEnabled"
    // Relevant classes:
    //  * com/google/android/exoplayer2/offline/DownloadService$ForegroundNotificationUpdater
    //  * com/google/android/exoplayer2/util/NotificationUtil
    private const val EXO_PLAYER_CLASS_NAME_PREFIX = "com/google/android/exoplayer2/"

    private const val MIN_TARGET = 33

    /**
     * Is the given [element] inside an `areNotificationsEnabled` check, or after an early return of
     * the negated check?
     */
    fun isNotificationPermissionChecked(element: UElement): Boolean {
      var curr = element.uastParent ?: return false

      var prev = element
      while (curr !is UFile) {
        if (curr is UIfExpression) {
          val condition = curr.condition
          if (prev !== condition) {
            val fromThen = prev == curr.thenExpression
            if (fromThen) {
              if (isNotificationPermissionCheck(condition)) {
                return true
              }
            } else {
              // Handle "if (!areNotificationsEnabled) else <CALL>"
              val op = condition.skipParenthesizedExprDown()
              if (
                op is UUnaryExpression &&
                  op.operator == UastPrefixOperator.LOGICAL_NOT &&
                  isNotificationPermissionCheck(op.operand)
              ) {
                return true
              } else if (
                op is UPolyadicExpression &&
                  op.operator == UastBinaryOperator.LOGICAL_OR &&
                  (op.operands.any {
                    val nested = it.skipParenthesizedExprDown()
                    nested is UUnaryExpression &&
                      nested.operator == UastPrefixOperator.LOGICAL_NOT &&
                      isNotificationPermissionCheck(nested.operand)
                  })
              ) {
                return true
              }
            }
          }
        } else if (curr is UMethod) {
          // See if there's an early return. We *only* handle a very simple canonical format here;
          // must be first statement in method.
          val body = curr.uastBody
          if (body is UBlockExpression && body.expressions.size > 1) {
            val first = body.expressions[0]
            if (first is UIfExpression) {
              val condition = first.condition.skipParenthesizedExprDown()
              if (
                condition is UUnaryExpression &&
                  condition.operator == UastPrefixOperator.LOGICAL_NOT &&
                  isNotificationPermissionCheck(condition.operand)
              ) {
                // It's a notifications enabled check; make sure we only return
                val then = first.thenExpression?.skipParenthesizedExprDown()
                if (then != null && then.isUnconditionalReturn()) {
                  return true
                }
              }
            }
          }
        }

        prev = curr
        curr = curr.uastParent ?: break
      }

      return false
    }

    /**
     * Is the given [element] a notify-allowed check, e.g.
     * `NotificationManager.areNotificationsEnabled()` or
     * `NotificationManagerCompat.areNotificationsEnabled()` ?
     */
    private fun isNotificationPermissionCheck(element: UElement): Boolean {
      if (element is UUnaryExpression && element.operator == UastPrefixOperator.LOGICAL_NOT) {
        return !isNotificationPermissionCheck(element.operand)
      } else if (element is UReferenceExpression || element is UCallExpression) {
        val resolved = element.tryResolve()
        if (resolved is PsiMethod) {
          if (resolved.name == ENABLED_CHECK_METHOD) {
            val cls = resolved.containingClass?.qualifiedName
            if (cls == NOTIFICATION_MANAGER || cls == NOTIFICATION_MANAGER_COMPAT) {
              return true
            }
          }
        } else if (resolved is PsiField) {
          // Arguably we should look for final fields here, but on the other hand
          // there may be cases where it's initialized later, so it's a bit like
          // Kotlin's "lateinit". Treat them all as constant.
          val initializer = UastFacade.getInitializerBody(resolved)
          if (initializer != null) {
            return isNotificationPermissionCheck(initializer)
          }
        }
      } else if (element is UParenthesizedExpression) {
        return isNotificationPermissionCheck(element.expression)
      } else if (element is UPolyadicExpression) {
        if (element.operator == UastBinaryOperator.LOGICAL_AND) {
          for (operand in element.operands) {
            if (isNotificationPermissionCheck(operand)) {
              return true
            }
          }
        }
      }
      return false
    }
  }

  override fun getApplicableMethodNames(): List<String> = listOf("notify")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (
      !evaluator.isMemberInClass(method, NOTIFICATION_MANAGER) &&
        !evaluator.isMemberInClass(method, NOTIFICATION_MANAGER_COMPAT)
    ) {
      return
    }

    if (isNotificationPermissionChecked(node)) {
      return
    }

    // In the IDE we can immediately check manifest and target sdk version and
    // bail out if permission is held or not yet targeting T
    if (context.isGlobalAnalysis()) {
      if (
        context.mainProject.targetSdk < MIN_TARGET ||
          isHoldingPostNotificationsViaAnnotations(node) ||
          isHoldingPostNotifications(context.mainProject) != false ||
          context.mainProject.isLibrary
      ) {
        return
      }
    } else {
      if (isHoldingPostNotificationsViaAnnotations(node)) {
        return
      }
      val map = context.getPartialResults(ISSUE).map()
      if (!map.containsKey(KEY_SOURCE)) {
        map.put(KEY_SOURCE, true)
      }
    }

    val incident =
      Incident(ISSUE, node, context.getLocation(node), getWarningMessage(), createFix())
    if (context.isGlobalAnalysis()) {
      context.report(incident)
      if (context.driver.scope.contains(Scope.JAVA_LIBRARIES)) {
        context.getPartialResults(ISSUE).map().put(KEY_SOURCE, true)
      }
    } else {
      context.report(incident, map())
    }
  }

  private fun getWarningMessage() =
    "When targeting Android 13 or higher, posting a permission requires holding the `POST_NOTIFICATIONS` permission"

  override fun getApplicableCallNames(): List<String> = listOf("notify")

  override fun checkCall(
    context: ClassContext,
    classNode: ClassNode,
    method: MethodNode,
    call: MethodInsnNode,
  ) {
    val owner = classNode.name
    if (
      owner.startsWith("android/") ||
        owner.startsWith("androidx/") ||
        owner.startsWith("com/google/android/gms/") // such as play-services-base
    ) {
      // Call from within AndroidX libraries; just depending on AndroidX doesn't mean you're using
      // its notification utility methods.
      return
    }
    if (
      call.owner == "android/app/NotificationManager" ||
        call.owner == "androidx/core/app/NotificationManagerCompat"
    ) {
      val map = context.getPartialResults(ISSUE).map()
      if (
        !map.containsKey(KEY_CLASS) ||
          // We special case the exo player reference later, so if there are any *other*
          // notification manager usages, make sure we record those
          map[KEY_CLASS_NAME]?.startsWith(EXO_PLAYER_CLASS_NAME_PREFIX) == true
      ) {
        if (isHoldingPostNotificationsViaAnnotations(classNode, method)) {
          return
        }
        map.put(KEY_CLASS, context.getLocation(call))
        map.put(KEY_CLASS_NAME, owner)
      }
    }
  }

  override fun afterCheckRootProject(context: Context) {
    if (
      context.isGlobalAnalysis() &&
        context.driver.scope.contains(Scope.JAVA_LIBRARIES) &&
        !context.mainProject.isLibrary
    ) {
      checkClassReference(context.getPartialResults(ISSUE).map(), context)
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    assert(!context.isGlobalAnalysis())
    if (context.project === context.mainProject && !context.mainProject.isLibrary) {
      val map = partialResults.map()
      checkClassReference(map, context)
    }
  }

  private fun checkClassReference(map: LintMap, context: Context) {
    if (context.mainProject.targetSdk < MIN_TARGET) {
      return
    }
    if (map.getBoolean(KEY_SOURCE) == true) {
      // Have already found source reference and reported it there (from visitCall); no need to also
      // report
      // from bytecode
      return
    }
    if (isHoldingPostNotifications(context.mainProject) != false) {
      return
    }

    // Allow suppressing via the manifest
    val mergedManifest = context.mainProject.mergedManifest?.documentElement
    if (mergedManifest != null && context.driver.isSuppressed(null, ISSUE, mergedManifest)) {
      return
    }

    val owner = map[KEY_CLASS_NAME] ?: return
    if (
      owner.startsWith(EXO_PLAYER_CLASS_NAME_PREFIX) &&
        (mergedManifest == null || !mergedManifest.hasExoPlayerDownloader())
    ) {
      // The only reference was from exoplayer, which uses notifications only for
      // the optional download service; here we've seen that you're not using it
      // so there's no reason to warn.
      return
    }

    // Avoid pointing to the .jar file which referenced the notification directly; these
    // paths often contain paths that vary from machine to machine so cannot be properly
    // baselined:
    // ~/.gradle/caches/transforms-3/4366a02f2b10003dc48387e903833c2d/transformed/leakcanary-android-core-2.8.1/jars/classes.jar
    //
    // It's not as if the user needs to go to the file to make changes;
    // this is the path where the usage was found; instead, point them to the manifest file
    // where they should be making these changes (adding the permission).
    val manifest = context.mainProject.manifestFiles.firstOrNull()
    val gradleFile = findGradleBuildFile(context.mainProject.dir)

    val location =
      if (manifest != null) Location.create(manifest)
      else if (gradleFile.isFile) Location.create(gradleFile)
      else map.getLocation(KEY_CLASS) ?: return
    val message = getWarningMessage() + " (usage from ${ClassContext.getFqcn(owner)})"
    context.report(ISSUE, location, message, createFix())
  }

  private fun Element.hasExoPlayerDownloader(): Boolean {
    val application = subtag(TAG_APPLICATION) ?: return false
    for (service in application.subtags(TAG_SERVICE)) {
      for (intentFilter in service.subtags(TAG_INTENT_FILTER)) {
        for (action in intentFilter.subtags(TAG_ACTION)) {
          val name = action.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (name == "com.google.android.exoplayer.downloadService.action.RESTART") {
            return true
          }
        }
      }
    }

    return false
  }

  /**
   * Is the given [scope] surrounded by a `@RequiresPermission` annotation which lists the
   * POST_NOTIFICATIONS permission as a prerequisite?
   */
  private fun isHoldingPostNotificationsViaAnnotations(scope: UElement): Boolean {
    var currentScope: UElement? = scope
    while (currentScope != null) {
      if (currentScope is UAnnotated) {
        //noinspection ExternalAnnotations
        val annotations = currentScope.uAnnotations
        for (annotation in annotations) {
          val fqcn = annotation.qualifiedName
          if (PERMISSION_ANNOTATION.isEquals(fqcn)) {
            val requirement = PermissionRequirement.create(annotation)
            if (isHoldingPostNotificationsViaAnnotations(requirement)) {
              return true
            }
          }
        }
      }

      if (currentScope is UFile) {
        return false
      }
      currentScope = currentScope.uastParent
    }

    return false
  }

  private fun List<AnnotationNode>.getPermissionRequirement(): PermissionRequirement? {
    for (annotation in this) {
      if (annotation.desc == "Landroidx/annotation/RequiresPermission;") {
        return PermissionRequirement.create(annotation)
      }
    }

    return null
  }

  private fun isHoldingPostNotificationsViaAnnotations(
    classNode: ClassNode,
    method: MethodNode,
  ): Boolean {
    val requirement =
      method.invisibleAnnotations?.getPermissionRequirement()
        ?: classNode.invisibleAnnotations?.getPermissionRequirement()
        ?: return false

    return isHoldingPostNotificationsViaAnnotations(requirement)
  }

  private fun isHoldingPostNotificationsViaAnnotations(
    requirement: PermissionRequirement
  ): Boolean {
    return requirement
      .getMissingPermissions(PermissionHolder.NONE)
      .contains(POST_NOTIFICATIONS_PERMISSION)
  }

  private fun isHoldingPostNotifications(project: Project): Boolean? {
    val mergedManifest = project.mergedManifest ?: return null
    var curr = mergedManifest.documentElement?.firstChild ?: return null
    while (true) {
      if (curr.nodeType == Node.ELEMENT_NODE && curr.nodeName == TAG_USES_PERMISSION) {
        val element = curr as Element
        val permission = element.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (permission == POST_NOTIFICATIONS_PERMISSION) {
          return true
        }
      }
      curr = curr.nextSibling ?: return false
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    val project = context.mainProject
    if (
      project.targetSdk >= MIN_TARGET &&
        !project.isLibrary &&
        isHoldingPostNotifications(project) == false
    ) {
      map.put(KEY_SOURCE, true)
      return true
    }

    return false
  }

  private fun createFix(): LintFix {
    return fix()
      .data(PermissionDetector.KEY_MISSING_PERMISSIONS, listOf(POST_NOTIFICATIONS_PERMISSION))
  }
}
