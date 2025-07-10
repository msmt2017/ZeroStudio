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

import com.android.tools.lint.detector.api.Category.Companion.CUSTOM_LINT_CHECKS
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Detector.UastScanner
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LintFix.GroupBuilder
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Platform.Companion.JDK_SET
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.Severity.WARNING
import com.android.tools.lint.detector.api.TextFormat
import com.android.tools.lint.detector.api.TextFormat.Companion.HTTPS_PREFIX
import com.android.tools.lint.detector.api.TextFormat.Companion.HTTP_PREFIX
import com.android.tools.lint.detector.api.findSelector
import com.android.tools.lint.detector.api.isDuplicatedOverload
import com.android.tools.lint.detector.api.isJava
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isPolyadicFromStringTemplate
import com.android.tools.lint.detector.api.nameFromSource
import com.android.utils.usLocaleCapitalize
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.PsiVariable
import java.lang.reflect.Modifier
import java.net.MalformedURLException
import java.net.URL
import java.util.EnumSet
import java.util.Locale
import kotlin.math.max
import org.jetbrains.uast.UBinaryExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPolyadicExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.UastBinaryOperator
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.isNullLiteral
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.toUElementOfType
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/**
 * A special check which analyzes lint detectors themselves, looking for common problems
 *
 * Additional ideas: Bundle this check with standalone lint! Or maybe make driver smart enough to
 * include it if there's a lint dependency in the project! Look for various instanceof PsiSomething
 * where Something is node types inside methods (Assignment expression etc) Look for binary instead
 * of polyadic checks Searching for UReturn which may not be there (expression bodies) Not using
 * named parameters in issue registrations Not using raw strings for issue explanations? And not
 * doing line continuations with \ ? Calling context.report without a scope node? Pulling out a
 * constant without using the constant evaluator? Look for error messages ending with ".", look for
 * capitalization on
 *
 *       issue registration (and maximum word length for the summary)
 *
 * Creating a visitor and only overriding visitCallExpression -- should probably just use
 * getApplicableMethods and visitMethodCall. Calling accept on a UElement with a PSI visitor
 *
 * Try running TextFormat on all messages to see if there are any problems? Warn about unit test
 * files which do not have any Kotlin test cases (if they analyze JAVA_SCOPE). Maybe look to see if
 * they're particularly needing it:
 * - manipulating strings (for kotlin check template and raw strings)
 * - creating a custom UastHandler
 * - doing anything with equals checks
 * - looking at UReturn statements or switch statements etc For Issue.create calls in Kotlin
 *   companion objects, suggest adding @JvmField to help issue registrations Look for TODO in issue
 *   registration strings, or empty registration strings
 */
class LintDetectorDetector : Detector(), UastScanner {
  override fun applicableSuperClasses(): List<String> {
    return listOf(CLASS_DETECTOR, CLASS_ISSUE_REGISTRY)
  }

  override fun getApplicableMethodNames(): List<String> =
    listOf("expect", "expectFixDiffs", "files", "projects", "lint", "visitAnnotationUsage")

  private val visitedTestClasses = mutableSetOf<String>()

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val methodName = node.methodName ?: node.methodIdentifier?.name ?: return

