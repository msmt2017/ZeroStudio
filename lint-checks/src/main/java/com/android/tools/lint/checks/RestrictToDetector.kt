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

import com.android.SdkConstants.ATTR_VALUE
import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationOrigin
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_LHS
import com.android.tools.lint.detector.api.AnnotationUsageType.ASSIGNMENT_RHS
import com.android.tools.lint.detector.api.AnnotationUsageType.CLASS_REFERENCE_AS_IMPLICIT_DECLARATION_TYPE
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.isKotlin
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.compiled.ClsAnnotationImpl
import com.intellij.psi.util.PsiTypesUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UTypeReferenceExpression
import org.jetbrains.uast.UUnknownExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.getContainingUFile
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.util.isArrayInitializer

class RestrictToDetector : AbstractAnnotationDetector(), SourceCodeScanner {
  override fun applicableAnnotations(): List<String> =
    listOf(RESTRICT_TO_ANNOTATION.oldName(), RESTRICT_TO_ANNOTATION.newName(), "VisibleForTesting")

  override fun inheritAnnotation(annotation: String): Boolean {
    // Require restriction annotations to be annotated everywhere
    return false
  }

  override fun isApplicableAnnotationUsage(type: AnnotationUsageType): Boolean {
    return type != ASSIGNMENT_LHS &&
      type != ASSIGNMENT_RHS &&
      super.isApplicableAnnotationUsage(type)
  }

  override fun visitAnnotationUsage(
    context: JavaContext,
    element: UElement,
    annotationInfo: AnnotationInfo,
    usageInfo: AnnotationUsageInfo,
  ) {
    val type = usageInfo.type
    if (type == AnnotationUsageType.EXTENDS && element is UTypeReferenceExpression) {
      // If it's a constructor reference we don't need to also check the type
      // reference. Ideally we'd do a "parent is KtConstructorCalleeExpression"
      // here, but that points to impl classes in its hierarchy which leads to
      // class loading trouble.
      val sourcePsi = element.sourcePsi
      if (isKotlin(element.lang) && sourcePsi?.parent?.toString() == "CONSTRUCTOR_CALLEE") {
        return
      }
    }

    val member = usageInfo.referenced as? PsiMember
    val annotation = annotationInfo.annotation
    val qualifiedName = annotationInfo.qualifiedName
    if (RESTRICT_TO_ANNOTATION.isEquals(qualifiedName)) {
      checkRestrictTo(context, element, member, annotation, usageInfo, true)
    } else if (
      qualifiedName.endsWith(VISIBLE_FOR_TESTING_SUFFIX) &&
        member != null &&
        type != AnnotationUsageType.METHOD_OVERRIDE &&
        type != AnnotationUsageType.METHOD_CALL_PARAMETER
    ) {
      checkVisibleForTesting(context, element, member, annotation, usageInfo)
    }
  }

  private fun isTestContext(context: JavaContext, element: UElement): Boolean {
    var current = element
    // (1) Is this compilation unit in a test source path?
    if (context.isTestSource) {
      return true
    }

    // (2) Is this AST node surrounded by a test-only annotation?
    // (Compose Preview annotations also imply a test context)
    while (true) {
      val owner = current.getParentOfType<UAnnotated>(true) ?: break

      if (owner.isTestContext()) {
        return true
      }

      current = owner
    }

    return false
  }

