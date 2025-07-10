/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.SdkConstants.VALUE_TRUE
import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.BooleanOption
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.nameFromSource
import com.android.tools.lint.detector.api.typeFromPsi
import com.android.utils.usLocaleCapitalize
import com.android.utils.usLocaleDecapitalize
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.types.renderers.KaFlexibleTypeRenderer
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDynamicType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaStarTypeProjection
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.types.Variance
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UAnonymousClass
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParameter
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getContainingUMethod
import org.jetbrains.uast.getParentOfType

/**
 * Checks for issues around creating APIs that make it harder to interoperate between Java and
 * Kotlin code.
 *
 * See https://android.github.io/kotlin-guides/interop.html .
 */
class InteroperabilityDetector : Detector(), SourceCodeScanner {
  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(InteroperabilityDetector::class.java, Scope.JAVA_FILE_SCOPE)

    val IGNORE_DEPRECATED =
      VALUE_TRUE == System.getenv("ANDROID_LINT_NULLNESS_IGNORE_DEPRECATED") ||
        VALUE_TRUE == System.getProperty("lint.nullness.ignore-deprecated")

    @JvmField
    val NO_HARD_KOTLIN_KEYWORDS =
      Issue.create(
        id = "NoHardKeywords",
        briefDescription = "No Hard Kotlin Keywords",
        explanation =
          """
            Do not use Kotlin’s hard keywords as the name of methods or fields. \
            These require the use of backticks to escape when calling from Kotlin. \
            Soft keywords, modifier keywords, and special identifiers are allowed.

            For example, ActionEvent's `getWhen()` method requires backticks when used from Kotlin:
            ```kotlin
            val timestamp = event.`when`
            ```
            """,
        moreInfo = "https://android.github.io/kotlin-guides/interop.html#no-hard-keywords",
        category = Category.INTEROPERABILITY_KOTLIN,
        priority = 6,
        severity = Severity.WARNING,
        enabledByDefault = false,
        implementation = IMPLEMENTATION,
      )

    @JvmField
    val LAMBDA_LAST =
      Issue.create(
        id = "LambdaLast",
        briefDescription = "Lambda Parameters Last",
        explanation =
          """
            To improve calling this code from Kotlin, \
            parameter types eligible for SAM conversion should be last.
            """,
        moreInfo = "https://android.github.io/kotlin-guides/interop.html#lambda-parameters-last",
        category = Category.INTEROPERABILITY_KOTLIN,
        priority = 6,
        severity = Severity.WARNING,
        enabledByDefault = false,
        implementation = IMPLEMENTATION,
      )

    private val CHECK_DEPRECATED =
      BooleanOption(
        "ignore-deprecated",
        "Whether to ignore classes and members that have been annotated with `@Deprecated`",
        false,
        """
                Normally this lint check will flag all unannotated elements, but by \
                setting this option to `true` it will skip any deprecated elements.
                """,
      )

    @JvmField
    val PLATFORM_NULLNESS =
      Issue.create(
          id = "UnknownNullness",
          briefDescription = "Unknown nullness",
          explanation =
            """
                To improve referencing this code from Kotlin, consider adding \
                explicit nullness information here with either `@NonNull` or `@Nullable`.
            """,
          moreInfo = "https://developer.android.com/kotlin/interop#nullability_annotations",
          category = Category.INTEROPERABILITY_KOTLIN,
          priority = 6,
          severity = Severity.WARNING,
          enabledByDefault = false,
          implementation = IMPLEMENTATION,
        )
        .setOptions(listOf(CHECK_DEPRECATED))

    @JvmField
    val KOTLIN_PROPERTY =
      Issue.create(
        id = "KotlinPropertyAccess",
        briefDescription = "Kotlin Property Access",
        explanation =
          """
            For a method to be represented as a property in Kotlin, strict “bean”-style prefixing must be used.

            Accessor methods require a `get` prefix or for boolean-returning methods an `is` prefix can be used.
            """,
        moreInfo = "https://android.github.io/kotlin-guides/interop.html#property-prefixes",
        category = Category.INTEROPERABILITY_KOTLIN,
        priority = 6,
        severity = Severity.WARNING,
        enabledByDefault = false,
        implementation = IMPLEMENTATION,
      )

