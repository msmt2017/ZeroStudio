/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.AndroidXConstants.MOTION_LAYOUT
import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_ID
import com.android.SdkConstants.AUTO_URI
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.XML
import com.android.support.AndroidxName
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TypeEvaluator
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.getBaseName
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.stripIdPrefix
import com.android.utils.iterator
import com.google.common.collect.Sets
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiVariable
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.w3c.dom.Element

/** Looks for issues around ObjectAnimator usages. */
class ObjectAnimatorDetector : Detector(), SourceCodeScanner, XmlScanner {
  /**
   * Multiple properties might all point back to the same setter; we don't want to highlight these
   * more than once (duplicate warnings etc) so keep track of them here.
   */
  private var mAlreadyWarned: MutableSet<Any?>? = null

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      "ofInt",
      "ofArgb",
      "ofFloat",
      "ofMultiInt",
      "ofMultiFloat",
      "ofObject",
      "ofPropertyValuesHolder",
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val evaluator = context.evaluator
    if (
      !evaluator.isMemberInClass(method, "android.animation.ObjectAnimator") &&
        !(method.name == "ofPropertyValuesHolder" &&
          evaluator.isMemberInClass(method, "android.animation.ValueAnimator"))
    ) {
      return
    }

    val expressions = node.valueArguments
    if (expressions.size < 2) {
      return
    }