  private fun UAnnotated.isTestContext(): Boolean {
    var isCompose = false

    //noinspection AndroidLintExternalAnnotations
    val annotations = this.uAnnotations

    for (annotation in annotations) {
      val name = annotation.qualifiedName ?: continue
      when {
        RESTRICT_TO_ANNOTATION.isEquals(name) -> {
          val restrictionScope = getRestrictionScope(annotation)
          if (restrictionScope and RESTRICT_TO_TESTS != 0) {
            return true
          }
        }
        name.endsWith(VISIBLE_FOR_TESTING_SUFFIX) -> return true
        name.endsWith(".TestOnly") -> return true
        name == COMPOSABLE_ANNOTATION -> isCompose = true
      }
    }

    if (isCompose) {
      // Look for preview annotations or multi-preview annotations
      //noinspection AndroidLintExternalAnnotations
      for (annotation in annotations) {
        val name = annotation.qualifiedName ?: continue
        when (name) {
          COMPOSE_PREVIEW,
          COMPOSE_DESKTOP_PREVIEW -> return true
          else -> {
            val cls = annotation.resolve()
            if (cls != null) {
              for (nested in cls.annotations) {
                when (nested.qualifiedName ?: continue) {
                  COMPOSE_PREVIEW,
                  COMPOSE_DESKTOP_PREVIEW -> return true
                }
              }
            }
          }
        }
      }
    }

    return false
  }

  private fun checkVisibleForTesting(
    context: JavaContext,
    node: UElement,
    member: PsiMember,
    annotation: UAnnotation,
    usageInfo: AnnotationUsageInfo,
  ) {
    val visibility = getVisibilityNotForTesting(annotation, getVisibility(member))
    if (visibility == VISIBILITY_NONE) { // not the default
      checkRestrictTo(context, node, member, annotation, usageInfo, RESTRICT_TO_TESTS)
    } else {
      // Check that the target method is available
      // (1) private is available in the same compilation unit
      // (2) package private is available in the same package
      // (3) protected is available either from subclasses or in same package

      val uFile = node.getContainingUFile()
      val containingFile1 = UastLintUtils.getPsiFile(uFile)
      val containingFile2 = UastLintUtils.getContainingFile(member)
      if (containingFile1 == containingFile2 || containingFile2 == null) {
        // Same compilation unit
        return
      }

      // Validity check (since Kotlin UAST creates several light classes around
      // PSI files that sometimes fail equality tests)
      if (containingFile1?.virtualFile == containingFile2.virtualFile) {
        return
      }

      if (visibility == VISIBILITY_PRIVATE) {
        if (!isTestContext(context, node)) {
          reportVisibilityError(context, node, annotation, usageInfo, "private")
        }
        return
      }

      val evaluator = context.evaluator
      val pkg = evaluator.getPackage(node)
      val methodPackage = evaluator.getPackage(member)
      // can't compare pkg == methodPackage because PsiPackageImpl#equals only returns
      // true for other instances of the exact same class
      if (pkg?.qualifiedName == methodPackage?.qualifiedName) {
        // Same package
        return
      }
      if (visibility == VISIBILITY_PACKAGE_PRIVATE) {
        if (!isTestContext(context, node)) {
          reportVisibilityError(context, node, annotation, usageInfo, "package private")
        }
        return
      }

      assert(visibility == VISIBILITY_PROTECTED)

      val methodClass = member.containingClass
      val thisClass = node.getParentOfType(UClass::class.java, true)?.javaPsi
      if (thisClass == null || methodClass == null) {
        return
      }
      val qualifiedName = methodClass.qualifiedName
      if (qualifiedName == null || evaluator.inheritsFrom(thisClass, qualifiedName, false)) {
        return
      }

      if (!isTestContext(context, node)) {
        reportVisibilityError(context, node, annotation, usageInfo, "protected")
      }
    }
  }

  private fun reportVisibilityError(
    context: JavaContext,
    node: UElement,
    annotation: UAnnotation,
    usageInfo: AnnotationUsageInfo,
    desc: String,
  ) {
    val type =
      when (node) {
        is UTypeReferenceExpression -> "class"
        else -> "method"
      }
    val message =
      when (usageInfo.type) {
        CLASS_REFERENCE_AS_IMPLICIT_DECLARATION_TYPE -> {
          val typeText = (usageInfo.referenced as? PsiClass)?.name
          "This declaration implicitly references ${typeText?.plus(",") ?: "a type"} " +
            "which should only be accessed from tests or within $desc scope"
        }
        else -> "This $type should only be accessed from tests or within $desc scope"
      }
    val location: Location =
      if (node is UCallExpression) {
        context.getCallLocation(node, false, false)
      } else {
        context.getLocation(node)
      }

    val issue =
      when (annotation.qualifiedName) {
        INTELLIJ_VISIBLE_FOR_TESTING_ANNOTATION -> TEST_VISIBILITY_INTELLIJ
        else -> TEST_VISIBILITY
      }

    report(context, issue, node, location, message)
  }