    private fun isKotlinHardKeyword(keyword: String): Boolean {
      // From
      // https://github.com/JetBrains/kotlin/blob/master/core/compiler.common/src/org/jetbrains/kotlin/renderer/KeywordStringsGenerated.java
      when (keyword) {
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while" -> return true
      }

      return false
    }

    private fun isNullableAnnotation(qualifiedName: String): Boolean {
      return qualifiedName.endsWith("Nullable")
    }

    private fun isNonNullAnnotation(qualifiedName: String): Boolean {
      return qualifiedName.endsWith("NonNull") ||
        qualifiedName.endsWith("NotNull") ||
        qualifiedName.endsWith("Nonnull")
    }

    private fun isNullnessAnnotation(qualifiedName: String?): Boolean {
      return qualifiedName != null &&
        (isNullableAnnotation(qualifiedName) || isNonNullAnnotation(qualifiedName))
    }

    private fun JavaContext.hasNullnessAnnotation(
      node: PsiModifierListOwner,
      type: PsiType?,
    ): Boolean {
      // Check both the annotations from the evaluator (which will include external annotations from
      // XML files) and the annotations on the node (which will include nullness annotations for
      // Kotlin not present in source), as well as annotations on the type
      @Suppress("ExternalAnnotations")
      return evaluator.getAnnotations(node, false).any { isNullnessAnnotation(it.qualifiedName) } ||
        node.annotations.any { isNullnessAnnotation(it.qualifiedName) } ||
        type?.annotations?.any { isNullnessAnnotation(it.qualifiedName) } == true
    }

