/*
 * Copyright (C) 2012 The Android Open Source Project
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

import com.android.AndroidXConstants.INT_DEF_ANNOTATION
import com.android.AndroidXConstants.LONG_DEF_ANNOTATION
import com.android.AndroidXConstants.STRING_DEF_ANNOTATION
import com.android.AndroidXConstants.SUPPORT_ANNOTATIONS_PREFIX
import com.android.SdkConstants.ATTR_VALUE
import com.android.SdkConstants.FQCN_SUPPRESS_LINT
import com.android.SdkConstants.FQCN_TARGET_API
import com.android.SdkConstants.TYPE_DEF_FLAG_ATTRIBUTE
import com.android.support.AndroidxName
import com.android.tools.lint.checks.EmptySuperDetector.Companion.EMPTY_SUPER_ANNOTATION
import com.android.tools.lint.checks.OpenForTestingDetector.Companion.OPEN_FOR_TESTING_ANNOTATION
import com.android.tools.lint.checks.RestrictToDetector.Companion.VISIBILITY_PRIVATE
import com.android.tools.lint.checks.RestrictToDetector.Companion.VISIBILITY_PROTECTED
import com.android.tools.lint.checks.RestrictToDetector.Companion.getVisibility
import com.android.tools.lint.checks.RestrictToDetector.Companion.getVisibilityNotForTesting
import com.android.tools.lint.checks.ReturnThisDetector.Companion.RETURN_THIS_ANNOTATION
import com.android.tools.lint.checks.TypedefDetector.Companion.ATTR_OPEN
import com.android.tools.lint.checks.TypedefDetector.Companion.findTypeDef
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.isPlatformAnnotation
import com.android.tools.lint.client.api.AndroidPlatformAnnotations.Companion.toAndroidxAnnotation
import com.android.tools.lint.client.api.TYPE_BYTE
import com.android.tools.lint.client.api.TYPE_DOUBLE
import com.android.tools.lint.client.api.TYPE_FLOAT
import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_LONG
import com.android.tools.lint.client.api.TYPE_SHORT
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.ExtensionSdk.Companion.MAX_PLATFORM_SDK_ID
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.ResourceEvaluator.COLOR_INT_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.DIMENSION_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.PX_ANNOTATION
import com.android.tools.lint.detector.api.ResourceEvaluator.RES_SUFFIX
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.findLastAssignment
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationBooleanValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationLongValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValue
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getAnnotationStringValues
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getDoubleAttribute
import com.android.tools.lint.detector.api.UastLintUtils.Companion.getLongAttribute
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_API_ANNOTATION
import com.android.tools.lint.detector.api.VersionChecks.Companion.REQUIRES_EXTENSION_ANNOTATION
import com.android.tools.lint.detector.api.getAutoBoxedType
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.typeFromPsi
import com.google.common.collect.Lists
import com.google.common.collect.Maps
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteral
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSwitchLabelStatement
import com.intellij.psi.PsiTypes
import java.util.Locale
import kotlin.math.abs
import org.jetbrains.kotlin.asJava.elements.KtLightField
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UBinaryExpressionWithType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclarationsExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USwitchClauseExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.namePsiElement
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Checks annotations to make sure they are valid */
class AnnotationDetector : Detector(), SourceCodeScanner {
  /**
   * Set of fields we've already warned about [.FLAG_STYLE] for; these can be referenced multiple
   * times, so we should only flag them once
   */
  private var warnedFlags: MutableSet<PsiElement>? = null

  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UAnnotation::class.java, USwitchExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler {
    return AnnotationChecker(context)
  }