  // TODO: Test XML access of restricted classes
  private fun checkRestrictTo(
    context: JavaContext,
    node: UElement,
    method: PsiMember?,
    annotation: UAnnotation,
    usageInfo: AnnotationUsageInfo,
    applyClassAnnotationsToMembers: Boolean = true,
  ) {
    val scope = getRestrictionScope(annotation)
    if (scope != 0) {
      checkRestrictTo(
        context,
        node,
        method,
        annotation,
        usageInfo,
        scope,
        applyClassAnnotationsToMembers,
      )
    }
  }

  private fun checkRestrictTo(
    context: JavaContext,
    node: UElement,
    member: PsiMember?,
    annotation: UAnnotation,
    usageInfo: AnnotationUsageInfo,
    scope: Int,
    applyClassAnnotationsToMembers: Boolean = true,
  ) {

    val containingClass =
      when {
        node is UTypeReferenceExpression -> PsiTypesUtil.getPsiClass(node.type)
        member != null -> member.containingClass
        node is UCallExpression -> node.classReference?.resolve() as? PsiClass?
        node is UClass -> node.javaPsi
        else -> null
      }

    containingClass ?: return

    val annotations = usageInfo.annotations
    if (!applyClassAnnotationsToMembers) {
      val origin = annotations[usageInfo.index].origin
      if (member is PsiClass) {
        if (origin != AnnotationOrigin.CLASS) {
          return
        }
      } else if (origin != AnnotationOrigin.METHOD && origin != AnnotationOrigin.FIELD) {
        return
      }
    }

    if (
      usageInfo.anyCloser {
        it.qualifiedName == RESTRICT_TO_ANNOTATION.oldName() ||
          it.qualifiedName == RESTRICT_TO_ANNOTATION.newName()
      }
    ) {
      return
    }

    if (scope and RESTRICT_TO_LIBRARY_GROUP != 0 && member != null) {
      val evaluator = context.evaluator
      val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
      val methodCoordinates =
        evaluator.getLibrary(member)
          ?: run {
            if (thisCoordinates != null && member !is PsiCompiledElement) {
              // Local source?
              context.evaluator.getProject(member)?.mavenCoordinate
            } else {
              null
            }
          }
      val thisGroup = thisCoordinates?.groupId
      val methodGroup = methodCoordinates?.groupId
      if (thisGroup != methodGroup && methodGroup != null) {
        val thisGroupDisplayText = thisGroup ?: "<unknown>"
        val where =
          "from within the same library group (referenced groupId=`$methodGroup` from groupId=`$thisGroupDisplayText`)"
        reportRestriction(where, containingClass, member, context, node, usageInfo)
      }
    } else if (scope and RESTRICT_TO_LIBRARY_GROUP_PREFIX != 0 && member != null) {
      val evaluator = context.evaluator
      val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
      val methodCoordinates =
        evaluator.getLibrary(member)
          ?: run {
            if (thisCoordinates != null && member !is PsiCompiledElement) {
              // Local source?
              context.evaluator.getProject(member)?.mavenCoordinate
            } else {
              null
            }
          }
      val thisGroup = thisCoordinates?.groupId
      val methodGroup = methodCoordinates?.groupId
      if (
        methodGroup != null &&
          (thisGroup == null || !sameLibraryGroupPrefix(thisGroup, methodGroup))
      ) {
        val expectedPrefix =
          methodGroup.lastIndexOf('.').let {
            if (it < 0) {
              "\"\""
            } else {
              methodGroup.substring(0, it)
            }
          }
        val where =
          "from within the same library group prefix (referenced groupId=`$methodGroup` with prefix $expectedPrefix${if (thisGroup != null) " from groupId=`$thisGroup`" else ""})"
        reportRestriction(where, containingClass, member, context, node, usageInfo)
      }
    } else if (scope and RESTRICT_TO_LIBRARY != 0 && member != null) {
      val evaluator = context.evaluator
      val thisCoordinates = evaluator.getLibrary(node) ?: context.project.mavenCoordinate
      val methodCoordinates = evaluator.getLibrary(member)
      val thisGroup = thisCoordinates?.groupId
      val methodGroup = methodCoordinates?.groupId
      if (thisGroup != methodGroup && methodGroup != null) {
        val thisArtifact = thisCoordinates?.artifactId
        val methodArtifact = methodCoordinates.artifactId
        if (thisArtifact != methodArtifact) {
          val where = "from within the same library ($methodGroup:$methodArtifact)"
          reportRestriction(where, containingClass, member, context, node, usageInfo)
        }
      } else if (member !is PsiCompiledElement) {
        // If the resolved method is source, make sure they're part
        // of the same Gradle project
        val project = context.evaluator.getProject(member)
        if (project != null && project != context.project) {
          val coordinates = project.mavenCoordinate
          val name =
            if (coordinates != null) {
              "${coordinates.groupId}:${coordinates.artifactId}"
            } else {
              project.name
            }
          val where = "from within the same library ($name)"
          reportRestriction(where, containingClass, member, context, node, usageInfo)
        }
      }
    }

    if (scope and RESTRICT_TO_TESTS != 0) {
      if (!isTestContext(context, node)) {
        reportRestriction("from tests", containingClass, member, context, node, usageInfo)
      }
    }

    if (scope and RESTRICT_TO_SUBCLASSES != 0) {
      val qualifiedName = containingClass.qualifiedName
      if (qualifiedName != null) {
        val evaluator = context.evaluator

        var outer: UClass?
        var isSubClass = false
        var prev = node

        while (true) {
          outer = prev.getParentOfType(UClass::class.java, true)
          if (outer == null) {
            break
          }
          if (evaluator.inheritsFrom(outer.javaPsi, qualifiedName, false)) {
            isSubClass = true
            break
          }

          if (evaluator.isStatic(outer)) {
            break
          }
          prev = outer
        }

        if (!isSubClass) {
          reportRestriction("from subclasses", containingClass, member, context, node, usageInfo)
        }
      }
    }
  }