    when (methodName) {
      "expect",
      "expectFixDiffs" -> {
        if (method.returnType?.canonicalText != CLASS_TEST_LINT_RESULT) {
          return
        }
        node.valueArguments.firstOrNull()?.let {
          LintDetectorVisitor(context).checkTrimIndent(it, false)
        }
      }
      "files",
      "projects" -> {
        if (method.returnType?.canonicalText != CLASS_TEST_LINT_TASK) {
          return
        }
        val visitor = LintDetectorVisitor(context)
        for (testFile in node.valueArguments) {
          checkTestFile(testFile, visitor)
        }
      }
      "lint" -> {
        if (method.returnType?.canonicalText != CLASS_TEST_LINT_TASK) {
          return
        }
        val testClass = node.getParentOfType(UClass::class.java)
        val qualifiedName = testClass?.qualifiedName
        if (testClass != null && qualifiedName != null && visitedTestClasses.add(qualifiedName)) {
          checkDocumentationExamples(context, testClass)
        }
      }
      "visitAnnotationUsage" -> {
        if (method.parameterList.parametersCount == 4 && node.receiver is USuperExpression) {
          context.report(
            USE_UAST,
            node,
            context.getLocation(node),
            "Do not invoke `super.visitAnnotationUsage`",
          )
        }
      }
    }
  }

  private fun checkTestFile(testFileExpression: UExpression, visitor: LintDetectorVisitor) {
    val testFile = testFileExpression.findSelector()
    if (testFile is UCallExpression) {
      visitor.checkTestFile(testFile)
    } else if (testFile is UReferenceExpression) {
      val resolved = testFile.resolve()
      if (resolved != null) {
        if (resolved is UVariable) {
          val initializer = resolved.uastInitializer
          if (initializer is UCallExpression) {
            visitor.checkTestFile(initializer)
          }
        } else if (resolved is PsiVariable) {
          //noinspection LintImplUseUast
          val initializer = resolved.initializer.toUElement()
          if (initializer is UCallExpression) {
            visitor.checkTestFile(initializer)
          }
        } else if (resolved is PsiMethod) {
          val method = resolved.name
          if (method == "indented" && testFile is UQualifiedReferenceExpression) {
            checkTestFile(testFile.receiver, visitor)
          }
        }
      }
    } else if (testFile is UParenthesizedExpression) {
      checkTestFile(testFile.expression, visitor)
    }
  }

  private fun checkDocumentationExamples(context: JavaContext, testClass: UClass) {
    // Only enforce for newly added checks
    if (getCopyrightYear(context) < 2021) {
      return
    }

    val tests =
      testClass.uastDeclarations.filterIsInstance<UMethod>().filter { it.name.startsWith("test") }
    if (tests.isEmpty()) {
      return
    }

    for (name in tests) {
      if (name.name.startsWith("testDocumentationExample")) {
        return
      }
    }

    // TODO: Try to line up the issues analyzed by the corresponding detector
    // and make sure we have a documentation example for each

    val element = tests.first()
    context.report(
      MISSING_DOC_EXAMPLE,
      element,
      context.getLocation(element),
      "Expected to also find a documentation example test (`testDocumentationExample`) which shows a " +
        "simple, typical scenario which triggers the test, and which will be extracted into lint's " +
        "per-issue documentation pages",
    )
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    checkKotlin(context, declaration)
    declaration.accept(LintDetectorVisitor(context))

    if (context.evaluator.inheritsFrom(declaration.javaPsi, CLASS_ISSUE_REGISTRY)) {
      checkIssueRegistry(context, declaration)
    }
  }

  private fun checkIssueRegistry(context: JavaContext, declaration: UClass) {
    val methods = declaration.javaPsi.allMethods
    val count = methods.count { it.name == "getVendor" && it.parameters.isEmpty() }
    if (count <= 1) { // one occurrence is on IssueRegistry itself; don't count that one
      val name = declaration.qualifiedName
      if (name != null && name.startsWith("com.android.tools.lint.client.api.")) {
        // Skip lint infrastructure classes
        return
      }
      context.report(
        MISSING_VENDOR,
        declaration,
        context.getNameLocation(declaration),
        "An `IssueRegistry` should override the `vendor` property",
      )
    }
  }

  private fun getCopyrightYear(context: JavaContext): Int {
    val source = context.getContents().toString()
    val yearIndex = source.indexOf(" 20")
    if (yearIndex != -1) {
      val yearString = source.substring(yearIndex + 1, yearIndex + 5)
      if (!yearString[2].isDigit() || !yearString[3].isDigit()) {
        return -1
      }
      return yearString.toInt()
    }

    return -1
  }

  private fun checkKotlin(context: JavaContext, declaration: UClass) {
    if (!isKotlin(declaration.lang)) {
      if (getCopyrightYear(context) >= 2020) {
        context.report(
          USE_KOTLIN,
          declaration,
          context.getNameLocation(declaration),
          "New lint checks should be implemented in Kotlin to take advantage of a lot of Kotlin-specific mechanisms in the Lint API",
        )
      }
    }
  }

  class LintDetectorVisitor(private val context: JavaContext) : AbstractUastVisitor() {
    private val typoLookup = TypoLookup[context.client, "en", null]

    override fun visitCallExpression(node: UCallExpression): Boolean {
      when (node.methodName) {
        "getBody" -> {
          checkCall(
            node,
            CLASS_PSI_METHOD,
            "Don't call PsiMethod#getBody(); you must use UAST instead. " +
              "If you don't have a UMethod call UastFacade.getMethodBody(method)",
          )
        }
        "getParent" -> {
          checkCall(
            node,
            CLASS_PSI_ELEMENT,
            "Don't call `PsiElement#getParent()`; you should use UAST instead and call `getUastParent()`",
            requireUastReceiver = true,
          )
        }
        "getContainingClass" -> {
          checkCall(
            node,
            CLASS_PSI_MEMBER,
            "Don't call `PsiMember#getContainingClass()`; you should use UAST instead and call `getContainingUClass()`",
            requireUastReceiver = true,
          )
        }
        "getParentOfType" -> {
          // Only a problem if arg0 is a UElement
          val receiverType = node.valueArguments.firstOrNull()?.getExpressionType()
          val evaluator = context.evaluator
          val typeClass = evaluator.getTypeClass(receiverType)
          if (typeClass != null && evaluator.inheritsFrom(typeClass, CLASS_U_ELEMENT, false)) {
            checkCall(
              node,
              CLASS_PSI_TREE_UTIL,
              "Don't call `PsiTreeUtil#getParentOfType()`; you should use UAST instead and call `UElement.parentOfType`",
            )
          }
        }
        "getInitializer" -> {
          checkCall(
            node,
            CLASS_PSI_VARIABLE,
            "Don't call PsiField#getInitializer(); you must use UAST instead. " +
              "If you don't have a UField call UastFacade.getInitializerBody(field)",
          )
        }
        "equals" -> {
          checkEquals(node, node.receiverType, node.receiver, node.valueArguments.firstOrNull())
        }
        "create" -> { // Issue.create
          checkIssueRegistration(node)
        }
        "addMoreInfo" -> { // on Issue.create
          if (node.valueArgumentCount == 1) {
            val argument = node.valueArguments[0]
            val string = getString(argument)
            if (string.isNotEmpty()) {
              checkMoreInfoUrl(argument, string)
            }
          }
        }
        "report" -> {
          checkReport(node)
        }
        "of" -> {
          checkEnumSet(node)
        }
        "print",
        "println" -> {
          val containingClass = node.resolve()?.containingClass?.qualifiedName
          if (
            !context.isTestSource &&
              (containingClass == "java.io.PrintStream" || containingClass == "kotlin.io.ConsoleKt")
          ) {
            context.report(
              TEXT_FORMAT,
              node,
              context.getLocation(node),
              "Lint checks should not be printing to console; use `LintClient.log` instead",
            )
          }
        }
      }

      return super.visitCallExpression(node)
    }

    fun checkTestFile(testFile: UCallExpression) {
      val name = testFile.methodName
      if (
        name == "java" ||
          name == "kotlin" ||
          name == "kt" ||
          name == "kts" ||
          name == "manifest" ||
          name == "gradle" ||
          name == "xml"
      ) {
        val args = testFile.valueArguments
        val source =
          if (args.size > 1) args[1]
          else if (args.size == 1) args[0]
          else {
            // Something like the manifest() DSL where you don't specify source;
            // ignore these
            return
          }
        val string = getString(source)
        checkTrimIndent(source, isUnitTestFile = true)
        if (string.contains("$") && isKotlin(testFile.lang)) {
          checkDollarSubstitutions(source)
        }
      }
    }

    private fun checkDollarSubstitutions(source: UExpression) {
      source.sourcePsi?.accept(
        object : PsiRecursiveElementVisitor() {
          override fun visitElement(element: PsiElement) {
            val text = element.text
            var string = true
            var index = text.indexOf(DOLLAR_STRING)
            if (index == -1) {
              string = false
              index = text.indexOf(DOLLAR_CHAR)
            }
            if (index != -1) {
              val fix =
                LintFix.create()
                  .replace()
                  .text(if (string) DOLLAR_STRING else DOLLAR_CHAR)
                  .with("＄")
                  .build()
              val location = context.getRangeLocation(element, index, 6)
              context.report(
                DOLLAR_STRINGS,
                source,
                location,
                "In unit tests, use the fullwidth dollar sign, `＄`, instead of `\$`, to avoid having to use cumbersome escapes. Lint will treat a `＄` as a `\$`.",
                fix,
              )
              return
            }
            super.visitElement(element)
          }
        }
      )
    }

    private fun checkEnumSet(node: UCallExpression) {
      val targetClass = node.resolve()?.containingClass?.qualifiedName ?: return
      if (targetClass == "java.util.EnumSet") {
        val scopes = EnumSet.noneOf(Scope::class.java)
        for (argument in node.valueArguments) {
          var name = (argument.tryResolve() as? PsiField)?.name
          if (name == null) {
            name =
              if (
                argument is UQualifiedReferenceExpression &&
                  argument.selector is USimpleNameReferenceExpression
              ) {
                (argument.selector as USimpleNameReferenceExpression).identifier
              } else if (argument is USimpleNameReferenceExpression) {
                argument.identifier
              } else {
                // Can't figure out scope set properly
                return
              }
          }
          try {
            val scope = Scope.valueOf(name)
            scopes.add(scope)
          } catch (e: Throwable) {
            // Can't figure out scope set properly
            return
          }
        }
        if (!scopes.isEmpty()) {
          // Compare to the well known scope sets
          for (field in Scope::class.java.declaredFields) {
            if (field.modifiers and Modifier.STATIC != 0 && !field.isEnumConstant) {
              field.isAccessible = true
              val constant = field.get(0)
              if (scopes == constant) {
                val fix =
                  LintFix.create()
                    .name("Replace with Scope.${field.name}")
                    .replace()
                    .text(node.sourcePsi?.text ?: node.asSourceString())
                    .with("com.android.tools.lint.detector.api.Scope.${field.name}")
                    .shortenNames()
                    .autoFix()
                    .build()
                context.report(
                  EXISTING_LINT_CONSTANTS,
                  node,
                  context.getLocation(node),
                  "Use `Scope.${field.name}` instead",
                  fix,
                )
              }
            }
          }
        }
      }
    }

    private fun checkReport(call: UCallExpression) {
      val create = call.resolve() ?: return
      val evaluator = context.evaluator
      if (
        !evaluator.isMemberInSubClassOf(create, CLASS_CONTEXT, false) &&
          !evaluator.isMemberInSubClassOf(create, CLASS_LINT_CLIENT, false)
      ) {
        return
      }

      val arguments = call.valueArguments
      for (index in 2 until arguments.size) { // 2: always after issue and location
        val argument = arguments[index]
        val type = argument.getExpressionType() ?: continue
        if (type.canonicalText == JAVA_LANG_STRING) {
          val string = getString(argument)
          checkLintString(argument, string)

          if (
            string.endsWith(".") &&
              string.lastIndexOf('.', string.length - 2) == -1 &&
              string.lastIndexOf('?', string.length - 2) == -1 &&
              !string.endsWith(" etc.")
          ) {
            // Make sure string is really there; may not be the case if we
            // did constant propagation and the string itself is elsewhere
            val fallback = context.getLocation(argument)
            val location = getStringLocation(argument, string, fallback)
            val canFix = location !== fallback || locationContains(location, string)
            val fix =
              if (canFix)
                LintFix.create()
                  .name("Remove period")
                  .replace()
                  .text(".")
                  .with("")
                  .autoFix()
                  .build()
              else null
            context.report(
              TEXT_FORMAT,
              argument,
              location,
              "Single sentence error messages should not end with a period",
              fix,
            )
          }
        }
      }
    }

    private fun locationContains(location: Location, string: String): Boolean {
      val start = location.start?.offset ?: -1
      val end = location.end?.offset ?: -1
      val i = context.getContents()?.indexOf(string, startIndex = start) ?: -1
      return i < end && i != -1
    }

    override fun visitField(node: UField): Boolean {
      if (node.nameFromSource == "issues") {
        val initializer = node.uastInitializer
        if (initializer != null) {
          checkGetIssues(initializer)
        }
      }
      return super.visitField(node)
    }

    override fun visitMethod(node: UMethod): Boolean {
      if (node.isDuplicatedOverload()) {
        return true
      }
      if (node.name == "getIssues") {
        checkGetIssues(node)
      }
      return super.visitMethod(node)
    }

    private fun checkGetIssues(node: UElement) {
      node.accept(
        object : AbstractUastVisitor() {
          override fun visitSimpleNameReferenceExpression(
            node: USimpleNameReferenceExpression
          ): Boolean {
            val evaluator = context.evaluator
            val type = context.evaluator.getTypeClass(node.getExpressionType())
            if (type != null && evaluator.inheritsFrom(type, CLASS_ISSUE)) {
              val resolved = node.resolve()
              if (resolved is PsiField) {
                // If marked @JvmField or in Java
                val issue = resolved.toUElementOfType<UField>()
                @Suppress("ControlFlowWithEmptyBody")
                if (
                  issue != null &&
                    isJava(issue.lang) &&
                    evaluator.inheritsFrom(issue.getContainingUClass()?.javaPsi, CLASS_DETECTOR)
                ) {
                  // Don't need to do anything; we'll see this registration
                  // as part of our regular detector visit
                } else if (
                  issue?.uAnnotations?.any { it.qualifiedName == "kotlin.jvm.JvmField" } == true
                ) {
                  // This field is annotated with @JvmField; we'll come across
                  // it within the class instead
                } else {
                  // Visit the issue since we won't find it otherwise
                  issue?.uastInitializer?.accept(this@LintDetectorVisitor)
                }
              } else if (resolved is PsiMethod) {
                val create = resolved.toUElementOfType<UMethod>()
                create?.accept(this@LintDetectorVisitor)
              }
            }
            return super.visitSimpleNameReferenceExpression(node)
          }
        }
      )
    }

    private fun checkIssueRegistration(call: UCallExpression) {
      val create = call.resolve() ?: return
      val evaluator = context.evaluator
      // Check both Issue and Issue.Companion; calls from Kotlin and Java resolve these
      // differently
      if (
        !evaluator.isMemberInClass(create, CLASS_ISSUE_COMPANION) &&
          !evaluator.isMemberInClass(create, CLASS_ISSUE)
      ) {
        return
      }

      /** Below will need updating if we ever change [Issue.create]'s parameter names */
      for ((arg, param) in evaluator.computeArgumentMapping(call, create)) {
        when (param.name) {
          "id" -> {
            val string = getString(arg)
            checkId(arg, string)
          }
          "briefDescription" -> {
            val string = getString(arg)
            if (string.isNotEmpty()) {
              // checkLintString(it, string)
              checkSummary(arg, string)
            }
          }
          "explanation" -> {
            val string = getString(arg)
            if (string.isNotEmpty()) {
              checkLintString(arg, string)
              checkTrimIndent(arg)
              checkContinuations(arg, string)
            }
          }
          "moreInfo" -> {
            val string = getString(arg)
            if (string.isNotEmpty()) {
              checkMoreInfoUrl(arg, string)
            }
          }
        }
      }
    }

    private fun checkSummary(argument: UExpression, title: String) {
      if (title.length > 60) {
        context.report(
          TEXT_FORMAT,
          argument,
          getStringLocation(argument, title),
          "The issue summary should be shorter; typically just a 3-6 words; it's used as a topic header in HTML reports and in the IDE inspections window",
        )
      } else {
        if (title[0].isLowerCase()) {
          context.report(
            TEXT_FORMAT,
            argument,
            getStringLocation(argument, title),
            "The issue summary should be capitalized",
          )
        }
        if (title.endsWith(".")) {
          context.report(
            TEXT_FORMAT,
            argument,
            getStringLocation(argument, title),
            "The issue summary should *not* end with a period (think of it as a headline)",
          )
        }
      }
    }

    private fun checkId(idArgument: UExpression, id: String) {
      // Existing ones that we don't want to keep flagging
      if (
        id == "IncompatibleMediaBrowserServiceCompatVersion" ||
          id == "PrivateMemberAccessBetweenOuterAndInnerClass" ||
          id == "PermissionImpliesUnsupportedChromeOsHardware"
      ) {
        return
      }

      if (id.startsWith("_") && context.isTestSource) {
        // Unit test detectors conventionally use _ as an id prefix
        // to easily tell them apart from regular checks
        return
      }

      // namespaced id?
      val leafIndex = id.lastIndexOf('.') + 1
      val leaf = if (leafIndex > 0 && leafIndex < id.length - 1) id.substring(leafIndex) else id

      if (leaf.isEmpty()) {
        // An empty id isn't valid but this is most likely due to difficulty
        // computing the constant value of a reference; this is not likely
        // to be helpful and we want to avoid a string index out of bounds
        // exception below
        return
      }

      if (!leaf[0].isUpperCase() || (leaf.none { it.isLowerCase() })) {
        context.report(
          ID,
          idArgument,
          context.getLocation(idArgument),
          "Lint issue IDs should use capitalized camel case, such as `MyIssueId`",
        )
      } else if (id.contains(" ")) {
        context.report(
          ID,
          idArgument,
          context.getLocation(idArgument),
          "Lint issue IDs should not contain spaces, such as `MyIssueId`",
        )
      } else if (leaf.length >= 40) {
        context.report(
          ID,
          idArgument,
          context.getLocation(idArgument),
          "Lint issue IDs should be reasonably short (< 40 chars); they're used in suppress annotations etc",
        )
      }
    }

    private fun checkMoreInfoUrl(urlArgument: UExpression, url: String) {
      checkUrl(url, urlArgument)
    }

    private fun checkUrls(argument: UExpression, string: String) {
      var start = 0
      while (true) {
        var index = string.indexOf(HTTP_PREFIX, start)
        if (index == -1) {
          index = string.indexOf(HTTPS_PREFIX, start)
          if (index == -1) {
            break
          }
        }
        start = index
        val end = TextFormat.findUrlEnd(string, start)
        val url = string.substring(start, end)

        checkUrl(url, argument)
        start = end
      }
    }

    private fun getStringLocation(
      argument: UExpression,
      string: String,
      location: Location = context.getLocation(argument),
      // a string where "|" represents the target position surrounded by additional
      // string context; for example, in the string "hello world", if we're looking
      // for the string "l" we might pick the very first "l" in "hello", but if we
      // pass in the window "wor|ld" it will find the "l" in "world" instead.
      window: String = "",
    ): Location {
      val start = location.start?.offset ?: return location
      val end = location.end?.offset ?: return location
      val contents = context.getContents() ?: return location
      var index =
        if (window.isNotEmpty()) {
          val caret = window.indexOf('|')
          assert(caret != -1)
          val match = window.substring(0, caret) + window.substring(caret + 1)
          val i = contents.indexOf(match, ignoreCase = false, startIndex = start)
          if (i == -1) {
            return location
          } else {
            i + caret
          }
        } else {
          contents.indexOf(string, ignoreCase = false, startIndex = start)
        }
      return if (index != -1) {
        if (index > end) {
          // Look for earlier occurrence too. We're seeking the string in the given
          // expression/argument position. If it's included as a literal, it will be
          // between start and end. But if we find one *after* the end, that's likely
          // another, unrelated one. Instead, find it earlier in the source; this is most
          // likely an earlier assignment which is then referenced in the expression.
          val alt = contents.lastIndexOf(string, ignoreCase = false, startIndex = start)
          if (alt != -1) {
            index = alt
          }
        }
        if (
          isPolyadicFromStringTemplate(argument) &&
            argument.operands.size == 1 &&
            location.source === argument.operands[0]
        ) {
          context.getRangeLocation(argument.operands[0], index - start, string.length)
        } else {
          context.getRangeLocation(argument, index - start, string.length)
        }
      } else {
        // Couldn't find string; this typically happens if the string value is split across
        // multiple string literals (line concatenations)  or has escapes etc. Just
        // use the reference location.
        location
      }
    }

    private val checkedUrls = mutableSetOf<String>()

    @Suppress("LintImplBadUrl") // This code contains the strings we're looking for
    private fun checkUrl(url: String, argument: UExpression) {
      if (
        url == "http://schemas.android.com/apk/res-auto" ||
          url == "http://schemas.android.com/apk/res/android" ||
          url == "http://schemas.android.com/tools"
      ) {
        // Not real URLs
        return
      }

      if (!checkedUrls.add(url)) {
        // only check URLs once; this is not just for performance but more importantly
        // because with constant evaluators we may end up generating the same
        // error multiple times at the same location with the same message, which
        // lint treats as an error
        return
      }
      if (url.contains("b.android.com") || url.contains("code.google.com/p/android/issues/")) {
        context.report(
          CHECK_URL,
          argument,
          getStringLocation(argument, url),
          //noinspection LintImplUnexpectedDomain
          "Don't point to old `http://b.android.com` links; should be using `https://issuetracker.google.com` instead",
        )
      } else if (url.startsWith("https://issuetracker.google.com/")) {
        val issueLength = url.length - (url.lastIndexOf('/') + 1)
        val expectedLength = 9
        if (issueLength < expectedLength) {
          context.report(
            CHECK_URL,
            argument,
            getStringLocation(argument, url),
            "Suspicious issue tracker length; expected a $expectedLength digit issue id, but was $issueLength",
          )
        }
      } else {
        try {
          val parsed = URL(url)
          val protocol = parsed.protocol?.lowercase(Locale.US)
          if (protocol == "mailto") {
            return
          } else if (protocol != null && protocol != "http" && protocol != "https") {
            context.report(
              CHECK_URL,
              argument,
              getStringLocation(argument, url),
              "Unexpected protocol `$protocol` in `$url`",
            )
          } else {
            val host = parsed.host
            if (
              host != null && (host.contains("corp.google.com") || host.contains("googleplex.com"))
            ) {
              context.report(
                UNEXPECTED_DOMAIN,
                argument,
                getStringLocation(argument, url),
                "Don't use internal Google links (`$url`)",
              )
            } else if (
              host != null &&
                !host.endsWith(".google.com") &&
                !host.endsWith(".android.com") &&
                host != "goo.gle" &&
                host != "android.com" &&
                host != "android-developers.googleblog.com" &&
                host != "android-developers.blogspot.com" &&
                host != "g.co" &&
                host != "material.io" &&
                host != "android.github.io" &&
                // Allow medium.com/androiddevelopers/*
                (host != "medium.com" || !parsed.path.startsWith("/androiddevelopers/")) &&
                // Also allow some other common resources
                !host.endsWith(".wikipedia.org") &&
                !host.endsWith(".groovy-lang.org") &&
                !host.endsWith(".sqlite.org") &&
                host != "stackoverflow.com" &&
                host != "tools.ietf.org" &&
                host != "kotlinlang.org" &&
                host != "bugs.eclipse.org"
            ) {
              context.report(
                UNEXPECTED_DOMAIN,
                argument,
                getStringLocation(argument, url),
                "Unexpected URL host `$host`; for the builtin Android Lint checks make sure to use an authoritative link (`$url`)",
              )
            } else if (protocol == "http") {
              // Use https for our known domains, not http
              context.report(
                UNEXPECTED_DOMAIN,
                argument,
                getStringLocation(argument, url),
                "Use https, not http, for more info links (`$url`)",
              )
            }
          }
        } catch (e: MalformedURLException) {
          context.report(
            CHECK_URL,
            argument,
            getStringLocation(argument, url),
            "The URL `$url` cannot be parsed: $e",
          )
        }
      }
    }

    fun checkTrimIndent(argument: UExpression, isUnitTestFile: Boolean = false) {
      if (argument is UQualifiedReferenceExpression) {
        val selector = argument.selector
        if (selector is UCallExpression) {
          val methodName = selector.methodName
          if (methodName == "trimIndent" || methodName == "trimMargin") {
            val location =
              context.getCallLocation(selector, includeReceiver = false, includeArguments = true)

            val fix =
              if (!isUnitTestFile) {
                LintFix.create().replace().all().with("").build()
              } else {
                // Tests: Need to adjust fix to also insert .indented() on parent
                null
              }
            context.report(
              TRIM_INDENT,
              selector,
              location,
              "No need to call `.$methodName()` in issue registration strings; they " +
                "are already trimmed by indent by lint when displaying to users${
                                if (isUnitTestFile) ". Instead, call `.indented()` on the surrounding `${(argument.uastParent as? UCallExpression)?.methodName}()` test file construction" else ""
                                }",
              fix,
            )
          }
        }
      }
    }

    /** Drops template expressions etc. */
    private fun getString(argument: UExpression): String {
      if (argument is UPolyadicExpression) {
        val sb = StringBuilder()
        for (part in argument.operands) {
          sb.append(getString(part))
        }
        return sb.toString()
      } else if (argument is ULiteralExpression) {
        return argument.value?.toString() ?: ""
      } else if (argument is UCallExpression) {
        val receiver = argument.receiver
        if (receiver != null && argument.methodName?.startsWith("trim") == true) {
          return getString(receiver)
        }
      } else if (argument is UQualifiedReferenceExpression) {
        val selector = argument.selector
        if (selector is UCallExpression) {
          val methodName = selector.methodName
          if (methodName?.startsWith("trim") == true) {
            return getString(argument.receiver)
          } else if (methodName == "format") { // string.format
            val args = selector.valueArguments
            for (arg in args) {
              if (arg.getExpressionType()?.canonicalText == JAVA_LANG_STRING) {
                return getString(arg)
              }
            }
          }
        }
      } else if (!isPolyadicFromStringTemplate(argument.uastParent)) {
        val constant = ConstantEvaluator.evaluateString(null, argument, true)
        if (constant != null) {
          return constant
        }
      }

      return ""
    }

    private fun isInCodeFragment(s: String, index: Int): Boolean {
      var escaped = false
      var code = false
      for (i in 0 until index) {
        val c = s[i]
        if (escaped) {
          escaped = false
        } else if (c == '`') {
          code = !code // also works for preformatted text since ``` is an odd number of backticks
        } else if (c == '\\') {
          escaped = true
        }
      }
      return code
    }

    private fun checkTypos(argument: UExpression, string: String) {
      var start = 0
      val length = string.length
      var index = start

      while (true) {
        // Find beginning of next word
        while (index < length && !string[index].isLetter()) {
          if (
            string[index] == '?' &&
              index > 0 &&
              string[index - 1] == ' ' &&
              !isInCodeFragment(string, index)
          ) {
            // warn about ? in error messages separated by a space,
            // but make sure code fragments ('foo ?: bar', `x = '?'`, ...) is ok.
            val fallback = context.getLocation(argument)
            // 5: arbitrarily chosen prefix; long enough to make ? likely to be found uniquely and
            // short enough
            // to usually match when strings are broken up into segments, since we search in source
            // code (like
            // x = "is " + price + " too expensive ?" -- we'll be searching the string "is too
            // expensive ?"; if the
            // window was the whole concatenated string it would not match the sources.
            val window = string.substring(max(0, index - 5), index) + "|?"
            val location = getStringLocation(argument, "?", fallback, window)
            context.report(
              TEXT_FORMAT,
              argument,
              location,
              "Question marks should not be separated by a space",
            )
          }

          index++
        }
        start = index

        // Find end of word
        while (index < length && string[index].isLetter()) {
          index++
        }

        if (index > start) {
          val replacements = typoLookup?.getTypos(string, start, index)
          if (replacements != null) {
            reportTypo(argument, string, start, replacements)
          }
        } else {
          break
        }
      }
    }

    /** For a lint issue explanation, makes sure we're properly joining lines with a backslash. */
    private fun checkContinuations(argument: UExpression, string: String) {
      val lines = string.split("\n")
      var preformat = false
      for (i in 0 until lines.size - 1) {
        val line = lines[i].trim()
        if (line.isEmpty()) {
          continue
        } else if (line.startsWith("```")) {
          preformat = !preformat
          continue
        } else if (preformat || line.endsWith("\\")) {
          continue
        } else if (!line.endsWith("\\")) {
          val next = lines[i + 1].trim()
          if (!impliesNewLine(line, next)) {
            val fallback = context.getLocation(argument)
            val location = getStringLocation(argument, line, fallback)
            context.report(
              TEXT_FORMAT,
              argument,
              location,
              "Multi-line issue explanation strings will interpret line separators as hard breaks, and this " +
                "looks like a continuation of the same paragraph. Consider using \\ at the end of the previous " +
                "line to indicate that the lines should be joined, or add a blank line between unrelated " +
                "sentences, or suppress this issue type here.",
            )
            return
          }
        }
      }
    }

    /**
     * Returns true if the given [line] looks like it implies a new paragraph from the [previous]
     * line. For example, if the previous line ends with ":", the next line is probably a new line.
     * Similarly, if the line starts with a list bullet, or a preformatted text block, etc.
     */
    private fun impliesNewLine(previous: String, line: String): Boolean {
      // If the next line is blank, it's a new paragraph
      if (line.isEmpty()) {
        return true
      }

      // If the previous line ends with ":", it's a new paragraph
      if (previous.endsWith(":")) {
        return true
      }

      // If the next line is a list item, such as
      //   - this
      //   * this
      //   1. this
      //   (1) this
      if (
        line.startsWith("-") ||
          line.startsWith("*") ||
          line.startsWith("```") ||
          line.first() == '(' && line.matches(Regex("""\([0-9A-Za-z.]+\).*""")) ||
          line.first().isDigit() && line.matches(Regex("""\d+\..*"""))
      ) {
        return true
      }

      return false
    }

    private fun checkLintString(argument: UExpression, string: String) {
      // Validate URLs in the string
      checkUrls(argument, string)

      // Look for typos
      checkTypos(argument, string)

      // Using preformatted text for symbols?
      // See if it's already doing so
      if (string.contains("`")) {
        return
      }

      // Look for likely candidates of symbols that should be capitalized:
      // camelcase expressions, and function calls.
      checkForCodeFragments(XML_PATTERN, string, argument, "an XML reference") ||
        checkForCodeFragments(CALL_PATTERN, string, argument, "a call") ||
        checkForCodeFragments(CAMELCASE_PATTERN, string, argument, "a code reference")

      // Look for string continuations that probably should be preceded by a space character
      checkConcatenations(argument, string)
    }

    private fun checkConcatenations(argument: UExpression, string: String) {
      var offset = 0
      val s = string.trimIndent()
      while (true) {
        offset = s.indexOf("\\\n", offset)
        if (offset == -1) {
          return
        }
        if (offset > 0 && !s[offset - 1].isWhitespace()) {
          val begin = s.lastIndexOf('\n', offset) + 1
          val location =
            getStringLocation(
              argument,
              s.substring(offset - 1, offset + 1),
              window = s.substring(begin, offset - 1) + "|",
            )
          var wordBegin = offset - 1
          while (wordBegin > 0 && !s[wordBegin - 1].isWhitespace()) {
            wordBegin--
          }
          var nextWordEnd = offset + 2
          while (nextWordEnd < s.length - 1 && !s[nextWordEnd].isWhitespace()) {
            nextWordEnd++
          }
          val prevWord = s.substring(wordBegin, offset - 1)
          val nextWord = s.substring(offset + 2, nextWordEnd)
          context.report(
            TEXT_FORMAT,
            argument,
            location,
            "This line continuation (**\\**) should probably be preceded by a space character, " +
              "otherwise this will render as a single word \"$prevWord$nextWord\", not \"$prevWord $nextWord\"",
            LintFix.create().name("Insert space").replace().text("\\").with(" \\").build(),
          )
        }

        offset += 2
      }
    }

    private fun checkForCodeFragments(
      pattern: Regex,
      string: String,
      argument: UExpression,
      typeString: String,
    ): Boolean {
      val xml = pattern.find(string)
      return if (xml != null) {
        val s = xml.groupValues[0]
        // Make sure string is really there; may not be the case if we
        // did constant propagation and the string itself is elsewhere
        val fallback = context.getLocation(argument)
        val location = getStringLocation(argument, s, fallback)
        val canFix = location !== fallback || locationContains(location, string)
        val fix = if (canFix) createSurroundFix(s, location) else null
        context.report(
          TEXT_FORMAT,
          argument,
          location,
          "\"$s\" looks like $typeString; surround with backtics in string to display as symbol, e.g. \\`$s\\`",
          fix,
        )
        true
      } else {
        false
      }
    }

    private fun createSurroundFix(s: String, location: Location): LintFix {
      return LintFix.create()
        .name("Surround with backtics")
        .replace()
        .text(s)
        .with("`$s`")
        .range(location)
        .autoFix()
        .build()
    }

    /** Report the typo found at the given offset and suggest the given replacements. */
    private fun reportTypo(
      argument: UExpression,
      text: String,
      begin: Int,
      replacements: List<String>,
    ) {
      if (replacements.size < 2) { // first is the typo itself
        return
      }
      val typo = replacements[0]
      val word = text.substring(begin, begin + typo.length)
      var first: String? = null
      val message: String
      val fixBuilder: GroupBuilder = LintFix.create().alternatives()
      val isCapitalized = Character.isUpperCase(word[0])
      val sb = java.lang.StringBuilder(40)
      var i = 1
      val n = replacements.size
      while (i < n) {
        var replacement = replacements[i]
        if (first == null) {
          first = replacement
        }
        if (sb.isNotEmpty()) {
          sb.append(" or ")
        }
        sb.append('"')
        if (isCapitalized) {
          replacement = replacement.usLocaleCapitalize()
        }
        sb.append(replacement)
        fixBuilder.add(
          LintFix.create()
            .name("Replace with \"$replacement\"")
            .replace()
            .text(word)
            .with(replacement)
            .build()
        )
        sb.append('"')
        i++
      }
      val fix = fixBuilder.build()
      val fallback = context.getLocation(argument)
      val location = getStringLocation(argument, word, fallback)
      val canFix = location !== fallback || locationContains(location, word)
      message =
        if (first != null && first.equals(word, ignoreCase = true)) {
          if (first == word) {
            return
          }
          "\"$word\" is usually capitalized as \"$first\""
        } else {
          "\"$word\" is a common misspelling; did you mean $sb?"
        }
      context.report(TEXT_FORMAT, argument, location, message, if (canFix) fix else null)
    }

    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      val operator = node.operator
      // Note that we deliberately don't enforce IDENTITY_EQUALS or IDENTITY_NOT_EQUALS;
      // these are probably okay since clients are not under the impression that
      // equivalence is expected
      if (operator == UastBinaryOperator.EQUALS || operator == UastBinaryOperator.NOT_EQUALS) {
        checkEquals(node, node.leftOperand.getExpressionType(), node.leftOperand, node.rightOperand)
      }
      return super.visitBinaryExpression(node)
    }

    private fun checkCall(
      call: UCallExpression,
      expectedContainer: String,
      message: String,
      requireUastReceiver: Boolean = false,
    ) {
      if (requireUastReceiver) {
        call.receiverType?.let {
          val evaluator = context.evaluator
          val typeClass = evaluator.getTypeClass(it)
          if (typeClass != null && !evaluator.inheritsFrom(typeClass, CLASS_U_ELEMENT, false)) {
            return
          }
        }
      }
      val method = call.resolve() ?: return
      if (context.evaluator.isMemberInSubClassOf(method, expectedContainer)) {
        context.report(USE_UAST, call, context.getLocation(call), message)
      }
    }

    private fun checkEquals(node: UElement, type: PsiType?, arg1: UElement?, arg2: UElement?) {
      if (type is PsiClassType) {
        val psiClass = type.resolve()
        if (
          psiClass != null &&
            context.evaluator.inheritsFrom(psiClass, CLASS_PSI_ELEMENT, strict = false)
        ) {
          if (arg1?.isNullLiteral() == true) { // comparisons with null are ok
            return
          }
          if (arg2?.isNullLiteral() == true) {
            return
          }
          val message =
            "Don't compare PsiElements with `equals`, use " + "`isEquivalentTo(PsiElement)` instead"
          context.report(PSI_COMPARE, node, context.getLocation(node), message)
        }
      }
    }
  }

  companion object {
    private const val CLASS_LINT_CLIENT = "com.android.tools.lint.client.api.LintClient"
    private const val CLASS_DETECTOR = "com.android.tools.lint.detector.api.Detector"
    private const val CLASS_ISSUE_REGISTRY = "com.android.tools.lint.client.api.IssueRegistry"
    private const val CLASS_CONTEXT = "com.android.tools.lint.detector.api.Context"
    private const val CLASS_ISSUE = "com.android.tools.lint.detector.api.Issue"
    private const val CLASS_ISSUE_COMPANION = "com.android.tools.lint.detector.api.Issue.Companion"
    private const val CLASS_TEST_LINT_TASK =
      "com.android.tools.lint.checks.infrastructure.TestLintTask"
    private const val CLASS_TEST_LINT_RESULT =
      "com.android.tools.lint.checks.infrastructure..TestLintResult"
    private const val CLASS_PSI_METHOD = "com.intellij.psi.PsiMethod"
    private const val CLASS_PSI_ELEMENT = "com.intellij.psi.PsiElement"
    private const val CLASS_PSI_VARIABLE = "com.intellij.psi.PsiVariable"
    private const val CLASS_PSI_TREE_UTIL = "com.intellij.psi.util.PsiTreeUtil"
    private const val CLASS_PSI_MEMBER = "com.intellij.psi.PsiMember"
    private const val CLASS_U_ELEMENT = "org.jetbrains.uast.UElement"

    private const val DOLLAR_STRING = "\${\"$\"}"
    private const val DOLLAR_CHAR = "\${'$'}"

    // TODO: use character classes for java identifier part
    private val CAMELCASE_PATTERN = Regex("[a-zA-Z]+[a-z]+[A-Z][a-z]+")
    private val CALL_PATTERN = Regex("[a-zA-Z().=]+\\(.*\\)")
    private val XML_PATTERN = Regex("<.+>")

    private val IMPLEMENTATION =
      Implementation(LintDetectorDetector::class.java, Scope.JAVA_FILE_SCOPE)

    private val TEST_IMPLEMENTATION =
      Implementation(
        LintDetectorDetector::class.java,
        EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
      )

    /** Expected lint id format. */
    @JvmField
    val ID =
      Issue.create(
        id = "LintImplIdFormat",
        briefDescription = "Lint ID Format",
        explanation =
          """
                    This check looks at lint issue id registrations and makes sure the id \
                    follows the expected conventions: capitalized, camel case, no spaces, \
                    and not too long.

                    Note: You shouldn't change id's for lint checks that are already widely \
                    used, since the id can already appear in `@SuppressLint` annotations, \
                    `tools:ignore=` attributes, lint baselines, Gradle `lintOptions` blocks, \
                    `lint.xml` files, and so on. In these cases, just explicitly suppress this \
                    warning instead using something like

                    ```kotlin
                    @JvmField
                    val ISSUE = Issue.create(
                        // ID string is too long, but we can't change this now since
                        // this id is already used in user suppress configurations
                        //noinspection LintImplIdFormat
                        id = "IncompatibleMediaBrowserServiceCompatVersion",
                        ...
                    ```
                """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Bad URLs in issue registrations. */
    @JvmField
    val CHECK_URL =
      Issue.create(
        id = "LintImplBadUrl",
        briefDescription = "Bad More Info Link",
        explanation =
          """
                   More Info URLs let a link check point to additional resources about \
                   the problem and solution it's checking for.

                   This check validates the URLs in various ways, such as making sure that \
                   issue tracker links look correct. It may also at some point touch the network \
                   to make sure that the URLs are actually still reachable.
                """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Unexpected URL domain. */
    @JvmField
    val UNEXPECTED_DOMAIN =
      Issue.create(
        id = "LintImplUnexpectedDomain",
        briefDescription = "Unexpected URL Domain",
        explanation =
          """
                    This checks flags URLs to domains that have not been explicitly \
                    allowed for use as a documentation source.
                """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = Severity.ERROR,
        // This is really specific to our built-in checks; turn it off by default
        // such that it doesn't by default flag problems in third party lint checks
        enabledByDefault = false,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Suggestions around lint string formats. */
    @JvmField
    val TEXT_FORMAT =
      Issue.create(
        id = "LintImplTextFormat",
        briefDescription = "Lint Text Format",
        explanation =
          """
                    Lint supports various markdown like formatting directives in all of its \
                    strings (issue explanations, reported error messages, etc).

                    This lint check looks for strings that look like they may benefit from \
                    additional formatting. For example, if a snippet looks like code it should \
                    be surrounded with backticks.

                    Note: Be careful changing **existing** strings; this may stop baseline file \
                    matching from working, so consider suppressing existing violations of this \
                    check if this is an error many users may be filtering in baselines. (This \
                    is only an issue for strings used in `report` calls; for issue registration \
                    strings like summaries and explanations there's no risk changing the text \
                    contents.)
                """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Should reuse existing constants. */
    @JvmField
    val EXISTING_LINT_CONSTANTS =
      Issue.create(
        id = "LintImplUseExistingConstants",
        briefDescription = "Use Existing Lint Constants",
        explanation =
          """
                    This check looks for opportunities to reuse predefined lint constants.
                """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Calling PSI methods when you should be calling UAST methods. */
    @JvmField
    val USE_UAST =
      Issue.create(
        id = "LintImplUseUast",
        briefDescription = "Using Wrong UAST Method",
        explanation =
          """
                    UAST is a library that sits on top of PSI, and in many cases PSI is \
                    part of the UAST API; for example, UResolvable#resolve returns a \
                    PsiElement.

                    Also, for convenience, a UClass is a PsiClass, a UMethod is a PsiMethod, \
                    and so on.

                    However, there are some parts of the PSI API that does not work correctly \
                    when used in this way. For example, if you call `PsiMethod#getBody` or \
                    `PsiVariable#getInitializer`, this will only work in Java, not for \
                    Kotlin (or potentially other languages).

                    There are UAST specific methods you need to call instead and lint will \
                    flag these.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Comparing PSI elements with equals. */
    @JvmField
    val PSI_COMPARE =
      Issue.create(
        id = "LintImplPsiEquals",
        briefDescription = "Comparing PsiElements with Equals",
        explanation =
          """
                    You should never compare two PSI elements for equality with `equals`; \
                    use `PsiEquivalenceUtil.areElementsEquivalent(PsiElement, PsiElement)` instead.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
        // There are still exceptions to this rule; see for example the tests for
        // SamDetector if you try to change the example in that detector
        enabledByDefault = false,
      )

    /** Still writing lint checks in Java. */
    @JvmField
    val USE_KOTLIN =
      Issue.create(
        id = "LintImplUseKotlin",
        briefDescription = "Non-Kotlin Lint Detectors",
        explanation =
          """
                    New lint checks should be written in Kotlin; the Lint API is written in \
                    Kotlin and uses a number of language features that makes it beneficial \
                    to also write the lint checks in Kotlin. Examples include many extension \
                    functions (as well as in UAST), default and named parameters (for the \
                    Issue registration methods for example where there are methods with 12+ \
                    parameters with only a couple of required ones), and so on.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** IssueRegistry not providing a vendor. */
    @JvmField
    val MISSING_VENDOR =
      Issue.create(
        id = "MissingVendor",
        briefDescription = "IssueRegistry not providing a vendor",
        explanation =
          """
                    Recent versions of lint includes a `vendor` property (or from Java, \
                    `getVendor` and `setVendor` methods) on `IssueRegistry`.

                    You should override this property and point to a suitable vendor \
                    instance where you list the author (or organization or vendor) \
                    providing the lint check, a feedback URL, etc. (See the Vendor \
                    documentation.)

                    The vendor info is included in a few places (such as HTML reports) \
                    and partially in a few other places (such as the identifier showing \
                    up at the end of each error line in the text output). This makes it \
                    easier for users to figure out where checks are coming from, since \
                    lint will pull in lint checks from a number of sources, and makes \
                    it clear where to go to provide feedback or file bug reports or \
                    requests.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = WARNING,
        implementation = IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Calling .trimIndent() on messages intended for lint. */
    @JvmField
    val TRIM_INDENT =
      Issue.create(
        id = "LintImplTrimIndent",
        briefDescription = "Calling `.trimIndent` on Lint Strings",
        explanation =
          """
                    Lint implicitly calls `.trimIndent()` (lazily, at the last minute) in \
                    a number of places:
                    * Issue explanations
                    * Error messages
                    * Lint test file descriptions
                    * etc

                    That means you don't need to put `.trimIndent()` in your source code \
                    to handle this.

                    There are advantages to **not** putting `.trimIndent()` in the code. \
                    For test files, if you call for example `kotlin(""\"source code"\""\")` \
                    then IntelliJ/Android Studio will syntax highlight the source code as \
                    Kotlin. The second you add in a .trimIndent() on the string, the syntax \
                    highlighting goes away. For test files you can instead call ".indented()" \
                    on the test file builder to get it to indent the string.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = TEST_IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** Using ${"$"} or ${'$'} in Kotlin string literals in lint unit tests. */
    @JvmField
    val DOLLAR_STRINGS =
      Issue.create(
        id = "LintImplDollarEscapes",
        briefDescription = "Using Dollar Escapes",
        //noinspection LintImplDollarEscapes
        explanation =
          """
                    Instead of putting `${"$"}{"$"}` in your Kotlin raw string literals \
                    you can simply use ＄. This looks like the dollar sign but is instead \
                    the full width dollar sign, U+FF04. And this character does not need \
                    to be escaped in Kotlin raw strings, since it does not start a \
                    string template.

                    Lint will automatically convert references to ＄ in unit test files into \
                    a real dollar sign, and when pulling results and error messages out of \
                    lint, the dollar sign back into the full width dollar sign.

                    That means you can use ＄ everywhere instead of `${"$"}{"$"}`, which makes \
                    the test strings more readable -- especially `${"$"}`-heavy code such as \
                    references to inner classes.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 4,
        severity = Severity.ERROR,
        implementation = TEST_IMPLEMENTATION,
        platforms = JDK_SET,
      )

    /** No documentation example in unit tests */
    @JvmField
    val MISSING_DOC_EXAMPLE =
      Issue.create(
        id = "LintDocExample",
        briefDescription = "Missing Documentation Example",
        explanation =
          """
                    Lint's tool for generating documentation for each issue has special \
                    support for including a code example which shows how to trigger \
                    the report. It will pick the first unit test it can find and pick out \
                    the source file referenced from the error message, but you can instead \
                    designate a unit test to be the documentation example, and in that case, \
                    all the files are included.

                    To designate a unit test as the documentation example for an issue, \
                    name the test `testDocumentationExample`, or if your detector reports \
                    multiple issues, `testDocumentationExample`<Id>, such as \
                    `testDocumentationExampleMyId`.
                    """,
        category = CUSTOM_LINT_CHECKS,
        priority = 6,
        severity = WARNING,
        implementation = TEST_IMPLEMENTATION,
        platforms = JDK_SET,
        enabledByDefault = false,
      )
  }
}