  private inner class AnnotationChecker(private val context: JavaContext) : UElementHandler() {
    override fun visitAnnotation(node: UAnnotation) {
      val type = node.qualifiedName ?: return
      if (type == JAVA_ANNOTATION_TARGET_FQN) {
        checkJavaTarget(node)
      }
      if (type.startsWith("java.lang.")) return
      checkAnnotation(node, type)
    }

    private fun checkJavaTarget(node: UAnnotation) {
      // Using java.lang.annotation.Target on a Kotlin annotation is wrong.
      // The IDE Kotlin plugin already gives a warning about this, but we want
      // to enforce this from the command line as an actual error because it
      // has more dire consequences than suggested (specifying *both* actually
      // causes the kotlin @Target attribute to be ignored, and the java @Target
      // also breaks Java access to the annotation). See issue 207151948.
      if (!isKotlin(node.lang)) {
        return
      }
      val nameElement = node.namePsiElement
      val location =
        if (nameElement != null) {
          context.getLocation(nameElement)
        } else {
          context.getLocation(node)
        }
      val annotated = node.getParentOfType<UAnnotated>(true) ?: return
      val hasTarget =
        context.evaluator.getAllAnnotations(annotated).any {
          it.qualifiedName == KOTLIN_ANNOTATION_TARGET_FQN
        }
      val fix: LintFix
      val message =
        if (hasTarget) {
          fix = fix().replace().all().with("").range(context.getLocation(node)).build()
          "Do not use `@java.lang.annotation.Target` here; it will cause the annotation to not be allowed on **any** element " +
            "types from Java"
        } else {
          fix =
            fix()
              .replace()
              .text(JAVA_ANNOTATION_TARGET_FQN)
              .with("Target")
              .range(context.getLocation(node))
              .build()
          "Use `@kotlin.annotation.Target`, not `@java.lang.annotation.Target` here; these targets will be ignored from Kotlin " +
            "and the annotation will not be allowed on **any** element types from Java"
        }
      // TODO: Use new issue type? This isn't really the right one.
      val incident =
        Incident(ANNOTATION_USAGE, node, location, message, fix).overrideSeverity(Severity.ERROR)
      context.report(incident)
    }

    private fun checkAnnotation(annotation: UAnnotation, type: String) {
      if (FQCN_SUPPRESS_LINT == type) {
        checkSuppressAnnotation(annotation)
      } else if (SUPPORT_ANNOTATIONS_PREFIX.isPrefix(type)) {
        checkAndroidxAnnotation(type, annotation)
      } else {
        checkTypedefAnnotation(annotation, type)
        if (isPlatformAnnotation(type)) {
          if (type == FQCN_TARGET_API) {
            checkTargetApiAnnotation(annotation)
          } else {
            checkAnnotation(annotation, toAndroidxAnnotation(type))
          }
        }
      }
    }

    private fun checkTargetApiAnnotation(annotation: UAnnotation) {
      val value = annotation.attributeValues.firstOrNull()?.sourcePsi?.text ?: ""
      var message = "Use `@RequiresApi($value) instead of `@TargetApi` to propagate the requirement"
      val owner = annotation.uastParent
      if (owner is UMethod) {
        message += " to callers of `${owner.name}`"
      } else if (owner is UVariable) {
        message += " to accessors of `${owner.name}`"
      } else if (owner is UClass) {
        @Suppress("UElementAsPsi")
        message += " to users of `${owner.name}`"
      }

      val fix =
        fix()
          .name("Replace with `@RequiresApi`")
          .replace()
          .pattern(annotation.namePsiElement?.text ?: "(?:android\\.annotation\\.)?TargetApi")
          .with(REQUIRES_API_ANNOTATION.newName())
          .shortenNames()
          .autoFix(true, true)
          .build()

      context.report(
        USE_REQUIRES_API,
        annotation,
        context.getNameLocation(annotation),
        message,
        fix,
      )
    }

    private fun checkSuppressAnnotation(annotation: UAnnotation) {
      val parent = skipParenthesizedExprUp(annotation.uastParent) ?: return
      // Only flag local variables and parameters (not classes, fields and methods)
      if (
        parent !is UDeclarationsExpression && parent !is ULocalVariable && parent !is UParameter
      ) {
        return
      }

      val attributes = annotation.attributeValues
      if (attributes.size == 1) {
        val attribute = attributes[0]
        val value = attribute.expression.skipParenthesizedExprDown()
        if (value is ULiteralExpression) {
          val v = value.value
          if (v is String) {
            checkSuppressLint(annotation, v)
          }
        } else if (value != null && value.isArrayInitializer()) {
          for (element in (value as UCallExpression).valueArguments) {
            val ex = element.skipParenthesizedExprDown()
            if (ex is ULiteralExpression) {
              val v = ex.value
              if (v is String) {
                if (!checkSuppressLint(annotation, v)) {
                  return
                }
              }
            }
          }
        }
      }
    }

    private fun checkAndroidxAnnotation(type: String, annotation: UAnnotation) {
      when {
        CHECK_RESULT_ANNOTATION.isEquals(type) -> {
          // Check that the return type of this method is not void!
          val parent = skipParenthesizedExprUp(annotation.uastParent)
          if (parent is UMethod) {
            if (!parent.isConstructor && PsiTypes.voidType() == parent.returnType) {
              context.report(
                ANNOTATION_USAGE,
                annotation,
                context.getLocation(annotation),
                "@CheckResult should not be specified on `void` methods",
              )
            }
          }
        }
        INT_RANGE_ANNOTATION.isEquals(type) || FLOAT_RANGE_ANNOTATION.isEquals(type) -> {
          // Check that the annotated element's type is int or long.
          // Also make sure that from <= to.
          val invalid: Boolean =
            if (INT_RANGE_ANNOTATION.isEquals(type)) {
              checkTargetType(annotation, type, TYPE_INT, TYPE_LONG)
              val from = getLongAttribute(context, annotation, ATTR_FROM, Long.MIN_VALUE)
              val to = getLongAttribute(context, annotation, ATTR_TO, Long.MAX_VALUE)
              from > to
            } else {
              checkTargetType(annotation, type, TYPE_FLOAT, TYPE_DOUBLE)
              val from =
                getDoubleAttribute(context, annotation, ATTR_FROM, Double.NEGATIVE_INFINITY)
              val to = getDoubleAttribute(context, annotation, ATTR_TO, Double.POSITIVE_INFINITY)
              from > to
            }
          if (invalid) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "Invalid range: the `from` attribute must be less than " + "the `to` attribute",
            )
          }
        }
        SIZE_ANNOTATION.isEquals(type) -> {
          // Check that the annotated element's type is an array, or a collection
          // (or at least not an int or long; if so, suggest IntRange)
          // Make sure the size and the modulo is not negative.
          val unset = -42
          val exact = getLongAttribute(context, annotation, ATTR_VALUE, unset.toLong())
          val min = getLongAttribute(context, annotation, ATTR_MIN, Long.MIN_VALUE)
          val max = getLongAttribute(context, annotation, ATTR_MAX, Long.MAX_VALUE)
          val multiple = getLongAttribute(context, annotation, ATTR_MULTIPLE, 1)
          if (min > max) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "Invalid size range: the `min` attribute must be less than " + "the `max` attribute",
            )
          } else if (multiple < 1) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "The size multiple must be at least 1",
            )
          } else if (exact < 0 && exact != unset.toLong() || min < 0 && min != Long.MIN_VALUE) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "The size can't be negative",
            )
          }
        }
        GRAVITY_INT_ANNOTATION.isEquals(type) -> {
          // Check that @GravityInt applies to the right type
          checkTargetType(annotation, type, TYPE_INT, TYPE_LONG)
        }
        COLOR_INT_ANNOTATION.isEquals(type) -> {
          // Check that @ColorInt applies to the right type
          checkTargetType(annotation, type, TYPE_INT, TYPE_LONG)
        }
        DIMENSION_ANNOTATION.isEquals(type) || PX_ANNOTATION.isEquals(type) -> {
          // Check that @Dimension and @Px applies to the right type
          checkTargetType(annotation, type, TYPE_INT, TYPE_LONG, TYPE_FLOAT, TYPE_DOUBLE)
        }
        INT_DEF_ANNOTATION.isEquals(type) || LONG_DEF_ANNOTATION.isEquals(type) -> {
          // Make sure IntDef constants are unique
          ensureUniqueValues(annotation)
        }
        PERMISSION_ANNOTATION.isEquals(type) -> {
          // Check that if there are no arguments, this is specified on a parameter,
          // and conversely, on methods and fields there is a valid argument.
          val parent = skipParenthesizedExprUp(annotation.uastParent)
          if (parent is UMethod) {
            val value = getAnnotationStringValue(annotation, ATTR_VALUE)
            val anyOf = getAnnotationStringValues(annotation, ATTR_ANY_OF)
            val allOf = getAnnotationStringValues(annotation, ATTR_ALL_OF)
            var set = 0
            if (value != null) {
              set++
            }
            if (allOf != null) {
              set++
            }
            if (anyOf != null) {
              set++
            }
            if (set == 0) {
              context.report(
                ANNOTATION_USAGE,
                annotation,
                context.getLocation(annotation),
                "For methods, permission annotation should specify one " +
                  "of `value`, `anyOf` or `allOf`",
              )
            } else if (set > 1) {
              context.report(
                ANNOTATION_USAGE,
                annotation,
                context.getLocation(annotation),
                "Only specify one of `value`, `anyOf` or `allOf`",
              )
            }
          }
        }
        HALF_FLOAT_ANNOTATION.isEquals(type) -> {
          // Check that half floats are on shorts
          checkTargetType(annotation, type, TYPE_SHORT)
        }
        type.endsWith(RES_SUFFIX) -> {
          // Check that resource type annotations are on ints
          checkTargetType(annotation, type, TYPE_INT, TYPE_LONG)
        }
        RESTRICT_TO_ANNOTATION.isEquals(type) -> {
          checkConstructorParameter(annotation, type)

          val attributeValue =
            annotation.findDeclaredAttributeValue(ATTR_VALUE)
              ?: annotation.findDeclaredAttributeValue(null)
              ?: run {
                context.report(
                  ANNOTATION_USAGE,
                  annotation,
                  context.getLocation(annotation),
                  "Restrict to what? Expected at least one `RestrictTo.Scope` arguments.",
                )
                return
              }
          val values = attributeValue.asSourceString()
          if (
            values.contains("SUBCLASSES") &&
              skipParenthesizedExprUp(annotation.uastParent) is UClass
          ) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "`RestrictTo.Scope.SUBCLASSES` should only be specified on methods and fields",
            )
          }
        }
        VISIBLE_FOR_TESTING_ANNOTATION.isEquals(type) -> {
          checkConstructorParameter(annotation, type)
          checkVisibleForTestingAnnotation(annotation)
        }
        REQUIRES_API_ANNOTATION.isEquals(type) -> {
          checkRequiresApi(annotation)
        }
        type == REQUIRES_EXTENSION_ANNOTATION -> {
          checkRequiresExtension(annotation)
        }
        type == EMPTY_SUPER_ANNOTATION -> {
          // Pointless on final methods
          val parent = skipParenthesizedExprUp(annotation.uastParent)
          if (parent is UMethod) {
            if (parent.isFinal) {
              context.report(
                ANNOTATION_USAGE,
                annotation,
                context.getLocation(annotation),
                "`@EmptySuper` is pointless on a final method",
              )
            }
          }
          // We don't warn if this method body isn't empty because you can legitimately
          // put code here, for example, backwards compatibility code meant to handle
          // cases where people used to call super and you now want to complain if they do
        }
        type == OPEN_FOR_TESTING_ANNOTATION -> {
          // Make sure on Kotlin and method or class
          if (!isKotlin(annotation.lang)) {
            context.report(
              ANNOTATION_USAGE,
              annotation,
              context.getLocation(annotation),
              "`@OpenForTesting` only applies to Kotlin APIs",
            )
          }
        }
        type == RETURN_THIS_ANNOTATION -> {
          // Type must be non-void and non-primitive
          val parent = skipParenthesizedExprUp(annotation.uastParent)
          if (parent is UMethod) {
            val returnType = parent.returnType
            if (
              !parent.isConstructor &&
                (PsiTypes.voidType() == returnType || returnType is PsiPrimitiveType)
            ) {
              context.report(
                ANNOTATION_USAGE,
                annotation,
                context.getLocation(annotation),
                "`@ReturnThis` should not be specified on `void` or primitive methods",
              )
            }
          }
        }
      }
    }

    private fun checkRequiresExtension(annotation: UAnnotation) {
      val holder = annotation.uastParent as? UAnnotated ?: return

      // Make sure that
      // (1) you specify both an SDK level and a version, and
      // (2) that there are no gaps between the highest API level and the lowest API level
      //     specified on the same element, and that
      // (3) the version numbers for each API level is >= the previous API level
      // (4) there's no duplication of API levels, and
      // (5) possibly ensure that there's a RequiresApi level for the highest number
      //     (or that there isn't duplication between the two)

      var foundFirst = false
      val levels = mutableListOf<Triple<UAnnotation, Int, Int>>()
      //noinspection ExternalAnnotations
      for (a in holder.uAnnotations) {
        val qualifiedName = a.qualifiedName
        if (qualifiedName == REQUIRES_EXTENSION_ANNOTATION) {
          if (!foundFirst) {
            // Since we're testing interactions here, only report this for the
            // *first* requires extension since otherwise we'll duplicate warnings.
            if (a != annotation) {
              return
            }
            foundFirst = true
          }

          val sdkVersion = getAnnotationLongValue(a, "extension", -1).toInt()
          if (sdkVersion == -1) {
            val location = context.getLocation(a)
            context.report(
              ANNOTATION_USAGE,
              a,
              location,
              "Must specify an extension `sdk` id attribute",
            )
            return
          }
          val extensionVersion = getAnnotationLongValue(a, "version", -1).toInt()
          if (extensionVersion == -1) {
            val location = context.getLocation(a)
            context.report(
              ANNOTATION_USAGE,
              a,
              location,
              "Must specify an extension `version` level attribute",
            )
            return
          }
          // Report error if either one is -1 (unspecified)
          levels.add(Triple(a, sdkVersion, extensionVersion))
        } else if (REQUIRES_API_ANNOTATION.isEquals(qualifiedName)) {
          val sdkVersion =
            getAnnotationLongValue(a, "api", -1L)
              .let { if (it != -1L) it else getAnnotationLongValue(a, ATTR_VALUE, -1L) }
              .toInt()
          if (sdkVersion == -1) {
            // Already validated elsewhere (in checkRequiresApi)
            return
          }
          levels.add(Triple(a, sdkVersion, 0))
        }
      }

      // The contiguous region expectation only applies to platform extension back-ports;
      // those have id's less than 1000000.
      levels.removeIf { it.second >= MAX_PLATFORM_SDK_ID }
      levels.sortedWith(compareBy({ -it.second }, { it.first.qualifiedName }))

      var prev: Triple<UAnnotation, Int, Int>? = null
      for (triple in levels) {
        if (prev != null) {
          if (prev.second - triple.second > 1 && WARN_ABOUT_EXTENSION_LEVEL_GAPS) {
            // Report gap
            val location =
              getAttributeValueLocation(context, triple.first, "extension")
                ?: context.getLocation(triple.first)
            location.secondary =
              getAttributeValueLocation(context, prev.first, "extension")
                ?: context.getLocation(prev.first)
            location.secondary?.setMessage("Previous level", false)
            context.report(
              ANNOTATION_USAGE,
              triple.first,
              location,
              "There should not be a gap in SDK extension levels; missing ${prev.second - 1}",
            )
            return
          } else if (
            triple.second == prev.second && triple.first.qualifiedName == prev.first.qualifiedName
          ) {
            // Duplicate - only okay if qualified names are different (e.g. specifying
            // both @RequiresApi and @RequiresExtension
            val location =
              getAttributeValueLocation(context, triple.first, "extension")
                ?: context.getLocation(triple.first)
            location.secondary =
              getAttributeValueLocation(context, prev.first, "extension")
                ?: context.getLocation(prev.first)
            location.secondary?.setMessage("Previous level", false)
            context.report(
              ANNOTATION_USAGE,
              triple.first,
              location,
              "Repeated SDK extension level ${triple.second}",
            )
          } else if (
            prev.third > triple.third &&
              triple.second != prev.second &&
              triple.first.qualifiedName == REQUIRES_EXTENSION_ANNOTATION
          ) {
            val location =
              getAttributeValueLocation(context, triple.first, "version")
                ?: context.getLocation(triple.first)
            location.secondary =
              getAttributeValueLocation(context, prev.first, "version")
                ?: context.getLocation(prev.first)
            location.secondary?.setMessage("Previous version", false)
            context.report(
              ANNOTATION_USAGE,
              triple.first,
              location,
              "Suspicious extension level; expect previous extension versions to be at least as high as later SDK extension",
            )
          }
        }
        prev = triple
      }
    }

    private fun getAttributeValueLocation(
      context: JavaContext,
      annotation: UAnnotation,
      name: String,
    ): Location? {
      val attribute = annotation.findDeclaredAttributeValue(name) ?: return null
      return context.getLocation(attribute)
    }

    private fun checkRequiresApi(annotation: UAnnotation) {
      if (annotation.attributeValues.isEmpty()) {
        val name = "Specify API level"
        val fix = LintFix.create().name(name).replace().end().with("(TODO)").select("TODO").build()
        val location = context.getLocation(annotation)
        context.report(ANNOTATION_USAGE, annotation, location, "Must specify an API level", fix)
      }
    }

    private fun checkVisibleForTestingAnnotation(annotation: UAnnotation) {
      val parentElement = skipParenthesizedExprUp(annotation.uastParent)?.sourcePsi
      if (parentElement !is PsiClass) {
        return
      }
      if (parentElement.getParentOfType<PsiClass>(strict = true) != null) {
        return
      }
      val visibility =
        getVisibilityNotForTesting(annotation, getVisibility(parentElement as PsiMember))

      if (visibility == VISIBILITY_PRIVATE || visibility == VISIBILITY_PROTECTED) {
        context.report(
          ANNOTATION_USAGE,
          annotation,
          context.getLocation(annotation),
          "Top level class can't have private or protected access level",
        )
      }
    }

    private fun checkConstructorParameter(annotation: UAnnotation, type: String) {
      val source = annotation.sourcePsi
      if (source is KtAnnotationEntry) {
        val parameter = source.parent?.parent as? KtParameter ?: return
        if (!parameter.hasValOrVar()) {
          return
        }
        val target = source.useSiteTarget?.getAnnotationUseSiteTarget()
        if (target == null || target == AnnotationUseSiteTarget.CONSTRUCTOR_PARAMETER) {
          val name = type.substringAfterLast('.')
          val fix =
            if (target == null) {
              fix().name("Change to `@get:`").replace().text("@").with("@get:").build()
            } else {
              null
            }

          context.report(
            ANNOTATION_USAGE,
            annotation,
            context.getLocation(annotation),
            "Did you mean `@get:$name`? Without `get:` this annotates the constructor " +
              "parameter itself instead of the associated getter.",
            fix,
          )
        }
      }
    }

    private fun checkTypedefAnnotation(annotation: UAnnotation, type: String) {
      // Look for typedefs (and make sure they're specified on the right type)
      val cls = annotation.resolve() ?: return
      if (cls.isAnnotationType && cls.modifierList != null) {
        for (a in context.evaluator.getAnnotations(cls, false)) {
          var name = a.qualifiedName ?: continue
          if (isPlatformAnnotation(name)) {
            name = toAndroidxAnnotation(name)
          }
          if (INT_DEF_ANNOTATION.isEquals(name)) {
            checkTargetType(annotation, name, TYPE_INT)
          } else if (LONG_DEF_ANNOTATION.isEquals(name)) {
            checkTargetType(annotation, name, TYPE_LONG)
          } else if (STRING_DEF_ANNOTATION.isEquals(type)) {
            checkTargetType(annotation, name, TYPE_STRING)
          }
        }
      }
    }

    private fun checkTargetType(
      node: UAnnotation,
      name: String,
      type1: String,
      type2: String? = null,
      type3: String? = null,
      type4: String? = null,
      allowCollection: Boolean = true,
    ) {
      val parent = skipParenthesizedExprUp(node.uastParent)
      val parentType =
        if (parent is UDeclarationsExpression) {
          val elements = parent.declarations
          if (elements.isNotEmpty()) {
            val element = elements[0]
            if (element is ULocalVariable) {
              element.typeFromPsi
            } else {
              return
            }
          } else {
            return
          }
        } else if (parent is UMethod) {
          if (parent.isConstructor)
            context.evaluator.getClassType(parent.getContainingUClass()?.javaPsi)
          else parent.returnType
        } else if (parent is UVariable) {
          // Field or local variable or parameter
          if (parent.typeReference == null) {
            // Uh oh.
            // https://youtrack.jetbrains.com/issue/KT-20172
            return
          }
          parent.type
        } else {
          return
        }
      var type = parentType ?: return
      var originalType = type

      if (
        type is PsiClassType &&
          type.getCanonicalText().startsWith("kotlin.properties.ReadWriteProperty")
      ) {
        var unknownDelegateType = true
        if (parent is UVariable) {
          val parameters = type.parameters
          if (parameters.isNotEmpty()) {
            type = parameters[parameters.size - 1]
            unknownDelegateType = false
            // Normally we use originalType in the error message, but here since
            // it's
            // misleading use the actual type instead
            originalType = type
          }
        }
        if (unknownDelegateType) {
          return
        }
      }
      if (allowCollection) {
        if (type is PsiArrayType) {
          // For example, int[]
          type = type.getDeepComponentType()
        } else if (type is PsiClassType) {
          val parameters = type.parameters
          if (parameters.isNotEmpty()) {
            type = parameters[0]
          }
        }
      }
      if (!type.isValid) {
        return
      }
      var typeName = type.canonicalText
      when (typeName) {
        "error.NonExistentClass" -> {
          // Type not found. Not awesome.
          // https://youtrack.jetbrains.com/issue/KT-20172
          return
        }
        "android.util.SparseIntArray" -> {
          typeName = "int"
        }
        "android.util.LongSparseArray",
        "android.util.SparseLongArray" -> {
          typeName = "long"
        }
        "android.util.SparseBooleanArray" -> {
          typeName = "boolean"
        }
      }
      if (!(typeName == type1 || typeName == type2 || typeName == type3 || typeName == type4)) {
        // Autoboxing? You can put @DrawableRes on a java.lang.Integer for example
        if (
          typeName == getAutoBoxedType(type1) ||
            type2 != null && typeName == getAutoBoxedType(type2) ||
            type3 != null && typeName == getAutoBoxedType(type3) ||
            type4 != null && typeName == getAutoBoxedType(type4)
        ) {
          return
        }

        // Allow flexibly mixing convertible types here, e.g. we may have int constants
        // but packed into byte or short arrays.
        // We're doing custom checks here rather than passing them in as allowed types
        // since we don't want to suggest them in the error messages.
        if (
          (typeName == TYPE_BYTE ||
            typeName == getAutoBoxedType(TYPE_BYTE) ||
            typeName == TYPE_SHORT ||
            typeName == getAutoBoxedType(TYPE_SHORT)) && (type1 == TYPE_INT || type1 == TYPE_LONG)
        ) {
          return
        } else if (
          type2 == null &&
            type1 == TYPE_INT &&
            (typeName == TYPE_LONG || typeName == getAutoBoxedType(TYPE_LONG))
        ) {
          return
        } else if (
          type2 == null &&
            type1 == TYPE_LONG &&
            (typeName == TYPE_INT || typeName == getAutoBoxedType(TYPE_INT))
        ) {
          return
        }
        val expectedTypes: String =
          if (type4 != null) {
            "$type1, $type2, $type3, or $type4"
          } else if (type3 != null) {
            "$type1, $type2, or $type3"
          } else if (type2 != null) {
            "$type1 or $type2"
          } else {
            type1
          }

        // When displaying the incorrect type, use the original type (e.g. "int[]") instead
        // of the checked inner type (such as the list element or array element type)
        typeName = originalType.canonicalText
        val isString = typeName == TYPE_STRING
        if (isString) {
          typeName = "String"
        }
        var message = "This annotation does not apply for type $typeName; expected $expectedTypes"
        if (isString && type1 == TYPE_INT && INT_DEF_ANNOTATION.isEquals(name)) {
          message += ". Should `${node.asSourceString()}` be annotated with `@StringDef` instead?"
        }
        val location = context.getLocation(node)
        context.report(ANNOTATION_USAGE, node, location, message)
      }
    }

    override fun visitSwitchExpression(node: USwitchExpression) {
      val condition = node.expression
      if (condition != null && PsiTypes.intType() == condition.getExpressionType()) {
        val annotation = findIntDefAnnotation(condition)
        if (annotation != null) {
          val value =
            annotation.findAttributeValue(ATTR_VALUE)?.skipParenthesizedExprDown()
              ?: annotation.findAttributeValue(null)?.skipParenthesizedExprDown()
              ?: return
          if (value.isArrayInitializer()) {
            val open = getAnnotationBooleanValue(annotation, ATTR_OPEN, false)
            if (open) {
              return
            }
            val allowedValues = (value as UCallExpression).valueArguments
            node.accept(SwitchChecker(node, allowedValues))
          }
        }
      }
    }

    /** Searches for the corresponding @IntDef annotation definition associated with a given node */
    private fun findIntDefAnnotation(expression: UExpression): UAnnotation? {
      if (expression is UReferenceExpression) {
        val resolved = expression.resolve()
        if (resolved is PsiModifierListOwner) {
          val annotation = findTypeDef(expression, resolved)
          if (annotation != null) {
            return annotation
          }
        }
        if (resolved is PsiLocalVariable) {
          val lastAssignment = findLastAssignment(resolved, expression)
          if (lastAssignment != null) {
            return findIntDefAnnotation(lastAssignment)
          }
        }
      } else if (expression is UCallExpression) {
        val method = expression.resolve()
        if (method != null) {
          val annotation = findTypeDef(expression, method)
          if (annotation != null) {
            return annotation
          }
        }
      } else if (expression is UIfExpression) {
        if (expression.thenExpression != null) {
          val result = findIntDefAnnotation(expression.thenExpression!!)
          if (result != null) {
            return result
          }
        }
        if (expression.elseExpression != null) {
          val result = findIntDefAnnotation(expression.elseExpression!!)
          if (result != null) {
            return result
          }
        }
      } else if (expression is UBinaryExpressionWithType) {
        return findIntDefAnnotation(expression.operand)
      } else if (expression is UParenthesizedExpression) {
        return findIntDefAnnotation(expression.expression)
      }
      return null
    }

    private fun findTypeDef(expression: UExpression, owner: PsiModifierListOwner): UAnnotation? {
      val evaluator = context.evaluator
      val annotations = evaluator.getAnnotations(owner, true)
      val uAnnotations =
        evaluator.filterRelevantAnnotations(
          annotations,
          expression,
          setOf(INT_DEF_ANNOTATION.oldName(), INT_DEF_ANNOTATION.newName()),
        )
      return findTypeDef(uAnnotations)
    }

    private fun getConstantValue(intDefConstantRef: PsiField): Int? {
      val constant = intDefConstantRef.computeConstantValue()
      return (constant as? Number)?.toInt()
    }

    private fun ensureUniqueValues(node: UAnnotation) {
      val value =
        node.findDeclaredAttributeValue(ATTR_VALUE)?.skipParenthesizedExprDown()
          ?: node.findDeclaredAttributeValue(null)?.skipParenthesizedExprDown()
          ?: return

      if (!value.isArrayInitializer()) {
        return
      }
      val initializers = (value as UCallExpression).valueArguments
      val valueToIndex: MutableMap<Number, Int> = Maps.newHashMapWithExpectedSize(initializers.size)
      val flag = getAnnotationBooleanValue(node, TYPE_DEF_FLAG_ATTRIBUTE) === java.lang.Boolean.TRUE
      if (flag) {
        ensureUsingFlagStyle(initializers)
      }
      val constantEvaluator = ConstantEvaluator()
      for (index in initializers.indices) {
        val expression = initializers[index].skipParenthesizedExprDown()
        val number = constantEvaluator.evaluate(expression) as? Number ?: continue
        if (valueToIndex.containsKey(number)) {
          val message: String
          val prevLocationLabel: String
          val prevIndex = valueToIndex[number] ?: continue
          val prevConstant = initializers[prevIndex]
          val constant1 = expression!!.asSourceString()
          val constant2 = prevConstant.asSourceString()
          if (constant1 == constant2) {
            message = "Constant `$constant1` has already been included"
            prevLocationLabel = "Previous occurrence"
          } else {
            var valueString = number.toString()

            // Try to use the string from the source code if it's available since
            // we'd like to use the same number format (e.g. hex or decimal)
            if (expression is UReferenceExpression) {
              val resolved = expression.resolve()
              if (resolved is PsiField) {
                var prevField: PsiField? = null
                if (prevConstant is UReferenceExpression) {
                  val resolvePrev = prevConstant.resolve()
                  if (resolvePrev is PsiField) {
                    prevField = resolvePrev
                    if (resolved.name == prevField.name) {
                      // cls1.FIELD_NAME == cls2.FIELD_NAME; probably
                      // setting up aliases.
                      return
                    }
                  }
                }
                val initializer = UastFacade.getInitializerBody(resolved)
                if (initializer is ULiteralExpression) {
                  val source = initializer.sourcePsi
                  if (source != null) {
                    valueString = source.text
                  }
                } else if (initializer is UReferenceExpression && prevField != null) {
                  val referencedField = initializer.resolve()
                  if (referencedField != null && referencedField.isEquivalentTo(prevField)) {
                    // This new reference is deliberately aliased to the
                    // same previous reference.
                    return
                  }
                }
                if (prevField != null && isDeprecated(resolved) != isDeprecated(prevField)) {
                  return
                }
              }
            }
            message =
              "Constants `$constant1` and `$constant2` specify the same exact " +
                "value ($valueString); this is usually a cut & paste or " +
                "merge error"
            prevLocationLabel = "Previous same value"
          }
          val location: Location = context.getLocation(expression)
          val secondary = context.getLocation(prevConstant)
          secondary.message = prevLocationLabel
          location.secondary = secondary
          val scope = getAnnotationScope(node)
          context.report(UNIQUE, scope, location, message)
          break
        }
        valueToIndex[number] = index
      }
    }

    @Suppress("ExternalAnnotations")
    private fun isDeprecated(field: PsiField): Boolean {
      if (field is KtLightField) {
        val uField = field.toUElement() as UField?
        if (uField != null) {
          val annotations = uField.uAnnotations
          return annotations.any {
            val name = it.qualifiedName
            name == "java.lang.Deprecated" || name == "kotlin.Deprecated"
          }
        }
      }
      return field.hasAnnotation("java.lang.Deprecated") || field.hasAnnotation("kotlin.Deprecated")
    }

    private fun ensureUsingFlagStyle(constants: List<UExpression>) {
      if (constants.size < 3) {
        return
      }
      val oneBitConstants: MutableList<Triple<UExpression, PsiElement, Number>> = ArrayList()
      for (constant in constants) {
        if (constant is UReferenceExpression) {
          val resolved = constant.resolve()
          // Don't try to check complied code.
          if (resolved !is PsiCompiledElement && resolved is PsiField) {
            val initializer =
              UastFacade.getInitializerBody(resolved)?.skipParenthesizedExprDown()
                as? ULiteralExpression ?: continue
            val o = initializer.value as? Number ?: continue
            val value = o.toLong()
            // Allow -1, 0 and 1. You can write 1 as "1 << 0" but IntelliJ for
            // example warns that that's a redundant shift.
            if (abs(value) <= 1) {
              continue
            }
            // Only warn if we're setting a specific bit
            if (java.lang.Long.bitCount(value) != 1) {
              // return rather than continue:
              // We have at least one constant which doesn't fit this format;
              // don't suggest shifting some constants if not all constants can be
              // represented that way
              return
            }
            oneBitConstants.add(Triple<UExpression, PsiElement, Number>(initializer, resolved, o))
          }
        }
      }
      for (triple in oneBitConstants) {
        val initializer = triple.component1()
        val resolved = triple.component2()
        val o = triple.component3()
        val value = o.toLong()
        val shift = java.lang.Long.numberOfTrailingZeros(value)
        if (warnedFlags == null) {
          warnedFlags = mutableSetOf()
        }
        if (!warnedFlags!!.add(resolved)) {
          return
        }
        val operator = if (isKotlin(resolved.language)) "shl" else "<<"
        val message =
          String.format(
            Locale.US,
            "Consider declaring this constant using 1 %s %d instead",
            operator,
            shift,
          )
        val replace =
          String.format(Locale.ROOT, "1%s %s %d", if (o is Long) "L" else "", operator, shift)
        val fix =
          fix()
            .replace()
            .sharedName("Change declaration to $operator")
            .with(replace)
            .autoFix()
            .build()
        val location = context.getLocation(initializer)
        context.report(FLAG_STYLE, initializer, location, message, fix)
      }
    }

    private fun checkSuppressLint(node: UAnnotation, id: String): Boolean {
      val registry = context.driver.registry
      val issue = registry.getIssue(id)
      // Special-case the ApiDetector issue, since it does both source file analysis
      // only on field references, and class file analysis on the rest, so we allow
      // annotations outside of methods only on fields
      if (
        issue != null && !issue.implementation.scope.contains(Scope.JAVA_FILE) ||
          issue === ApiDetector.UNSUPPORTED
      ) {
        // This issue doesn't have AST access: annotations are not
        // available for local variables or parameters
        val scope = getAnnotationScope(node)
        context.report(
          INSIDE_METHOD,
          scope,
          context.getLocation(node),
          "The `@SuppressLint` annotation cannot be used on a local " +
            "variable with the lint check '$id': move out to the " +
            "surrounding method",
        )
        return false
      }
      return true
    }

    private inner class SwitchChecker(
      private val switchExpression: USwitchExpression,
      allowedValues: List<UExpression>,
    ) : AbstractUastVisitor() {
      private val allowedValues: List<UExpression>?
      private val fields: MutableList<Any>
      private val seenValues: MutableList<Int?>
      private var reported = false

      override fun visitSwitchClauseExpression(node: USwitchClauseExpression): Boolean {
        if (reported) {
          return true
        }
        if (allowedValues == null) {
          return true
        }
        val caseValues = node.caseValues
        if (caseValues.isEmpty()) {
          // We had an else clause: don't report any as missing
          fields.clear()
          return true
        }
        for (caseValue in caseValues) {
          if (caseValue is ULiteralExpression) {
            // Report warnings if you specify hardcoded constants.
            // It's the wrong thing to do.
            val list = computeFieldNames(switchExpression, allowedValues)
            val message = "Don't use a constant here; expected one of: ${displayConstants(list)}"
            context.report(SWITCH_TYPE_DEF, caseValue, context.getLocation(caseValue), message)
            // Don't look for other missing typedef constants since you might
            // have aliased with value
            reported = true
          } else if (caseValue is UReferenceExpression) { // default case can have null expression
            var resolved: PsiElement? =
              caseValue.resolve()
                // If there are compilation issues (e.g. user is editing code) we
                // can't be certain, so don't flag anything.
                ?: return true
            if (resolved is PsiField) {
              // We can't just do
              //    fields.remove(resolved);
              // since the fields list contains instances of potentially
              // different types with different hash codes (due to the
              // external annotations, which are not of the same type as
              // for example the ECJ based ones.
              //
              // The equals method on external field class deliberately handles
              // this (but it can't make its hash code match what
              // the ECJ fields do, which is tied to the ECJ binding hash code.)
              // So instead, manually check for equals. These lists tend to
              // be very short anyway.
              var found = removeFieldFromList(fields, resolved)
              if (!found) {
                // Look for local alias
                val initializer =
                  UastFacade.getInitializerBody(resolved)?.skipParenthesizedExprDown()
                if (initializer is UReferenceExpression) {
                  resolved = initializer.resolve()
                  if (resolved is PsiField) {
                    found = removeFieldFromList(fields, resolved)
                  }
                }
              }
              if (found) {
                val cv = getConstantValue(resolved as PsiField)
                if (cv != null) {
                  seenValues.add(cv)
                }
              } else {
                // Sanity check in case the default statement does not appear last
                if (
                  resolved != null && allowedValues.any { resolved.isEquivalentTo(it.tryResolve()) }
                ) {
                  return true
                }

                val list = computeFieldNames(switchExpression, allowedValues)
                val message = "Unexpected constant; expected one of: ${displayConstants(list)}"
                val fix = fix().data(KEY_CASES, list)
                val location = context.getNameLocation(caseValue)
                context.report(SWITCH_TYPE_DEF, caseValue, location, message, fix)
              }
            }
          } else {
            val sourcePsi = caseValue.sourcePsi
            if (sourcePsi is PsiSwitchLabelStatement && sourcePsi.isDefaultCase) {
              fields.clear()
              return true
            }
          }
        }
        return true
      }

      override fun afterVisitSwitchExpression(node: USwitchExpression) {
        reportMissingSwitchCases()
        super.afterVisitSwitchExpression(node)
      }

      private fun reportMissingSwitchCases() {
        if (reported) {
          return
        }
        if (allowedValues == null) {
          return
        }

        // Any missing switch constants? Before we flag them, look to see if any
        // of them have the same values: those can be omitted
        if (fields.isNotEmpty()) {
          val iterator = fields.listIterator()
          while (iterator.hasNext()) {
            val next = iterator.next()
            if (next is PsiField) {
              val cv = getConstantValue(next)
              if (seenValues.contains(cv)) {
                iterator.remove()
              }
            }
          }
        }
        if (fields.isNotEmpty()) {
          val list = computeFieldNames(switchExpression, fields)
          val fix = fix().data(KEY_CASES, list)
          val identifier = switchExpression.switchIdentifier
          var location = context.getLocation(identifier)
          // Workaround Kotlin UAST passing <error> instead of PsiKeyword as in Java
          if (switchExpression.sourcePsi is KtWhenExpression && "when" != identifier.name) {
            val sourcePsi = switchExpression.sourcePsi as KtWhenExpression
            val keyword = sourcePsi.firstChild
            if (keyword != null) {
              location = context.getLocation(keyword)
            }
          }
          val message =
            "Switch statement on an `int` with known associated constant missing case " +
              displayConstants(list)
          context.report(SWITCH_TYPE_DEF, switchExpression, location, message, fix)
        }
      }

      init {
        this.allowedValues = allowedValues
        fields = Lists.newArrayListWithCapacity(allowedValues.size)
        for (allowedValue in allowedValues) {
          if (allowedValue is UReferenceExpression) {
            val resolved = allowedValue.resolve()
            if (resolved != null) {
              fields.add(resolved)
            }
          } else if (allowedValue is ULiteralExpression) {
            fields.add(allowedValue)
          }
        }
        seenValues = Lists.newArrayListWithCapacity(allowedValues.size)
      }
    }
  }

  private fun displayConstants(list: List<String>): String {
    return list.joinToString(
      ", ", // separator
      "", // prefix
      "", // postfix
      -1, // limited
      "", // truncated
    ) { s: String ->
      val index = s.lastIndexOf('.')
      if (index != -1) {
        val classIndex = s.lastIndexOf('.', index - 1)
        if (classIndex != -1) {
          return@joinToString "`" + s.substring(classIndex + 1) + "`"
        }
      }
      "`$s`"
    }
  }

  private fun computeFieldNames(node: USwitchExpression, allowedValues: Iterable<*>): List<String> {
    val list = mutableListOf<String>()
    for (allowedValue in allowedValues) {
      var o = allowedValue
      if (o is PsiReferenceExpression) {
        val ref = o
        val resolved = ref.resolve()
        o =
          if (resolved != null) {
            resolved
          } else {
            val referenceName = ref.referenceName
            if (referenceName != null) {
              list.add(referenceName)
            }
            continue
          }
      } else if (o is PsiLiteral) {
        list.add(o.value.toString())
        continue
      } else if (o is UReferenceExpression) {
        val ref = o
        val resolved = ref.resolve()
        if (resolved == null) {
          val resolvedName = ref.resolvedName
          if (resolvedName != null) {
            list.add(resolvedName)
          }
          continue
        }
        o = resolved
      }
      if (o is PsiField) {
        val field = o
        // Only include class name if necessary
        var name = field.name
        val clz = node.getParentOfType(UClass::class.java, true)
        if (clz != null) {
          val containingClass = field.containingClass
          if (containingClass != null && !containingClass.isEquivalentTo(clz.javaPsi)) {
            name = containingClass.qualifiedName + '.' + field.name
          }
        }
        list.add(name)
      }
    }
    list.sort()
    return list
  }

  /**
   * Returns the node to use as the scope for the given annotation node. You can't annotate an
   * annotation itself (with `@SuppressLint`), but you should be able to place an annotation next to
   * it, as a sibling, to only suppress the error on this annotated element, not the whole
   * surrounding class.
   */
  private fun getAnnotationScope(node: UAnnotation): UElement =
    node.getParentOfType(UAnnotation::class.java, true) ?: node

  private fun removeFieldFromList(fields: List<Any>, resolvedField: PsiField): Boolean {
    for (field in fields) {
      // We can't just call .equals here because the annotation
      // we are comparing against may be either a PsiFieldImpl
      // (for a local annotation) or a ClsFieldImpl (for an annotation
      // read from storage) or maybe even other PSI internal classes.
      // So compare by name and class instead.
      if (field !is PsiField) {
        continue
      }
      if (field.isEquivalentTo(resolvedField)) {
        return true
      }
    }
    return false
  }

  companion object {
    const val KEY_CASES = "cases"
    const val ATTR_SUGGEST = "suggest"
    const val ATTR_TO = "to"
    const val ATTR_FROM = "from"
    const val ATTR_FROM_INCLUSIVE = "fromInclusive"
    const val ATTR_TO_INCLUSIVE = "toInclusive"
    const val ATTR_MULTIPLE = "multiple"
    const val ATTR_MIN = "min"
    const val ATTR_MAX = "max"
    const val ATTR_ALL_OF = "allOf"
    const val ATTR_ANY_OF = "anyOf"
    const val ATTR_CONDITIONAL = "conditional"
    private const val JAVA_ANNOTATION_TARGET_FQN = "java.lang.annotation.Target"
    private const val KOTLIN_ANNOTATION_TARGET_FQN = "kotlin.annotation.Target"

    /**
     * Feature temporarily disabled while we settle whether we expect people to specify all
     * extension levels or whether specifying the lowest one impliest the other dessert extensions.
     */
    const val WARN_ABOUT_EXTENSION_LEVEL_GAPS = false

    val IMPLEMENTATION = Implementation(AnnotationDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Placing SuppressLint on a local variable doesn't work for class-file based checks */
    @JvmField
    val INSIDE_METHOD =
      create(
        id = "LocalSuppress",
        briefDescription = "@SuppressLint on invalid element",
        explanation =
          """
                The `@SuppressAnnotation` is used to suppress Lint warnings in Java files. However, while \
                many lint checks analyzes the Java source code, where they can find annotations on \
                (for example) local variables, some checks are analyzing the `.class` files. And in class \
                files, annotations only appear on classes, fields and methods. Annotations placed on local \
                variables disappear. If you attempt to suppress a lint error for a class-file based lint \
                check, the suppress annotation not work. You must move the annotation out to the \
                surrounding method.
                """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** Incorrectly using a support annotation */
    @JvmField
    val ANNOTATION_USAGE =
      create(
        id = "SupportAnnotationUsage",
        briefDescription = "Incorrect support annotation usage",
        explanation =
          """
                This lint check makes sure that the support annotations (such as `@IntDef` and `@ColorInt`) \
                are used correctly. For example, it's an error to specify an `@IntRange` where the `from` \
                value is higher than the `to` value.
                """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
      )

    /** IntDef annotations should be unique */
    @JvmField
    val UNIQUE =
      create(
        id = "UniqueConstants",
        briefDescription = "Overlapping Enumeration Constants",
        explanation =
          """
                The `@IntDef` annotation allows you to create a light-weight "enum" or type definition. \
                However, it's possible to accidentally specify the same value for two or more of the \
                values, which can lead to hard-to-detect bugs. This check looks for this scenario and \
                flags any repeated constants.

                In some cases, the repeated constant is intentional (for example, renaming a constant to \
                a more intuitive name, and leaving the old name in place for compatibility purposes).  In \
                that case, simply suppress this check by adding a `@SuppressLint("UniqueConstants")` \
                annotation.
                """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    /** Flags should typically be specified as bit shifts */
    @JvmField
    val FLAG_STYLE =
      create(
        id = "ShiftFlags",
        briefDescription = "Dangerous Flag Constant Declaration",
        explanation =
          """
                When defining multiple constants for use in flags, the recommended style is to use the \
                form `1 << 2`, `1 << 3`, `1 << 4` and so on to ensure that the constants are unique and \
                non-overlapping.
                """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
      )

    /** All IntDef constants should be included in switch */
    @JvmField
    val SWITCH_TYPE_DEF =
      create(
        id = "SwitchIntDef",
        briefDescription = "Missing @IntDef in Switch",
        explanation =
          """
                This check warns if a `switch` statement does not explicitly include all the values \
                declared by the typedef `@IntDef` declaration.
                """,
        category = Category.CORRECTNESS,
        priority = 3,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )

    /** Using TargetApi instead of RequiresApi */
    @JvmField
    val USE_REQUIRES_API =
      create(
        id = "UseRequiresApi",
        briefDescription = "Use `@RequiresApi` instead of `@TargetApi`",
        explanation =
          """
          The `@TargetApi` annotation only *suppresses* API warnings locally. `@RequiresApi` \
          on the other hand will propagate the requirement out to any *callers* of this API, \
          making sure that they either perform API level checks (using for example `SDK_INT`), \
          or defining `@RequiresApi` annotations themselves.

          (The `@TargetApi` annotation predates `@RequiresApi`, and was introduced as an early \
          way to *suppress* lint API warnings for a particular API level. Accidentally using \
          `@TargetApi` can lead to crashes since there is no check that other calls to this \
          method actually check that the call is safe.)
          """,
        category = Category.CORRECTNESS,
        priority = 2,
        severity = Severity.WARNING,
        implementation = IMPLEMENTATION,
        androidSpecific = true,
      )
  }
}

// Well-known Annotation names

const val SECURITY_EXCEPTION = "java.lang.SecurityException"

@JvmField
val CHECK_RESULT_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "CheckResult")
@JvmField
val UI_THREAD_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "UiThread")
@JvmField
val MAIN_THREAD_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "MainThread")
@JvmField
val WORKER_THREAD_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "WorkerThread")
@JvmField
val BINDER_THREAD_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "BinderThread")
@JvmField
val ANY_THREAD_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "AnyThread")
@JvmField
val VISIBLE_FOR_TESTING_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "VisibleForTesting")
@JvmField
val HALF_FLOAT_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "HalfFloat")
@JvmField val SIZE_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "Size")
@JvmField
val FLOAT_RANGE_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "FloatRange")
@JvmField
val RESTRICT_TO_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RestrictTo")
@JvmField
val INT_RANGE_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "IntRange")
@JvmField
val PERMISSION_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RequiresPermission")
@JvmField
val PERMISSION_ANNOTATION_READ: AndroidxName = AndroidxName.of(PERMISSION_ANNOTATION, "Read")
@JvmField
val PERMISSION_ANNOTATION_WRITE: AndroidxName = AndroidxName.of(PERMISSION_ANNOTATION, "Write")
@JvmField
val REQUIRES_FEATURE_ANNOTATION: AndroidxName =
  AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "RequiresFeature")
@JvmField
val GRAVITY_INT_ANNOTATION: AndroidxName = AndroidxName.of(SUPPORT_ANNOTATIONS_PREFIX, "GravityInt")