  private fun reportRestriction(
    where: String?,
    containingClass: PsiClass,
    member: PsiMember?,
    context: JavaContext,
    node: UElement,
    usageInfo: AnnotationUsageInfo,
  ) {
    var api: String
    api =
      if (member == null || member is PsiMethod && member.isConstructor) {
        member?.name ?: (containingClass.name + " constructor")
      } else
      //noinspection LintImplPsiEquals
      if (containingClass == member) {
        member.name ?: "class"
      } else {
        containingClass.name + "." + member.name
      }

    var locationNode = node
    if (node is UCallExpression) {
      val nameElement = node.methodIdentifier
      if (nameElement != null) {
        locationNode = nameElement
      }

      // If the annotation was reported on the class, and the left hand side
      // expression is that class, use it as the name node?
      val annotation = usageInfo.annotations[usageInfo.index]
      val annotated = annotation.annotated
      //noinspection LintImplPsiEquals
      if (where == null && annotated is PsiClass && annotated != usageInfo.referenced) {
        val qualifier = node.receiver
        val className = annotated.name
        if (qualifier != null && className != null && qualifier.asSourceString() == className) {
          locationNode = qualifier
          api = className
        }
      }
    }

    // If this error message changes, you need to also update ResourceTypeInspection#guessLintIssue
    var message: String
    if (where == null) {
      message = "$api is marked as internal and should not be accessed from apps"
    } else {
      val refType = if (member is PsiMethod) "called" else "accessed"
      message = "$api can only be $refType $where"

      // Most users will encounter this for the support library; let's have a clearer error message
      // for that specific scenario
      if (where == "from within the same library (groupId=com.android.support)") {
        // If this error message changes, you need to also update
        // ResourceTypeInspection#guessLintIssue
        message =
          "This API is marked as internal to the support library and should not be accessed from apps"
      }
    }

    val location =
      if (locationNode is UCallExpression) {
        context.getCallLocation(locationNode, false, false)
      } else {
        context.getLocation(locationNode)
      }
    report(context, RESTRICTED, node, location, message, null)
  }