    val type = TypeEvaluator.evaluate(expressions[0]) as? PsiClassType ?: return
    val targetClass = type.resolve() ?: return
    val methodName = method.name
    if (methodName == "ofPropertyValuesHolder") {
      if (!evaluator.isMemberInClass(method, "android.animation.ObjectAnimator")) {
        // *Don't* match ValueAnimator.ofPropertyValuesHolder(); for that method,
        // arg0 is another PropertyValuesHolder, *not* the target object!
        return
      }

      // Try to find the corresponding property value holder initializations
      // and validate each one
      checkPropertyValueHolders(context, targetClass, expressions)
    } else {
      // If "ObjectAnimator#ofObject", look for the type evaluator type in
      // argument at index 2 (third argument)
      val expectedType = getExpectedType(node, 2) ?: return
      checkProperty(context, expressions[1], targetClass, expectedType)
    }
  }

  private fun checkPropertyValueHolders(
    context: JavaContext,
    targetClass: PsiClass,
    expressions: List<UExpression>,
  ) {
    for (i in 1 until expressions.size) { // expressions[0] is the target class
      val arg = expressions[i]
      // Find last assignment for each argument; this should be generic
      // infrastructure.
      val holder = findHolderConstruction(context, arg) ?: return
      val args = holder.valueArguments
      if (args.size >= 2) {
        // If "PropertyValueHolder#ofObject", look for the type evaluator type in
        // argument at index 1 (second argument)
        val expectedType = getExpectedType(holder, 1) ?: return
        checkProperty(context, args[0], targetClass, expectedType)
      }
    }
  }

  private fun checkProperty(
    context: JavaContext,
    propertyNameExpression: UExpression,
    targetClass: PsiClass,
    expectedType: String,
  ) {
    val property = ConstantEvaluator.evaluate(context, propertyNameExpression) as? String ?: return
    val qualifiedName = targetClass.qualifiedName ?: return
    if (qualifiedName.indexOf('.') == -1) { // resolve error?
      return
    }
    val methodName = getMethodName("set", property)
    val methods = targetClass.findMethodsByName(methodName, true)
    var bestMethod: PsiMethod? = null
    var isExactMatch = false
    for (m in methods) {
      if (m.parameterList.parametersCount == 1) {
        if (bestMethod == null) {
          bestMethod = m
        }
        if (context.evaluator.parametersMatch(m, expectedType)) {
          bestMethod = m
          isExactMatch = true
          break
        }
      } else if (bestMethod == null) {
        bestMethod = m
      }
    }
    if (bestMethod == null) {
      report(
        context,
        BROKEN_PROPERTY,
        propertyNameExpression,
        null,
        "Could not find property setter method `$methodName` on `$qualifiedName`",
        null,
      )
      return
    }
    if (!isExactMatch) {
      report(
        context,
        BROKEN_PROPERTY,
        propertyNameExpression,
        bestMethod,
        "The setter for this property does not match the " +
          "expected signature (`public void $methodName($expectedType arg`)",
        null,
      )
    } else if (context.evaluator.isStatic(bestMethod)) {
      report(
        context,
        BROKEN_PROPERTY,
        propertyNameExpression,
        bestMethod,
        "The setter for this property ($qualifiedName.$methodName) should not be static",
        null,
      )
    } else {
      var owner: PsiModifierListOwner? = bestMethod
      while (owner != null) {
        for (annotation in context.evaluator.getAnnotations(owner, false)) {
          if (KEEP_ANNOTATION.isEquals(annotation.qualifiedName)) {
            return
          }
        }
        owner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner::class.java, true)
      }

      // Only flag these warnings if minifyEnabled is true in at least one
      // variant?
      if (!isShrinking(context)) {
        return
      }
      val fix =
        fix().annotate(KEEP_ANNOTATION.newName(), context = context, element = bestMethod).build()
      report(
        context,
        MISSING_KEEP,
        propertyNameExpression,
        bestMethod,
        "This method is accessed from an ObjectAnimator so it should be " +
          "annotated with `@Keep` to ensure that it is not discarded or renamed " +
          "in release builds",
        fix,
      )
    }
  }

  private fun report(
    context: JavaContext,
    issue: Issue,
    propertyNameExpression: UExpression,
    method: PsiMethod?,
    originalMessage: String,
    fix: LintFix?,
  ) {
    var message = originalMessage
    val reportOnMethod = issue === MISSING_KEEP && method != null

    // No need to report @Keep issues in third party libraries
    if (reportOnMethod && method is PsiCompiledElement) {
      return
    }

    val locationNode: Any = if (reportOnMethod && method != null) method else propertyNameExpression
    val alreadyWarned =
      mAlreadyWarned
        ?: run {
          val set: MutableSet<Any?> = Sets.newIdentityHashSet()
          mAlreadyWarned = set
          set
        }
    if (alreadyWarned.contains(locationNode)) {
      return
    }
    alreadyWarned.add(locationNode)

    var methodLocation: Location? = null
    if (method != null && method !is PsiCompiledElement) {
      val nameIdentifier = method.nameIdentifier
      methodLocation =
        if (nameIdentifier != null)
          context.getRangeLocation(
            nameIdentifier,
            fromDelta = 0,
            to = method.parameterList,
            toDelta = 0,
          )
        else context.getNameLocation(method)
    }
    var location: Location
    if (reportOnMethod && methodLocation != null && method != null) {
      location = methodLocation
      val secondary = context.getLocation(propertyNameExpression)
      location.secondary = secondary
      secondary.message = "ObjectAnimator usage here"

      // In the same compilation unit, don't show the error on the reference,
      // but in other files (where you may not spot the problem), highlight it.
      if (isInSameCompilationUnit(propertyNameExpression, method)) {
        // Same compilation unit: we don't need to show (in the IDE) the secondary
        // location since we're drawing attention to the keep issue)
        secondary.visible = false
      } else {
        assert(issue === MISSING_KEEP)
        val secondaryMessage =
          "The method referenced here (${method.name}) has " +
            "not been annotated with `@Keep` which means it could be " +
            "discarded or renamed in release builds"

        // If on the other hand we're in a separate compilation unit, we should
        // draw attention to the problem
        if (location === Location.NONE) {
          // When running within the IDE in single file scope the IDE just creates
          // none-locations for items in other files; in this case make this
          // the primary locations instead
          location = secondary
          message = secondaryMessage
        } else {
          secondary.message = secondaryMessage
        }
      }
    } else {
      location = context.getNameLocation(propertyNameExpression)
      if (methodLocation != null) {
        location = location.withSecondary(methodLocation, "Property setter here")
      }
    }

    // Allow suppressing at either the property binding site *or* the property site
    // (we report errors on both)
    val owner = propertyNameExpression.getParentOfType<UElement>(UDeclaration::class.java, false)
    if (owner != null && context.driver.isSuppressed(context, issue, owner)) {
      return
    }
    context.report(issue, method, location, message, fix)
  }

  private fun getExpectedType(method: UCallExpression, evaluatorIndex: Int): String? {
    val methodName = getMethodName(method) ?: return null
    when (methodName) {
      "ofArgb",
      "ofInt" -> return "int"
      "ofFloat" -> return "float"
      "ofMultiInt" -> return "int[]"
      "ofMultiFloat" -> return "float[]"
      "ofKeyframe" -> return "android.animation.Keyframe"
      "ofObject" -> {
        val args = method.valueArguments
        if (args.size > evaluatorIndex) {
          val evaluatorType = TypeEvaluator.evaluate(args[evaluatorIndex]) ?: return null
          return when (evaluatorType.canonicalText) {
            "android.animation.FloatEvaluator" -> "float"
            "android.animation.FloatArrayEvaluator" -> "float[]"
            "android.animation.IntEvaluator",
            "android.animation.ArgbEvaluator" -> "int"
            "android.animation.IntArrayEvaluator" -> "int[]"
            "android.animation.PointFEvaluator" -> "android.graphics.PointF"
            else -> null
          }
        }
      }
    }
    return null
  }

  private fun findHolderConstruction(context: JavaContext, arg: UExpression?): UCallExpression? {
    if (arg == null) {
      return null
    }
    if (arg is UCallExpression) {
      if (isHolderConstructionMethod(context, arg)) {
        return arg
      }
      // else: look inside the method and see if it's a method which trivially returns
      // an instance?
    } else if (arg is UReferenceExpression) {
      if (arg is UQualifiedReferenceExpression) {
        if (arg.selector is UCallExpression) {
          val selector = arg.selector as UCallExpression
          if (isHolderConstructionMethod(context, selector)) {
            return selector
          }
        }
      }

      // Variable reference? Field reference? etc.
      val resolved = arg.resolve()
      if (resolved is PsiVariable) {
        var lastAssignment = UastLintUtils.findLastAssignment(resolved, arg)
        // Resolve variable reassignments
        while (lastAssignment is USimpleNameReferenceExpression) {
          val el = lastAssignment.resolve()
          lastAssignment =
            if (el is PsiLocalVariable) {
              UastLintUtils.findLastAssignment(el, lastAssignment)
            } else {
              break
            }
        }
        if (lastAssignment is UCallExpression) {
          val callExpression = lastAssignment
          if (isHolderConstructionMethod(context, callExpression)) {
            return callExpression
          }
        } else if (lastAssignment is UQualifiedReferenceExpression) {
          val expression = lastAssignment
          if (expression.selector is UCallExpression) {
            val callExpression = expression.selector as UCallExpression
            if (isHolderConstructionMethod(context, callExpression)) {
              return callExpression
            }
          }
        }
      }
    }
    return null
  }

  private fun isHolderConstructionMethod(
    context: JavaContext,
    callExpression: UCallExpression,
  ): Boolean {
    val referenceName = getMethodName(callExpression)
    if (
      referenceName != null &&
        referenceName.startsWith("of") &&
        // This will require more indirection; see unit test
        referenceName != "ofKeyframe"
    ) {
      val resolved = callExpression.resolve()
      if (
        resolved != null &&
          context.evaluator.isMemberInClass(resolved, "android.animation.PropertyValuesHolder")
      ) {
        return true
      }
    }
    return false
  }

  private fun isInSameCompilationUnit(element1: UElement, element2: PsiElement): Boolean {
    val containingFile = element1.getContainingUFile()
    var file = containingFile?.javaPsi
    if (file == null) {
      val psi = element1.javaPsi
      if (psi != null) {
        file = psi.containingFile
      }
    }
    return file == element2.containingFile
  }

  // Copy of PropertyValuesHolder#getMethodName - copy to ensure lint & platform agree
  private fun getMethodName(
    @Suppress("SameParameterValue") prefix: String,
    propertyName: String?,
  ): String {
    if (propertyName == null || propertyName.isEmpty()) {
      // shouldn't get here
      return prefix
    }
    val firstLetter = Character.toUpperCase(propertyName[0])
    val theRest = propertyName.substring(1)
    return prefix + firstLetter + theRest
  }

  private fun isShrinking(context: Context): Boolean {
    val project = if (context.isGlobalAnalysis()) context.mainProject else context.project
    val model = project.buildModule
    return if (model != null) {
      !model.neverShrinking()
    } else {
      // No model? Err on the side of caution;
      // assume project may be using ProGuard/other shrinking
      true
    }
  }

  // Implements XmlScanner

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == LAYOUT || folderType == XML
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(MOTION_LAYOUT.oldName(), MOTION_LAYOUT.newName(), "CustomAttribute")
  }

  private data class SceneReference(val viewClass: String, val id: String, val scene: String)

  private var sceneIds: MutableList<SceneReference>? = null

  override fun visitElement(context: XmlContext, element: Element) {
    if (context.resourceFolderType == LAYOUT) {
      // MotionLayout
      // app:layoutDescription="@xml/scene_show_details"
      val sceneReference = element.getAttributeNS(MOTION_LAYOUT_URI, "layoutDescription")
      if (sceneReference.isEmpty()) {
        return
      }

      val sceneName = sceneReference.substring(sceneReference.indexOf('/') + 1)

      // Record mapping from id's to class types
      for (view in element) {
        if (!view.tagName.contains(".")) {
          // For now, limit search to custom views
          continue
        }
        val id = stripIdPrefix(view.getAttributeNS(ANDROID_URI, ATTR_ID))
        if (id.isNotEmpty()) {
          val list =
            sceneIds
              ?: run {
                val list = ArrayList<SceneReference>()
                sceneIds = list
                list
              }
          list += SceneReference(view.tagName, id, sceneName)
        }
      }
    } else {
      assert(context.resourceFolderType == XML)

      // Did any layouts reference this scene?
      val ids = sceneIds ?: return

      // CustomAttribute
      val attribute = element.getAttributeNodeNS(MOTION_LAYOUT_URI, "attributeName")
      attribute ?: return

      val attributeName = attribute.value
      val parent = element.parentNode as? Element ?: return
      val parentId = stripIdPrefix(parent.getAttributeNS(ANDROID_URI, ATTR_ID))
      val sceneName = getBaseName(context.file.name)

      for (s in ids) {
        if (parentId == s.id && s.scene == sceneName) {
          val viewClass = s.viewClass
          val uastParser = context.client.getUastParser(context.project)
          val evaluator = uastParser.evaluator
          val targetClass = evaluator.findClass(viewClass) ?: continue
          val methodName = getMethodName("set", attributeName)
          val methods = targetClass.findMethodsByName(methodName, true)

          for (m in methods) {
            if (m.parameterList.parametersCount == 1) {
              var owner: PsiModifierListOwner? = m
              while (owner != null) {
                val modifierList = owner.modifierList
                if (modifierList != null) {
                  //noinspection ExternalAnnotations
                  for (annotation in modifierList.annotations) {
                    if (KEEP_ANNOTATION.isEquals(annotation.qualifiedName)) {
                      return
                    }
                  }
                }
                owner = PsiTreeUtil.getParentOfType(owner, PsiModifierListOwner::class.java, true)
              }

              // Only flag these warnings if minifyEnabled is true in at least one
              // variant?
              if (!isShrinking(context)) {
                return
              }

              val location = context.getValueLocation(attribute)
              val javaContext =
                JavaContext(
                  context.driver,
                  context.project,
                  context.project,
                  VfsUtilCore.virtualToIoFile(targetClass.containingFile.virtualFile),
                )
              location.withSecondary(
                uastParser.getLocation(javaContext, m),
                "" +
                  "This method is accessed via reflection from a " +
                  "MotionScene ($sceneName) so it should be " +
                  "annotated with `@Keep` to ensure that it is not " +
                  "discarded or renamed in release builds",
                selfExplanatory = true,
              )
              // TODO: Add a quickfix here (which should be trivial except
              // that the lint fix verifier does not handle fixes in different
              // files.)
              val incident =
                Incident(
                  MISSING_KEEP,
                  element,
                  location,
                  "" +
                    "This attribute references a method or property in " +
                    "custom view $viewClass which is not annotated with " +
                    "`@Keep`; it should be annotated with `@Keep` to " +
                    "ensure that it is not discarded or renamed in " +
                    "release builds",
                )
              context.report(incident, map())
            }
          }
        }
      }
    }
  }

  override fun filterIncident(context: Context, incident: Incident, map: LintMap): Boolean {
    return isShrinking(context)
  }

  companion object {
    private const val MOTION_LAYOUT_URI = AUTO_URI

    val KEEP_ANNOTATION = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "Keep")

    private val IMPLEMENTATION =
      Implementation(
        ObjectAnimatorDetector::class.java,
        Scope.JAVA_AND_RESOURCE_FILES,
        Scope.JAVA_FILE_SCOPE,
      )

    /** Missing @Keep. */
    @JvmField
    val MISSING_KEEP =
      Issue.create(
        id = "AnimatorKeep",
        briefDescription = "Missing @Keep for Animated Properties",
        explanation =
          """
                    When you use property animators, properties can be accessed via reflection. \
                    Those methods should be annotated with @Keep to ensure that during release \
                    builds, the methods are not potentially treated as unused and removed, or \
                    treated as internal only and get renamed to something shorter.

                    This check will also flag other potential reflection problems it encounters, \
                    such as a missing property, wrong argument types, etc.
                    """,
        category = Category.PERFORMANCE,
        priority = 4,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    /** Incorrect ObjectAnimator binding. */
    @JvmField
    val BROKEN_PROPERTY =
      Issue.create(
        id = "ObjectAnimatorBinding",
        briefDescription = "Incorrect ObjectAnimator Property",
        explanation =
          """
                    This check cross references properties referenced by String from \
                    `ObjectAnimator` and `PropertyValuesHolder` method calls and ensures that \
                    the corresponding setter methods exist and have the right signatures.
                    """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )
  }
}