    private fun isApi(context: JavaContext, declaration: UDeclaration): Boolean {
      val evaluator = context.evaluator
      if (evaluator.isPublic(declaration) || evaluator.isProtected(declaration)) {
        val cls = declaration.getParentOfType<UClass>(UClass::class.java) ?: return true
        return evaluator.isPublic(cls) && cls !is UAnonymousClass
      }

      return false
    }
  }

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    // using deprecated psi field here instead of sourcePsi because the IDE
    // still uses older version of UAST
    val uastFile = context.uastFile
    if (uastFile != null && isKotlin(uastFile.lang)) {
      val checkNullness = context.isEnabled(PLATFORM_NULLNESS)
      if (checkNullness) {
        return KotlinVisitor(context)
      }

      // The remaining checks apply only to Java code
      return null
    }
    return JavaVisitor(context)
  }

  override fun getApplicableUastTypes(): List<Class<out UElement>> {
    return listOf(UMethod::class.java, UField::class.java)
  }

  class KotlinVisitor(val context: JavaContext) : UElementHandler() {
    override fun visitMethod(node: UMethod) {
      if (isApi(context, node) && node.returnTypeReference == null) {
        // Not explicitly setting return type. See if it's a nullable type:
        val uastType = node.returnType ?: return
        if (uastType is PsiPrimitiveType) {
          return
        }

        // Already annotated?
        if (context.hasNullnessAnnotation(node.javaPsi, uastType)) {
          return
        }

        // Don't flag suspend function; the return value is implicit
        if (context.evaluator.isSuspend(node.javaPsi)) {
          return
        }

        // Based on SpecifyTypeExplicitlyIntention, the IntelliJ inspection for
        // adding explicit types. However, that code runs in the IDE which has
        // access to more resolve machinery; here, instead of looking up
        // CallableDescriptor for the return type we look at the right hand side
        // where the type is.

        val declaration = node.sourcePsi as? KtCallableDeclaration ?: return
        when (declaration) {
          is KtFunction -> if (declaration.isLocal || declaration.hasDeclaredReturnType()) return
          is KtProperty -> if (declaration.isLocal || declaration.typeReference != null) return
          else -> return
        }
        if (declaration.containingClassOrObject?.isLocal == true) return
        val expression =
          when (declaration) {
            is KtProperty -> declaration.initializer ?: declaration.delegateExpression ?: return
            is KtFunction -> declaration.bodyExpression ?: declaration.bodyBlockExpression ?: return
            else -> return
          }
        analyze(expression) {
          val ktType = expression.expressionType ?: return
          if (ktType is KaDynamicType) return
          // We're considering flexible types as platform types since Kotlin doesn't support union
          // types yet. In the future this may need to be refined.
          if (!ktType.isFlexibleRecursive()) return
          // NB: The return type of the declaration isn't flexible type.
          // Rather, type arguments could be flexible, e.g., Lazy<(String..String?)>
          val typeString =
            if (ktType is KaFlexibleType) null
            else {
              @OptIn(KaExperimentalApi::class)
              val renderer =
                KaTypeRendererForSource.WITH_SHORT_NAMES.with {
                  // By default, nullability flexible type is rendered with ! at the end,
                  // e.g., Lazy<String!>
                  flexibleTypeRenderer = KaFlexibleTypeRenderer.AS_RANGE
                }
              @OptIn(KaExperimentalApi::class)
              ktType.render(renderer, position = Variance.INVARIANT)
            }
          reportMissingExplicitType(node, typeString)
        }
      }
    }

    private fun KaType.isFlexibleRecursive(): Boolean {
      if (this is KaFlexibleType) return true
      val arguments = (this as? KaClassType)?.typeArguments ?: return false
      return arguments.any { it !is KaStarTypeProjection && it.type?.isFlexibleRecursive() == true }
    }

    override fun visitField(node: UField) {}

    private fun reportMissingExplicitType(node: UElement, typeString: String? = null) {
      context.report(
        PLATFORM_NULLNESS,
        node,
        context.getNameLocation(node),
        "Should explicitly declare type here since implicit type does not specify nullness" +
          if (typeString != null) " ($typeString)" else "",
      )
    }
  }

  class JavaVisitor(val context: JavaContext) : UElementHandler() {
    private val checkForKeywords = context.isEnabled(NO_HARD_KOTLIN_KEYWORDS)
    private val checkNullness = context.isEnabled(PLATFORM_NULLNESS)
    private val checkLambdaLast = context.isEnabled(LAMBDA_LAST)
    private val checkPropertyAccess = context.isEnabled(KOTLIN_PROPERTY)

    override fun visitMethod(node: UMethod) {
      if (isApi(context, node)) {
        val methodName = node.name

        if (checkForKeywords) {
          ensureNonKeyword(methodName, node, "method")
        }

        if (checkPropertyAccess && isLikelySetter(methodName, node)) {
          ensureValidProperty(node, methodName)
        }

        if (checkLambdaLast) {
          ensureLambdaLastParameter(node)
        }

        if (checkNullness) {
          val type = node.returnType
          if (type != null) { // not a constructor
            ensureNullnessKnown(node, type)
          }
          for (parameter in node.uastParameters) {
            ensureNullnessKnown(parameter, parameter.typeFromPsi)
          }
        }
      }
    }

    override fun visitField(node: UField) {
      if (isApi(context, node)) {
        if (checkForKeywords) {
          node.nameFromSource?.let { name -> ensureNonKeyword(name, node, "field") }
        }
        if (checkNullness) {
          ensureNullnessKnown(node, node.typeFromPsi)
        }
      }
    }

    private fun isLikelySetter(methodName: String, node: UMethod): Boolean {
      return methodName.startsWith("set") &&
        methodName.length > 3 &&
        Character.isUpperCase(methodName[3]) &&
        node.uastParameters.size == 1 &&
        context.evaluator.isPublic(node) &&
        !context.evaluator.isStatic(node)
    }

    private fun ensureValidProperty(setter: UMethod, methodName: String) {
      val cls = setter.getContainingUClass() ?: return
      val propertySuffix = methodName.substring(3)
      val propertyName = propertySuffix.usLocaleDecapitalize()
      val getterName1 = "get$propertySuffix"
      val getterName2 = "is$propertySuffix"
      val badGetterName = "has$propertySuffix"
      var getter: PsiMethod? = null
      var badGetter: UMethod? = null
      for (uMethod in cls.methods) {
        if (uMethod.uastParameters.isEmpty()) {
          val name = uMethod.name
          if (name == getterName1 || name == getterName2) {
            getter = uMethod.javaPsi
          } else if (
            (name == badGetterName || name == propertyName || name.endsWith(propertySuffix)) &&
              context.evaluator.isPublic(uMethod) &&
              !uMethod.isConstructor &&
              uMethod.returnType == setter.uastParameters.firstOrNull()?.typeFromPsi
          ) {
            badGetter = uMethod
          }
        }
      }

      if (getter == null) {
        // Look for inherited methods
        cls.uastSuperTypes.forEach {
          (it.type as? PsiClassType)?.resolve()?.let { superClass ->
            for (inherited in superClass.findMethodsByName(getterName1, true)) {
              if (inherited.parameterList.parametersCount == 0) {
                getter = inherited
                break
              }
            }
            if (getter == null) {
              for (inherited in superClass.findMethodsByName(getterName2, true)) {
                if (inherited.parameterList.parametersCount == 0) {
                  getter = inherited
                  break
                }
              }
            }
          }
        }
      }

      if (getter != null && getter !is PsiCompiledElement) {
        @Suppress("NAME_SHADOWING") // compiler gets confused about getter nullness
        val getter: PsiMethod = getter!!

        // enforce public and not static
        if (!context.evaluator.isPublic(getter)) {
          val message =
            "This getter should be public such that `$propertyName` can " +
              "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
          val location = context.getNameLocation(getter)
          context.report(KOTLIN_PROPERTY, getter, location, message)
          return
        }

        if (context.evaluator.isStatic(getter)) {
          var staticElement: PsiElement? = null
          val modifierList = getter.modifierList
          // Try to find the static modifier itself
          if (modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
            var child: PsiElement? = modifierList.firstChild
            while (child != null) {
              if (child is PsiKeyword && PsiKeyword.STATIC == child.text) {
                staticElement = child
                break
              }
              child = child.nextSibling
            }
          }
          val location =
            if (staticElement != null) {
              context.getLocation(staticElement)
            } else {
              context.getNameLocation(getter)
            }
          val message =
            "This getter should not be static such that `$propertyName` can " +
              "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
          context.report(
            KOTLIN_PROPERTY,
            location.source as? PsiElement ?: setter,
            location,
            message,
          )
          return
        }

        val setterParameterType = setter.uastParameters.first().typeFromPsi
        if (
          setterParameterType != getter.returnType &&
            !hasSetter(cls, getter.returnType, setter.name) &&
            !isTypeVariableReference(setterParameterType)
        ) {
          val message =
            "The getter return type (`${getter.returnType?.presentableText}`) and setter parameter type (`${setterParameterType?.presentableText}`) getter and setter methods for property `$propertyName` should have exactly the same type to allow " +
              "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
          val location = getPropertyLocation(getter, setter.javaPsi)
          context.report(
            KOTLIN_PROPERTY,
            location.source as? PsiElement ?: setter,
            location,
            message,
          )
          return
        }

        // Make sure that if the getter is inherited, it has the same return type
        for (superMethod in getter.findSuperMethods()) {
          if (superMethod.containingClass?.isInterface != true) {
            val superReturnType = superMethod.returnType ?: return
            val getterType = getter.returnType
            if (superReturnType != getterType) {
              val message =
                "The getter return type (`${getterType?.presentableText}`)" +
                  " is not the same as the super return type " +
                  "(`${superReturnType.presentableText}`); they should have " +
                  "exactly the same type to allow " +
                  "`${propertySuffix.usLocaleDecapitalize()}` " +
                  "be accessed as a property from Kotlin; see " +
                  "https://android.github.io/kotlin-guides/interop.html#property-prefixes"
              val location = getPropertyLocation(getter, superMethod)
              location.secondary?.message = "Super method here"
              context.report(
                KOTLIN_PROPERTY,
                location.source as? PsiElement ?: setter,
                location,
                message,
              )
              return
            }
          }
        }
      } else if (
        badGetter != null &&
          // Don't complain about overrides; we can't rename those
          !badGetter.javaPsi.findSuperMethods().any() &&
          // Don't complain if the matched bad getter method already has its own
          // match
          run {
            val matchingName =
              "set${badGetter.name.removePrefix("is").removePrefix("get")
                            .removePrefix("has")}"

            methodName == matchingName || cls.methods.none { it.name == matchingName }
          }
      ) {
        val name1 = badGetter.name
        if (name1.startsWith("is") && methodName.startsWith("setIs") && name1[2].isUpperCase()) {
          val newProperty = name1[2].toLowerCase() + name1.substring(3)
          val message =
            "This method should be called `set${newProperty.usLocaleCapitalize()}` such " +
              "that (along with the `$name1` getter) Kotlin code can access it " +
              "as a property (`$newProperty`); see " +
              "https://android.github.io/kotlin-guides/interop.html#property-prefixes"
          val location = context.getNameLocation(setter)
          context.report(KOTLIN_PROPERTY, setter, location, message)
          return
        }

        val location = context.getNameLocation(badGetter)
        val message =
          "This method should be called `get$propertySuffix` such that `$propertyName` can " +
            "be accessed as a property from Kotlin; see https://android.github.io/kotlin-guides/interop.html#property-prefixes"
        context.report(KOTLIN_PROPERTY, badGetter, location, message)
      }
    }

    private fun isTypeVariableReference(type: PsiType?): Boolean {
      if (type is PsiClassType) {
        val cls = type.resolve() ?: return false
        return cls is PsiTypeParameter
      } else {
        return false
      }
    }

    /** Returns true if the given class has a (possibly inherited) setter of the given type. */
    private fun hasSetter(cls: UClass, type: PsiType?, setterName: String): Boolean {
      for (method in cls.javaPsi.findMethodsByName(setterName, true)) {
        val parameterList = method.parameterList
        val parameters = parameterList.parameters
        if (parameters.size == 1 && parameters[0].type == type) {
          return true
        }
      }

      return false
    }

    private fun getPropertyLocation(location1: PsiMethod, location2: PsiMethod): Location {
      val primary: PsiMethod
      val secondary: PsiMethod

      if (location1 is PsiCompiledElement) {
        primary = location2
        secondary = location1
      } else {
        primary = location1
        secondary = location2
      }

      return context
        .getNameLocation(primary)
        .withSecondary(
          context.getNameLocation(secondary),
          "${if (secondary.name.startsWith("set")) "Setter" else "Getter"} here",
        )
    }

    private fun ensureNullnessKnown(node: UDeclaration, type: PsiType?) {
      if (type is PsiPrimitiveType) {
        return
      }
      if (type is PsiClassType && type.resolve() is PsiTypeParameter) {
        // The nullability of generic type parameters is often only known by the caller.
        return
      }
      if (
        node is UField &&
          (node.javaPsi as? PsiField)?.modifierList?.hasModifierProperty(PsiModifier.FINAL) == true
      ) {
        return
      }

      // Check annotations present on the item or on the type. The annotations from the item are
      // UAnnotations and the annotations from the type are PsiAnnotations, so extract the name
      // and location from each kind of annotation to make one list to loop over.
      val allAnnotations =
        context.evaluator.getAllAnnotations(node as UAnnotated, false).map {
          Pair(it.qualifiedName) { context.getLocation(it) }
        } +
          (type?.annotations?.map { Pair(it.qualifiedName) { context.getLocation(it) } }
            ?: emptyList())
      for ((name, location) in allAnnotations) {
        name ?: continue

        if (isNullableAnnotation(name)) {
          if (isToStringMethod(node)) {
            val message = "Unexpected `@Nullable`: `toString` should never return null"
            context.report(PLATFORM_NULLNESS, node as UElement, location(), message)
          }
          return
        }

        if (isNonNullAnnotation(name)) {
          if (isEqualsParameter(node)) {
            val message =
              "Unexpected @NonNull: The `equals` contract allows the parameter to be null"
            context.report(PLATFORM_NULLNESS, node as UElement, location(), message)
          }
          return
        }
      }

      // Known nullability: don't complain
      if (isEqualsParameter(node) || isToStringMethod(node)) {
        return
      }

      // Annotation members cannot be null
      if (node is UMethod) {
        node.getContainingUClass()?.let {
          if (it.isAnnotationType) {
            return
          }
        }
      }

      // Skip deprecated members?
      if (IGNORE_DEPRECATED || CHECK_DEPRECATED.getValue(context.configuration)) {
        val deprecatedNode =
          if (node is UParameter) {
            node.uastParent
          } else {
            node
          }
        if ((deprecatedNode?.sourcePsi as? PsiDocCommentOwner)?.isDeprecated == true) {
          return
        }
        var curr = deprecatedNode
        while (curr != null) {
          curr = curr.getContainingUClass() ?: break
          if ((curr.sourcePsi as? PsiDocCommentOwner)?.isDeprecated == true) {
            return
          }
        }
      }

      if (overridesUnannotatedPlatformMethod(node)) {
        return
      }

      val location: Location =
        when (node) {
          is UVariable -> // UParameter, UField
          context.getLocation(node.typeReference ?: return)
          is UMethod -> context.getLocation(node.returnTypeElement ?: return)
          else -> return
        }
      val replaceLocation =
        if (node is UParameter) {
          location
        } else if (node is UMethod) {
          // Place the insertion point at the modifiers such that we don't
          // insert the annotation for example after the "public" keyword.
          // We also don't want to place it on the method range itself since
          // that would place it before the method comments.
          context.getLocation(node.javaPsi.modifierList)
        } else if (node is UField) {
          // Ditto for fields
          (node.javaPsi as? PsiField)?.modifierList?.let { modifierList ->
            context.getLocation(modifierList)
          } ?: return
        } else {
          return
        }
      val message =
        "Unknown nullability; explicitly declare as `@Nullable` or `@NonNull`" +
          " to improve Kotlin interoperability; see " +
          "https://developer.android.com/kotlin/interop#nullability_annotations"
      val fix =
        LintFix.create()
          .alternatives(
            LintFix.create()
              .replace()
              .name("Annotate @NonNull")
              .range(replaceLocation)
              .beginning()
              .shortenNames()
              .reformat(true)
              .with("${getNonNullAnnotation(context)} ")
              .build(),
            LintFix.create()
              .replace()
              .name("Annotate @Nullable")
              .range(replaceLocation)
              .beginning()
              .shortenNames()
              .reformat(true)
              .with("${getNullableAnnotation(context)} ")
              .build(),
          )
      context.report(PLATFORM_NULLNESS, node as UElement, location, message, fix)
    }

    private fun overridesUnannotatedPlatformMethod(node: UDeclaration): Boolean {
      if (node is UParameter) {
        val method = node.getParentOfType(UMethod::class.java)
        if (method != null && !method.isConstructor) {
          val superMethod = method.findRootMethod() ?: return false
          val superParameters = superMethod.parameterList.parameters
          val parameterIndex = method.uastParameters.indexOf(node)
          if (parameterIndex >= 0 && parameterIndex < superParameters.size) {
            val superParameter = superParameters[parameterIndex]
            if (
              isPlatformMethod(superMethod) &&
                !context.hasNullnessAnnotation(superParameter, superParameter.type)
            ) {
              return true
            }
          }
          return false
        }
      } else if (node is UMethod && !node.isConstructor) {
        val superMethod = node.findRootMethod() ?: return false
        if (
          isPlatformMethod(superMethod) &&
            !context.hasNullnessAnnotation(superMethod, superMethod.returnType)
        ) {
          return true
        }
      }
      return false
    }

    private fun UMethod.findRootMethod(): PsiMethod? {
      var superMethod = javaPsi.findSuperMethods().firstOrNull() ?: return null
      while (true) {
        superMethod = superMethod.findSuperMethods().firstOrNull() ?: return superMethod
      }
    }

    private fun isPlatformMethod(method: PsiMethod): Boolean {
      val containingClass = method.containingClass ?: return false
      val name = containingClass.qualifiedName ?: return false
      return name.startsWith("android.") || name.startsWith("java.")
    }

    private fun isEqualsParameter(node: UDeclaration): Boolean {
      if (node is UParameter) {
        val method = node.getContainingUMethod() ?: return false
        if (method.name == "equals" && method.uastParameters.size == 1) {
          return true
        }
      }

      return false
    }

    private fun isToStringMethod(node: UDeclaration): Boolean {
      if (node is UMethod) {
        val method = node
        if (method.name == "toString" && method.uastParameters.isEmpty()) {
          return true
        }
      }

      return false
    }

    private fun getNonNullAnnotation(context: JavaContext): String {
      initializeAnnotationNames(context)
      return nonNullAnnotation!!
    }

    private fun getNullableAnnotation(context: JavaContext): String {
      initializeAnnotationNames(context)
      return nullableAnnotation!!
    }

    private var nonNullAnnotation: String? = null
    private var nullableAnnotation: String? = null

    private fun initializeAnnotationNames(context: JavaContext) {
      if (nonNullAnnotation == null) {
        if (context.evaluator.findClass("android.support.annotation.NonNull") != null) {
          nonNullAnnotation = "@android.support.annotation.NonNull"
          nullableAnnotation = "@android.support.annotation.Nullable"
        } else {
          nonNullAnnotation = "@androidx.annotation.NonNull"
          nullableAnnotation = "@androidx.annotation.Nullable"
        }
      }
    }

    /** Returns true if this declaration has an inherited signature */
    private fun isInherited(node: UDeclaration): Boolean {
      return node is UMethod && node.javaPsi.findSuperMethods().isNotEmpty()
    }

    private fun ensureNonKeyword(name: String, node: UDeclaration, typeLabel: String) {
      if (isKotlinHardKeyword(name) && !isInherited(node)) {
        // See if this method is overriding some other method; in that case
        // we don't have a choice here.
        if (node is UMethod && context.evaluator.isOverride(node)) {
          return
        }
        val message =
          "Avoid $typeLabel names that are Kotlin hard keywords (\"$name\"); see " +
            "https://android.github.io/kotlin-guides/interop.html#no-hard-keywords"
        context.report(
          NO_HARD_KOTLIN_KEYWORDS,
          node as UElement,
          context.getNameLocation(node as UElement),
          message,
        )
      }
    }

    private fun ensureLambdaLastParameter(method: UMethod) {
      val parameters = method.uastParameters
      if (parameters.size > 1) {
        // Make sure that SAM-compatible parameters are last
        val lastIndex = parameters.size - 1
        if (!isFunctionalInterface(parameters[lastIndex].typeFromPsi)) {
          for (i in lastIndex - 1 downTo 0) {
            val parameter = parameters[i]
            if (isFunctionalInterface(parameter.typeFromPsi)) {
              // Don't flag Executor; see b/135275901
              if (parameter.typeFromPsi?.canonicalText == "java.util.concurrent.Executor") {
                continue
              }
              if (isInherited(method)) {
                return
              }

              val message =
                "Functional interface parameters (such as parameter ${i + 1}, \"${parameter.nameFromSource}\", in ${
                                method.javaPsi.containingClass?.qualifiedName}.${method.name
                                }) should be last to improve Kotlin interoperability; see " +
                  "https://kotlinlang.org/docs/reference/java-interop.html#sam-conversions"
              val last = parameters[lastIndex] as UElement
              context.report(LAMBDA_LAST, last, context.getLocation(last), message)
              break
            }
          }
        }
      }
    }

    private fun isFunctionalInterface(type: PsiType?): Boolean {
      if (type !is PsiClassType) {
        return false
      }

      val cls = type.resolve() ?: return false
      if (!cls.isInterface) {
        return false
      }

      var abstractCount = 0
      for (method in cls.methods) {
        if (method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT)) {
          abstractCount++
        }
      }

      if (abstractCount != 1) {
        // Try a little harder; we don't want to count methods that are overrides
        if (abstractCount > 1) {
          abstractCount = 0
          for (method in cls.methods) {
            if (
              method.modifierList.hasModifierProperty(PsiModifier.ABSTRACT) &&
                !context.evaluator.isOverride(method, true)
            ) {
              abstractCount++
            }
          }
        }

        if (abstractCount != 1) {
          return false
        }
      }

      if (cls.superClass?.isInterface == true) {
        return false
      }

      return true
    }
  }
}