  companion object {
    private val IMPLEMENTATION =
      Implementation(RestrictToDetector::class.java, Scope.JAVA_FILE_SCOPE)

    private const val INTELLIJ_VISIBLE_FOR_TESTING_ANNOTATION =
      "org.jetbrains.annotations.VisibleForTesting"

    private const val VISIBLE_FOR_TESTING_SUFFIX = ".VisibleForTesting"
    private const val ATTR_OTHERWISE = "otherwise"
    private const val ATTR_PRODUCTION_VISIBILITY = "productionVisibility"
    private const val ATTR_VISIBILITY = "visibility"
    private const val COMPOSABLE_ANNOTATION = "androidx.compose.runtime.Composable"
    private const val COMPOSE_PREVIEW = "androidx.compose.ui.tooling.preview.Preview"
    private const val COMPOSE_DESKTOP_PREVIEW =
      "androidx.compose.desktop.ui.tooling.preview.Preview"

    // Must match constants in @VisibleForTesting:
    const val VISIBILITY_PRIVATE = 2
    private const val VISIBILITY_PACKAGE_PRIVATE = 3
    const val VISIBILITY_PROTECTED = 4
    private const val VISIBILITY_NONE = 5
    private const val VISIBILITY_PUBLIC = 6

    // TODO: Kotlin "module" visibility

    fun getVisibility(member: PsiMember): Int {
      val defaultForLanguage =
        when (member.language) {
          is KotlinLanguage -> VISIBILITY_PUBLIC
          is JavaLanguage -> VISIBILITY_PACKAGE_PRIVATE
          else -> VISIBILITY_NONE
        }

      val modifierList = member.modifierList ?: return defaultForLanguage

      return when {
        modifierList.hasExplicitModifier(PsiModifier.PUBLIC) -> VISIBILITY_PUBLIC
        modifierList.hasExplicitModifier(PsiModifier.PROTECTED) -> VISIBILITY_PROTECTED
        modifierList.hasExplicitModifier(PsiModifier.PRIVATE) -> VISIBILITY_PRIVATE
        modifierList.hasExplicitModifier(PsiModifier.DEFAULT) -> defaultForLanguage
        else -> defaultForLanguage
      }
    }

    fun getVisibilityNotForTesting(annotation: UAnnotation, visibility: Int): Int {
      // The VisibleForTesting annotations from AndroidX and IntelliJ have slightly
      // different specs:
      //
      //  - org.jetbrains.annotations.VisibleForTesting implies that the production visibility
      //    is 'one step down' from the testing visibility. e.g. if the testing visibility
      //    is public, the production visibility is assumed to be package-private. There is no
      //    parameter to change what the production visibility is assumed to be.
      //
      //  - androidx.annotations.VisibleForTesting implies that the production visibility
      //    is private, unless explicitly specified with the "otherwise=" parameter.
      //
      //  - com.google.common.annotations.VisibleForTesting is the same, except
      //    its parameter is called "productionVisibility"
      //
      //  - and com.android.internal.annotations.VisibleForTesting is the same with its "visibility"
      // parameter
      //
      // Here we check which one we are dealing with and then specify behavior based on their specs.
      when (annotation.qualifiedName) {
        // The IntelliJ annotation
        INTELLIJ_VISIBLE_FOR_TESTING_ANNOTATION -> {
          return when (visibility) {
            VISIBILITY_PUBLIC -> VISIBILITY_PACKAGE_PRIVATE
            VISIBILITY_PROTECTED -> VISIBILITY_PACKAGE_PRIVATE
            VISIBILITY_PACKAGE_PRIVATE -> VISIBILITY_PRIVATE
            else -> VISIBILITY_PRIVATE // the default
          }
        }

        // The other annotations, which have parameters to specify production visibility
        else -> {
          val value =
            annotation.findDeclaredAttributeValue(ATTR_OTHERWISE)
              // Guava within Google3:
              ?: annotation.findDeclaredAttributeValue(ATTR_PRODUCTION_VISIBILITY)
              // Used in many android versions like
              // com.android.internal.annotations.VisibleForTesting
              ?: annotation.findDeclaredAttributeValue(ATTR_VISIBILITY)
          if (value is ULiteralExpression) {
            val v = value.value
            if (v is Int) {
              return v
            }
          } else if (value is UReferenceExpression) {
            // Not compiled; this is unlikely (but can happen when editing the support
            // library project itself)
            when (value.resolvedName) {
              "NONE" -> return VISIBILITY_NONE
              "PRIVATE" -> return VISIBILITY_PRIVATE
              "PROTECTED" -> return VISIBILITY_PROTECTED
              "PACKAGE_PRIVATE" -> return VISIBILITY_PACKAGE_PRIVATE
              "PACKAGE" -> return VISIBILITY_PACKAGE_PRIVATE
            }
          } else if (value is UUnknownExpression) {
            // Workaround for https://youtrack.jetbrains.com/issue/KT-47290 -- see
            // https://issuetracker.google.com/190113936 for an applicable scenario
            val sourcePsi = value.sourcePsi
            if (sourcePsi is PsiLiteralExpression) {
              val v = sourcePsi.value
              if (v is Int) {
                return v
              }
            }
          } else if (value is UastEmptyExpression) {
            // Some kind of error in UAST; try harder. JavaUAnnotation is used to wrap
            // class file annotations and in findDeclaredAttributeValue it returns
            // UastEmptyExpression if it cannot convert it to UAST.
            // (This may be an older version of KT-47290; it doesn't seem to trigger
            // from the tests anymore)
            val psi = annotation.sourcePsi
            if (psi is ClsAnnotationImpl) {
              val otherwise =
                psi.findAttribute(ATTR_OTHERWISE)
                  ?: psi.findAttribute(ATTR_PRODUCTION_VISIBILITY)
                  ?: psi.findAttribute(ATTR_VISIBILITY)
              val v = otherwise?.attributeValue
              if (v is JvmAnnotationConstantValue) {
                val constant = v.constantValue
                if (constant is Number) {
                  return constant.toInt()
                }
              }
            }
          }
          return VISIBILITY_PRIVATE // the default
        }
      }
    }

    /** `RestrictTo(RestrictTo.Scope.GROUP_ID` */
    private const val RESTRICT_TO_LIBRARY_GROUP = 1 shl 0

    /** `RestrictTo(RestrictTo.Scope.GROUP_ID` */
    private const val RESTRICT_TO_LIBRARY = 1 shl 1

    /** `RestrictTo(RestrictTo.Scope.GROUP_ID` */
    private const val RESTRICT_TO_LIBRARY_GROUP_PREFIX = 1 shl 2

    /** `RestrictTo(RestrictTo.Scope.TESTS` */
    private const val RESTRICT_TO_TESTS = 1 shl 3

    /** `RestrictTo(RestrictTo.Scope.SUBCLASSES` */
    private const val RESTRICT_TO_SUBCLASSES = 1 shl 4

    private fun getRestrictionScope(annotation: UAnnotation): Int {
      val value = annotation.findDeclaredAttributeValue(ATTR_VALUE)
      if (value != null) {
        return getRestrictionScope(value, annotation)
      }
      return 0
    }

    private fun getRestrictionScope(expression: UExpression?, annotation: UAnnotation): Int {
      var scope = 0
      if (expression != null) {
        if (expression.isArrayInitializer()) {
          val initializerExpression = expression as UCallExpression?
          val initializers = initializerExpression!!.valueArguments
          for (initializer in initializers) {
            scope = scope or getRestrictionScope(initializer, annotation)
          }
        } else if (expression is UReferenceExpression) {
          val resolved = expression.resolve()
          if (resolved is PsiField) {
            val name = resolved.name
            if ("GROUP_ID" == name || "LIBRARY_GROUP" == name) {
              scope = scope or RESTRICT_TO_LIBRARY_GROUP
            } else if ("SUBCLASSES" == name) {
              scope = scope or RESTRICT_TO_SUBCLASSES
            } else if ("TESTS" == name) {
              scope = scope or RESTRICT_TO_TESTS
            } else if ("LIBRARY" == name) {
              scope = scope or RESTRICT_TO_LIBRARY
            } else if ("LIBRARY_GROUP_PREFIX" == name) {
              scope = scope or RESTRICT_TO_LIBRARY_GROUP_PREFIX
            }
          }
        } else if (expression is UastEmptyExpression) {
          // See JavaUAnnotation.findDeclaredAttributeValue
          val psi = annotation.sourcePsi
          if (psi is ClsAnnotationImpl) {
            val otherwise = psi.findAttribute(ATTR_VALUE)
            val v = otherwise?.attributeValue
            if (v is JvmAnnotationConstantValue) {
              val constant = v.constantValue
              if (constant is Number) {
                scope = scope or constant.toInt()
              }
            }
          }
        }
      }

      return scope
    }

    /**
     * Implements the group prefix equality that is described in the documentation for the
     * RestrictTo.Scope.LIBRARY_GROUP_PREFIX enum constant.
     */
    fun sameLibraryGroupPrefix(group1: String, group2: String): Boolean {
      // TODO: Allow group1.startsWith(group2) || group2.startsWith(group1) ?

      if (group1 == group2) {
        return true
      }

      val i1 = group1.lastIndexOf('.')
      val i2 = group2.lastIndexOf('.')
      if (i2 != i1 || i1 == -1) {
        return false
      }

      return group1.regionMatches(0, group2, 0, i1)
    }

    /** Using a restricted API. */
    @JvmField
    val RESTRICTED =
      Issue.create(
        id = "RestrictedApi",
        briefDescription = "Restricted API",
        explanation =
          """
                This API has been flagged with a restriction that has not been met.

                Examples of API restrictions:
                * Method can only be invoked by a subclass
                * Method can only be accessed from within the same library (defined by the Gradle library group id)
                * Method can only be accessed from tests.

                You can add your own API restrictions with the `@RestrictTo` annotation.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Using an intended-for-tests API */
    @JvmField
    val TEST_VISIBILITY =
      Issue.create(
        id = "VisibleForTests",
        briefDescription = "Visible Only For Tests",
        explanation =
          """
                This check looks for accesses from production code (e.g. not tests) where \
                the access would not have been allowed with the intended production \
                visibility.

                Depending on your development environment, you may be able to use an \
                `@VisibleForTesting` annotation to specify the intended visibility if \
                the method had not been more widely visible for the tests.

                When using `androidx.annotations.VisibleForTesting`, the production \
                visibility is assumed to be private unless specified with the `otherwise=` \
                parameter. `com.google.common.annotations.VisibleForTesting` and \
                `com.android.internal.annotations.VisibleForTesting` work similarly with \
                their own parameters called `productionVisibility` and `visibility`.

                If instead you use `org.jetbrains.annotations.VisibleForTesting`, there \
                is no such parameter, and the production visibility is instead assumed to be \
                "one step down" from the testing visibility. For example, if the testing \
                visibility is public, the production visibility is assumed to be package-private.
                """,
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val TEST_VISIBILITY_INTELLIJ =
      Issue.create(
        id = "VisibleForTests",
        briefDescription = "Visible Only For Tests",
        explanation =
          """
                This check looks for accesses from production code (e.g. not tests) where \
                the access would not have been allowed if its visibility was not relaxed for \
                testing purposes.""",
        category = Category.CORRECTNESS,
        priority = 4,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )
  }
}
