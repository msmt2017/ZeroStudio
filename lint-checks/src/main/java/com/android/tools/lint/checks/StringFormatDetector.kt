/*
 * Copyright (C) 2011 The Android Open Source Project
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

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType
import com.android.resources.ResourceUrl
import com.android.tools.lint.checks.StringFormatDetector.StringFormatType.FORMATTED
import com.android.tools.lint.checks.StringFormatDetector.StringFormatType.IGNORE
import com.android.tools.lint.checks.StringFormatDetector.StringFormatType.NOT_FORMATTED
import com.android.tools.lint.client.api.ResourceRepositoryScope.LOCAL_DEPENDENCIES
import com.android.tools.lint.client.api.TYPE_BOOLEAN_WRAPPER
import com.android.tools.lint.client.api.TYPE_BYTE_WRAPPER
import com.android.tools.lint.client.api.TYPE_CHARACTER_WRAPPER
import com.android.tools.lint.client.api.TYPE_DOUBLE_WRAPPER
import com.android.tools.lint.client.api.TYPE_FLOAT_WRAPPER
import com.android.tools.lint.client.api.TYPE_INTEGER_WRAPPER
import com.android.tools.lint.client.api.TYPE_LONG_WRAPPER
import com.android.tools.lint.client.api.TYPE_OBJECT
import com.android.tools.lint.client.api.TYPE_SHORT_WRAPPER
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Issue.Companion.create
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.Location.Companion.create
import com.android.tools.lint.detector.api.Location.ResourceItemHandle
import com.android.tools.lint.detector.api.ResourceEvaluator
import com.android.tools.lint.detector.api.ResourceXmlDetector
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.XmlContext
import com.android.tools.lint.detector.api.getFileNameWithParent
import com.android.tools.lint.detector.api.isEnglishResource
import com.android.tools.lint.detector.api.isErroneous
import com.android.tools.lint.detector.api.isKotlin
import com.android.utils.CharSequences
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.Sets
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypes
import com.intellij.psi.PsiVariable
import java.util.EnumSet
import java.util.Locale
import java.util.regex.MatchResult
import java.util.regex.Pattern
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULiteralExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isArrayInitializer
import org.jetbrains.uast.util.isNewArray
import org.jetbrains.uast.util.isNewArrayWithDimensions
import org.jetbrains.uast.util.isNewArrayWithInitializer
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Check which looks for problems with formatting strings such as inconsistencies between
 * translations or between string declaration and string usage in Java.
 *
 * TODO
 * * Handle Resources.getQuantityString as well
 * * Remove all the batch mode handling here; instead of accumulating all strings we can now limit
 *   the analysis directly to resolving strings from String#format calls, so there's no longer any
 *   ambiguity about what is a formatting string and what is not. One small challenge is what to do
 *   about formatted= attributes which we can't look up later; maybe only flag these in batch mode.
 *   (It's also unlikely to happen; these strings tend not to be used from String#format).
 * * Add support for Kotlin strings
 */
class StringFormatDetector : ResourceXmlDetector(), SourceCodeScanner {
  /**
   * Map from a format string name to a declaration file and actual formatting string of default
   * resource variant.
   */
  private val mFormatStrings: MutableMap<String, Pair<Location.Handle, String>> = LinkedHashMap()

  /** Map of strings that do not contain any formatting. */
  private val mNotFormatStrings: MutableMap<String, Location.Handle> = LinkedHashMap()

  /**
   * Set of strings that have an unknown format such as date formatting; we should not flag these as
   * invalid when used from a String#format call
   */
  private val mIgnoreStrings: MutableSet<String> = HashSet()

  /**
   * Map from a format string name to list of objects with actual formatting info. We're using a
   * list since a format string can be defined multiple times, usually for different translations.
   */
  private val mStringsFromPsiCache: MutableMap<String, List<FormatString>> = LinkedHashMap()

  override fun appliesTo(folderType: ResourceFolderType): Boolean {
    return folderType == ResourceFolderType.VALUES
  }

  override fun getApplicableElements(): Collection<String> {
    return listOf(SdkConstants.TAG_STRING)
  }

  override fun visitElement(context: XmlContext, element: Element) {
    val text = element.textContent
    if (text.isEmpty()) {
      return
    }

    if (!context.project.reportIssues) {
      // If this is a library project not being analyzed, ignore it
      return
    }

    if (!crossCheckResources(context)) {
      checkTextNode(context, element, stripQuotes(text))
    } else if (context.phase == 1 && context.getFolderConfiguration()?.isDefault == true) {
      val formatType = checkTextNode(context, element, stripQuotes(text))
      checkArityAndTypes(context, element, formatType)

      val name = element.getAttribute(SdkConstants.ATTR_NAME)
      when (formatType) {
        IGNORE -> {
          mIgnoreStrings.add(name)
        }
        FORMATTED -> {
          mFormatStrings[name] = (createLocationHandleForXmlDomElement(context, element) to text)
        }
        NOT_FORMATTED -> {
          mNotFormatStrings[name] = createLocationHandleForXmlDomElement(context, element)
        }
      }
    } else if (context.phase == 2 && context.getFolderConfiguration()?.isDefault == false) {
      val formatType = checkTextNode(context, element, stripQuotes(text))
      checkArityAndTypes(context, element, formatType)
    }
  }

  private fun createLocationHandleForXmlDomElement(
    context: XmlContext,
    element: Element,
  ): Location.Handle {
    val handle = context.createLocationHandle(element)
    handle.clientData = element
    return handle
  }

  override fun afterCheckRootProject(context: Context) {
    if (context.phase == 1 && crossCheckResources(context)) {
      context.requestRepeat(this, Scope.ALL_RESOURCES_SCOPE)
    }
  }

  override fun getApplicableMethodNames(): List<String> {
    return listOf(
      SdkConstants.FORMAT_METHOD,
      SdkConstants.GET_STRING_METHOD,
      STRING_RESOURCE_METHOD,
    )
  }

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    if (context.phase != 1) {
      return
    }
    val evaluator = context.evaluator
    val methodName = method.name
    if (methodName == SdkConstants.FORMAT_METHOD) {
      val cls = method.containingClass?.qualifiedName
      if (cls == TYPE_STRING) {
        // Check formatting parameters for
        //   java.lang.String#format(String format, Object... formatArgs)
        //   java.lang.String#format(Locale locale, String format, Object... formatArgs)
        checkStringFormatCall(
          context,
          method,
          node,
          if (method.parameterList.parametersCount == 3) 1 else 0,
        )
      } else if (
        cls == LocaleDetector.KOTLIN_STRINGS_JVM_KT || cls == LocaleDetector.KOTLIN_STRINGS_KT
      ) {
        // Kotlin stdlib extension functions from kotlin.txt
        //   public inline fun String.Companion.format(format: String, vararg args: Any?): String =
        // java.lang.String.format(format, *args)
        //   public inline fun String.format(locale: Locale?, vararg args: Any?): String =
        // java.lang.String.format(locale, this, *args)
        checkStringFormatCall(
          context,
          method,
          node,
          if (method.parameterList.parametersCount == 4) 1 else 0,
        )

        // TODO: Consider also enforcing
        // java.util.Formatter#format(String string, Object... formatArgs)
      }
    } else if (methodName == STRING_RESOURCE_METHOD) {
      if (method.parameterList.parametersCount == 2) {
        val name = method.containingClass?.qualifiedName
        if (name == "androidx.compose.ui.res.StringResources_androidKt") {
          checkStringFormatCall(context, method, node, 0)
        }
      }
    } else {
      // Look up any of these string formatting methods:
      // android.content.res.Resources#getString(@StringRes int resId, Object... formatArgs)
      // android.content.Context#getString(@StringRes int resId, Object... formatArgs)
      // android.app.Fragment#getString(@StringRes int resId, Object... formatArgs)
      // android.support.v4.app.Fragment#getString(@StringRes int resId, Object... formatArgs)

      // Many of these also define a plain getString method:
      // android.content.res.Resources#getString(@StringRes int resId)
      // However, while it's possible that these contain formatting strings, it's
      // also possible that they're looking up strings that are not intended to be used
      // for formatting so while we may want to warn about this it's not necessarily
      // an error.
      if (method.parameterList.parametersCount < 2) {
        return
      }
      if (
        evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_RESOURCES, false) ||
          evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_CONTEXT, false) ||
          evaluator.isMemberInSubClassOf(method, SdkConstants.CLASS_FRAGMENT, false) ||
          evaluator.isMemberInSubClassOf(
            method,
            AndroidXConstants.CLASS_V4_FRAGMENT.oldName(),
            false,
          ) ||
          evaluator.isMemberInSubClassOf(
            method,
            AndroidXConstants.CLASS_V4_FRAGMENT.newName(),
            false,
          )
      ) {
        checkStringFormatCall(context, method, node, 0)
      }

      // TODO: Consider also looking up
      // android.content.res.Resources#getQuantityString(@PluralsRes int id, int quantity,
      //              Object... formatArgs)
      // though this will require being smarter about cross referencing formatting
      // strings since we'll need to go via the quantity string definitions
    }
  }

  private fun crossCheckResources(context: Context): Boolean {
    return context.isEnabled(ARG_COUNT) ||
      context.isEnabled(INVALID) ||
      context.isEnabled(ARG_TYPES)
  }

  /**
   * Check the given String.format call (with the given arguments) to see if the string format is
   * being used correctly
   *
   * @param context the context to report errors to
   * @param calledMethod the method being called
   * @param call the AST node for the [String.format]
   * @param argIndex the index of the string argument, normally 0 but 1 if locale is specified, or 1
   *   or 2 in extension methods
   */
  private fun checkStringFormatCall(
    context: JavaContext,
    calledMethod: PsiMethod,
    call: UCallExpression,
    argIndex: Int,
  ) {
    val args = call.valueArguments
    if (args.size <= argIndex) {
      return
    }
    val argument = args[argIndex]
    val resource = ResourceEvaluator.getResource(context.evaluator, argument)
    if (resource == null || resource.isFramework || resource.type != ResourceType.STRING) {
      checkTrivialString(context, calledMethod, call, args, argIndex)
      return
    }
    val name = resource.name
    if (mIgnoreStrings.contains(name)) {
      return
    }
    var passingVarArgsArray = false
    var callCount = args.size - 1 - argIndex
    if (callCount == 1) {
      // If instead of a varargs call like
      //    getString(R.string.foo, arg1, arg2, arg3)
      // the code is calling the varargs method with a packed Object array, as in
      //    getString(R.string.foo, new Object[] { arg1, arg2, arg3 })
      // we'll need to handle that such that we don't think this is a single
      // argument
      var lastArg: UExpression? = args[args.size - 1].skipParenthesizedExprDown()
      val parameterList = calledMethod.parameterList
      val parameterCount = parameterList.parametersCount
      if (parameterCount > 0 && parameterList.parameters[parameterCount - 1].isVarArgs) {
        var knownArity = false
        var argWasReference = false
        if (lastArg is UReferenceExpression) {
          val resolved = lastArg.resolve()
          if (resolved is PsiVariable) {
            var initializer = UastFacade.getInitializerBody((resolved as PsiVariable?)!!)
            if (initializer != null) {
              initializer = initializer.skipParenthesizedExprDown()
            }
            if (
              initializer != null && (initializer.isNewArray() || initializer.isArrayInitializer())
            ) {
              argWasReference = true
              // Now handled by check below
              lastArg = initializer
            }
          }
        }
        if (lastArg != null && (lastArg.isNewArray() || lastArg.isArrayInitializer())) {
          val arrayInitializer = lastArg as UCallExpression
          if (lastArg.isNewArrayWithInitializer() || lastArg.isArrayInitializer()) {
            callCount = arrayInitializer.valueArgumentCount
            knownArity = true
          } else if (lastArg.isNewArrayWithDimensions()) {
            val arrayDimensions = arrayInitializer.valueArguments
            if (arrayDimensions.size == 1) {
              val first = arrayDimensions[0].skipParenthesizedExprDown()
              if (first is ULiteralExpression) {
                val o = first.value
                if (o is Int) {
                  callCount = o
                  knownArity = true
                }
              }
            }
          }
          if (!knownArity) {
            if (!argWasReference) {
              return
            }
          } else {
            passingVarArgsArray = true
          }
        }
      }
    }

    //
    // Backward Compatibility
    //
    // Originally (for same error) detector was implemented to highlight whole XML line during
    // global analysis, while for incremental check only highlight resource value
    // see @testAdditionalGetStringMethods and @testAdditionalGetStringMethodsIncrementally test
    //
    // Here we use bits of previous logic, to preserve highlighting behaviour
    val alreadyVisited = mFormatStrings.contains(name) || mNotFormatStrings.containsKey(name)
    val valueOnlyHandle = !alreadyVisited

    val client = context.client
    if (mStringsFromPsiCache[name] == null) {
      val full = context.isGlobalAnalysis()
      val project = if (full) context.mainProject else context.project
      val resources = client.getResources(project, LOCAL_DEPENDENCIES)
      val items = resources.getResources(ResourceNamespace.TODO(), ResourceType.STRING, name)
      val formatStrings =
        items
          .map { resourceFormatString(name, it, resources) }
          .filter { it.type != IGNORE }
          .sortedWith { h1: FormatString, h2: FormatString ->
            val item1 = h1.resourceItem!!
            val item2 = h2.resourceItem!!
            val f1 = item1.configuration
            val f2 = item2.configuration
            val delta = f1.compareTo(f2)
            if (delta != 0) {
              delta
            } else item1.toString().compareTo(item2.toString())
          }
      mStringsFromPsiCache[name] = formatStrings

      // Check string consistency for incremental analysis.
      // (for global analysis this is done while visiting XML resources)
      if (!alreadyVisited) {
        visitResourceItems(context, name, formatStrings, valueOnlyHandle)
      }
    }

    val list = mStringsFromPsiCache[name]
    if (!list.isNullOrEmpty()) {

      // check Not Formatted strings
      if (callCount > 0) {
        for (item in list) {
          var found = false
          if (item.type == NOT_FORMATTED) {
            checkNotFormattedHandle(
              context,
              call,
              name,
              item.createResourceItemHandle(context, valueOnlyHandle),
            )
            found = true
          }
          if (found) return
        }
      }

      var reported: MutableSet<String>? = null
      for (formatString in list) {
        if (formatString.type != FORMATTED) continue
        val s = formatString.value!!
        if (reported != null && reported.contains(s)) {
          continue
        }
        val count = getFormatArgumentCount(s, null)
        if (count != callCount) {
          val location = context.getLocation(call)
          val handle = formatString.createResourceItemHandle(context, valueOnlyHandle)!!
          val secondary = handle.resolve()
          secondary.message =
            String.format(
              Locale.US,
              "This definition requires %1\$d argument%2\$s",
              count,
              if (count != 1) "s" else "",
            )
          location.secondary = secondary
          val message =
            String.format(
              Locale.US,
              "Wrong argument count, format string `%1\$s` requires `%2\$d` but format " +
                "call supplies `%3\$d`",
              name,
              count,
              callCount,
            )
          context.report(ARG_TYPES, call, location, message)
          if (reported == null) {
            reported = Sets.newHashSet()
          }
          reported!!.add(s)
        } else {
          if (passingVarArgsArray) {
            // Can't currently check these: make sure we don't incorrectly
            // flag parameters on the Object[] instead of the wrapped parameters
            return
          }
          for (i in 1..count) {
            val argumentIndex = i + argIndex
            val expression = args[argumentIndex]
            var type = expression.getExpressionType()
            if (isInStringExpression(call, expression)) {
              type = getStringType(context, expression)
            }
            if (type != null && !type.isErroneous()) {
              var valid = true
              val formatType = getFormatArgumentType(s, i) ?: continue
              val last = formatType[formatType.length - 1]
              if (
                formatType.length >= 2 && formatType[formatType.length - 2].lowercaseChar() == 't'
              ) {
                // Date time conversion.
                // TODO
                continue
              }
              when (last) {
                'b',
                'B' -> valid = isBooleanType(type)
                'x',
                'X',
                'd',
                'o',
                'e',
                'E',
                'f',
                'g',
                'G',
                'a',
                'A' -> valid = isNumericOrBigNumberType(type)
                'c',
                'C' -> // Unicode character
                valid = isCharacterType(type)
                'h',
                'H' -> // From
                  // https://developer.android.com/reference/java/util/Formatter.html
                  // """The following general conversions may be applied to any
                  // argument type: 'b', 'B', 'h', 'H', 's', 'S' """
                  // We'll still warn about %s since you may have intended
                  // numeric formatting, but hex printing seems pretty well
                  // intended.
                  continue
                's',
                'S' -> // String. Can pass anything, but warn about
                  // numbers since you may have meant more
                  // specific formatting. Use special issue
                  // explanation for this?
                  valid = !isBooleanType(type) && !isNumericType(type)
              }
              if (!valid) {
                val location = context.getLocation(args[argumentIndex])
                val handle = formatString.createResourceItemHandle(context, valueOnlyHandle)!!
                val secondary = handle.resolve()
                secondary.message = "Conflicting argument declaration here"
                location.secondary = secondary
                if (isSuppressed(context, ARG_TYPES, secondary)) {
                  continue
                }
                var suggestion: String? = null
                if (isBooleanType(type)) {
                  suggestion = "`b`"
                } else if (isCharacterType(type)) {
                  suggestion = "'c'"
                } else if (isIntType(type)) {
                  suggestion = "`d`, 'o' or `x`"
                } else if (isFloatType(type)) {
                  suggestion = "`e`, 'f', 'g' or `a`"
                } else if (type is PsiClassType && type.getCanonicalText() == TYPE_OBJECT) {
                  suggestion = "'s' or 'h'"
                }
                suggestion =
                  if (suggestion != null) {
                    " (Did you mean formatting character $suggestion?)"
                  } else {
                    ""
                  }
                var canonicalText = type.canonicalText
                canonicalText = canonicalText.substring(canonicalText.lastIndexOf('.') + 1)
                var message =
                  String.format(
                    Locale.US,
                    "Wrong argument type for formatting argument '#%1\$d' " +
                      "in `%2\$s`: conversion is '`%3\$s`', received `%4\$s` " +
                      "(argument #%5\$d in method call)%6\$s",
                    i,
                    name,
                    formatType,
                    canonicalText,
                    argumentIndex + 1,
                    suggestion,
                  )
                if ((last == 's' || last == 'S') && isNumericType(type)) {
                  message =
                    String.format(
                      Locale.US,
                      "Suspicious argument type for formatting argument #%1\$d " +
                        "in `%2\$s`: conversion is `%3\$s`, received `%4\$s` " +
                        "(argument #%5\$d in method call)%6\$s",
                      i,
                      name,
                      formatType,
                      canonicalText,
                      argumentIndex + 1,
                      suggestion,
                    )
                }
                context.report(ARG_TYPES, call, location, message)
                if (reported == null) {
                  reported = Sets.newHashSet()
                }
                reported!!.add(s)
              }
            }
          }
        }
      }
    }
  }

  private fun resourceFormatString(
    name: String,
    item: ResourceItem,
    resources: ResourceRepository,
  ): FormatString {
    var v: ResourceValue? = item.resourceValue ?: return FormatString(name, IGNORE)
    var value: String? = v?.rawXmlValue ?: return FormatString(name, IGNORE)
    // Attempt to resolve indirection
    if (isReference(value!!)) {
      // Only resolve a few indirections
      for (i in 0..2) {
        val url = ResourceUrl.parse(value!!)
        if (url == null || url.isFramework) {
          break
        }
        val l = resources.getResources(ResourceNamespace.TODO(), url.type, url.name)
        if (l.isNotEmpty()) {
          v = l[0].resourceValue
          if (v != null) {
            value = v.value
            if (value == null || !isReference(value)) {
              break
            }
          } else {
            break
          }
        } else {
          break
        }
      }
    }
    if (value != null && !isReference(value)) {
      // Make sure it's really a formatting string,
      // not for example "Battery remaining: 90%"
      var isFormattingString = value.indexOf('%') != -1
      var j = 0
      val m = value.length
      while (j < m && isFormattingString) {
        val c = value[j]
        if (c == '\\') {
          j++
        } else if (c == '%') {
          val matcher = FORMAT.matcher(value)
          if (!matcher.find(j)) {
            isFormattingString = false
          } else {
            val conversion = matcher.group(6)
            val conversionClass = getConversionClass(conversion[0])
            if (conversionClass == CONVERSION_CLASS_UNKNOWN || matcher.group(5) != null) {
              // Some date format etc - don't process
              return FormatString(name, IGNORE)
            }
          }
          j++ // Don't process second % in a %%
        }
        j++
      }
      return if (isFormattingString) {
        FormatString(name, FORMATTED, item, value)
      } else {
        FormatString(name, NOT_FORMATTED, item)
      }
    }
    return FormatString(name, IGNORE)
  }

  private fun visitResourceItems(
    context: Context,
    name: String,
    strings: List<FormatString>,
    valueOnlyHandle: Boolean,
  ) {
    val list = ArrayList<Pair<Location.Handle, String>>()
    for (formatString in strings) {
      if (formatString.type == FORMATTED) {
        val handle: Location.Handle =
          formatString.createResourceItemHandle(context, valueOnlyHandle)!!
        list.add(handle to formatString.value!!)
      } else if (formatString.type == NOT_FORMATTED) {
        val handle: Location.Handle =
          formatString.createResourceItemHandle(context, valueOnlyHandle)!!
        list.add(handle to name)
      }
    }

    // Check argument counts
    if (context.isEnabled(ARG_COUNT)) {
      checkArity(context, name, list)
    }

    // Check argument types (and also make sure that the formatting strings are valid)
    if (context.isEnabled(INVALID) || context.isEnabled(ARG_TYPES)) {
      checkTypes(context, context.isEnabled(INVALID), context.isEnabled(ARG_TYPES), name, list)
    }
  }

  /**
   * Returns true if the given expression in UAST is really a String inside a template expression.
   * This works around a bug in UAST, described in
   * https://issuetracker.google.com/217570491#comment2.
   */
  private fun isInStringExpression(call: UCallExpression, expression: UExpression): Boolean {
    val sourcePsi = expression.sourcePsi ?: return false
    val parent = sourcePsi.parent as? KtStringTemplateEntry ?: return false
    val stringElement = parent.parent
    val callPsi = call.sourcePsi
    if (callPsi is KtCallExpression) {
      for (argument in callPsi.valueArguments) {
        val valueExpression = argument.getArgumentExpression()
        if (valueExpression === stringElement) {
          return true
        }
      }
    }
    return false
  }

  private fun getStringType(context: JavaContext, expression: UExpression): PsiType? {
    val element = expression.sourcePsi ?: return null
    val facade = JavaPsiFacade.getInstance(element.project)
    val javaLangClass = facade.findClass(CommonClassNames.JAVA_LANG_STRING, element.resolveScope)
    return if (javaLangClass != null) {
      context.evaluator.getClassType(javaLangClass)
    } else {
      null
    }
  }

  private fun checkTrivialString(
    context: JavaContext,
    calledMethod: PsiMethod,
    call: UCallExpression,
    args: List<UExpression>,
    stringIndex: Int,
  ) {
    val s = ConstantEvaluator.evaluateString(context, args[stringIndex], false) ?: return
    var uppercase = false
    val count = getFormatArgumentCount(s, null)
    for (i in 1..count) {
      val argumentIndex = i + stringIndex
      if (argumentIndex >= args.size) {
        return
      }
      val type = args[argumentIndex].getExpressionType()
      if (type != null) {
        val formatType = getFormatArgumentType(s, i) ?: return
        val last = formatType[formatType.length - 1]
        if (formatType.length >= 2 && formatType[formatType.length - 2].lowercaseChar() == 't') {
          return
        }
        when (last) {
          'x',
          'X',
          'd',
          'o',
          'e',
          'E',
          'f',
          'g',
          'G',
          'a',
          'A',
          'h',
          'H' -> return
          'b',
          'B' -> // '+' concatenation of Booleans does not exist in Kotlin,
            // so "%b" should not be flagged as a trivial conversion.
            if (isKotlin(calledMethod.language)) {
              return
            }
        }

        // Strings with formatting arguments that contain modifiers (precision,
        // justification, etc.) will not be flagged as trivial.
        if (hasFormatArgumentModifiers(s, i)) {
          return
        }

        // If uppercase formatting is used but all format types are trivial,
        // String.toUpperCase() will be suggested.
        if (Character.isUpperCase(last)) {
          uppercase = true
        }
      }
    }

    // Creates the lint check message based on the conversions in the format string.
    var message =
      ("This formatting string is trivial. Rather than using " +
        "`String.format` to create your String, it will be more " +
        "performant to concatenate your arguments with `+`. ")
    if (uppercase) {
      message += "If uppercase formatting is necessary, use `String.toUpperCase()`."
    }
    context.report(TRIVIAL, call, context.getLocation(args[stringIndex]), message)
  }

  private fun checkArityAndTypes(
    context: XmlContext,
    element: Element,
    formatType: StringFormatType,
  ) {
    if (formatType == FORMATTED || formatType == NOT_FORMATTED) {
      val name = element.getAttribute(SdkConstants.ATTR_NAME)
      val list = ArrayList<Pair<Location.Handle, String>>()

      val defaultFormat = mFormatStrings[name]
      if (defaultFormat != null) {
        list.add(defaultFormat)
      }

      val defaultNotFormat = mNotFormatStrings[name]
      if (defaultNotFormat != null) {
        list.add(defaultNotFormat to name)
      }

      if (formatType == FORMATTED) {
        list.add(createLocationHandleForXmlDomElement(context, element) to element.textContent)
      }
      if (formatType == NOT_FORMATTED) {
        list.add(createLocationHandleForXmlDomElement(context, element) to name)
      }

      // Ensure that all the format strings are consistent with respect to each other;
      // e.g. they all have the same number of arguments, they all use all the
      // arguments, and they all use the same types for all the numbered arguments
      if (context.isEnabled(ARG_COUNT)) {
        checkArity(context, name, list)
      }

      // Check argument types (and also make sure that the formatting strings are valid)
      if (context.isEnabled(INVALID) || context.isEnabled(ARG_TYPES)) {
        checkTypes(context, context.isEnabled(INVALID), context.isEnabled(ARG_TYPES), name, list)
      }
    }
  }

  enum class StringFormatType {
    FORMATTED,
    NOT_FORMATTED,
    IGNORE,
  }

  data class FormatString(
    val name: String,
    val type: StringFormatType,
    val resourceItem: ResourceItem? = null,
    val value: String? = null,
  ) {

    fun createResourceItemHandle(context: Context, valueOnly: Boolean): ResourceItemHandle? {
      return if (resourceItem != null) {
        context.client.createResourceItemHandle(
          resourceItem,
          nameOnly = false,
          valueOnly = valueOnly,
        )
      } else {
        null
      }
    }
  }

  companion object {
    private val IMPLEMENTATION_XML =
      Implementation(StringFormatDetector::class.java, Scope.ALL_RESOURCES_SCOPE)
    private val IMPLEMENTATION_XML_AND_JAVA =
      Implementation(
        StringFormatDetector::class.java,
        EnumSet.of(Scope.ALL_RESOURCE_FILES, Scope.JAVA_FILE),
        Scope.JAVA_FILE_SCOPE,
      )

    /** Whether formatting strings are invalid */
    @JvmField
    val INVALID =
      create(
        "StringFormatInvalid",
        "Invalid format string",
        """
          If a string contains a '%' character, then the string may be a formatting string which will be passed to `String.format` from Java code to replace each '%' occurrence with specific values.

          This lint warning checks for two related problems:
          (1) Formatting strings that are invalid, meaning that `String.format` will throw exceptions at runtime when attempting to use the format string.
          (2) Strings containing '%' that are not formatting strings getting passed to a `String.format` call. In this case the '%' will need to be escaped as '%%'.

          NOTE: Not all Strings which look like formatting strings are intended for use by `String.format`; for example, they may contain date formats intended for `android.text.format.Time#format()`. Lint cannot always figure out that a String is a date format, so you may get false warnings in those scenarios. See the suppress help topic for information on how to suppress errors in that case.
          """,
        Category.MESSAGES,
        9,
        Severity.ERROR,
        IMPLEMENTATION_XML,
      )

    /** Whether formatting argument types are consistent across translations */
    @JvmField
    val ARG_COUNT =
      create(
        "StringFormatCount",
        "Formatting argument types incomplete or inconsistent",
        """
          When a formatted string takes arguments, it usually needs to reference the same arguments in all translations (or all arguments if there are no translations.

          There are cases where this is not the case, so this issue is a warning rather than an error by default. However, this usually happens when a language is not translated or updated correctly.
          """,
        Category.MESSAGES,
        5,
        Severity.WARNING,
        IMPLEMENTATION_XML,
      )

    /** Whether the string format used in a String.format call is trivial */
    val TRIVIAL =
      create(
          "StringFormatTrivial",
          "`String.format` string only contains trivial conversions",
          "Every call to `String.format` creates a new `Formatter` instance, which will " +
            "decrease the performance of your app. `String.format` should only be used when " +
            "necessary--if the formatted string contains only trivial conversions " +
            "(e.g. `b`, `s`, `c`) and there are no translation concerns, it will be " +
            "more efficient to replace them and concatenate with `+`.",
          Category.PERFORMANCE,
          5,
          Severity.WARNING,
          IMPLEMENTATION_XML_AND_JAVA,
        )
        .setAndroidSpecific(true)
        .setEnabledByDefault(false)

    /** Whether the string format supplied in a call to String.format matches the format string */
    @JvmField
    val ARG_TYPES =
      create(
        "StringFormatMatches",
        "`String.format` string doesn't match the XML format string",
        """
          This lint check ensures the following:
          (1) If there are multiple translations of the format string, then all translations use the same type for the same numbered arguments
          (2) The usage of the format string in Java is consistent with the format string, meaning that the parameter types passed to String.format matches those in the format string.
          """,
        Category.MESSAGES,
        9,
        Severity.ERROR,
        IMPLEMENTATION_XML_AND_JAVA,
      )

    /** This plural does not use the quantity value */
    @JvmField
    val POTENTIAL_PLURAL =
      create(
          "PluralsCandidate",
          "Potential Plurals",
          """This lint check looks for potential errors in internationalization where you have translated a message which involves a quantity and it looks like other parts of the string may need grammatical changes.

For example, rather than something like this:
```xml
  <string name="try_again">Try again in %d seconds.</string>
```
you should be using a plural:
```xml
   <plurals name="try_again">
        <item quantity="one">Try again in %d second</item>
        <item quantity="other">Try again in %d seconds</item>
    </plurals>
```
This will ensure that in other languages the right set of translations are provided for the different quantity classes.

(This check depends on some heuristics, so it may not accurately determine whether a string really should be a quantity. You can use tools:ignore to filter out false positives.""",
          Category.MESSAGES,
          5,
          Severity.WARNING,
          IMPLEMENTATION_XML,
        )
        .addMoreInfo(
          "https://developer.android.com/guide/topics/resources/string-resource.html#Plurals"
        )

    private const val STRING_RESOURCE_METHOD = "stringResource"

    /**
     * Removes all the unescaped quotes. See
     * [Escaping apostrophes and quotes](http://developer.android.com/guide/topics/resources/string-resource.html#FormattingAndStyling)
     */
    @JvmStatic
    fun stripQuotes(s: String): String {
      val sb = StringBuilder()
      var isEscaped = false
      var isQuotedBlock = false
      s.forEach { current ->
        if (isEscaped) {
          sb.append(current)
          isEscaped = false
        } else {
          isEscaped = current == '\\' // Next char will be escaped, so we will just copy it
          when (current) {
            '"' -> isQuotedBlock = !isQuotedBlock
            // We only add single quotes when they are within a quoted block
            '\'' -> if (isQuotedBlock) sb.append(current)
            else -> sb.append(current)
          }
        }
      }
      return sb.toString()
    }

    private fun isReference(text: String): Boolean =
      text.find { !it.isWhitespace() }?.let { it == '@' || it == '?' } ?: false

    /**
     * Detect StringFormatType and checks PotentialPlural when necessary.
     *
     * TODO extract checkPotentialPlural call to make this method side-effect free, only detecting
     * formatting type
     */
    private fun checkTextNode(
      context: XmlContext,
      element: Element,
      text: String,
    ): StringFormatType {
      var found = false
      var foundPlural = false

      // Look at the String and see if it's a format string (contains
      // positional %'s)
      var j = 0
      val m = text.length
      while (j < m) {
        val c = text[j]
        if (c == '\\') {
          j++
        }
        if (c == '%') {
          // Also make sure this String isn't an unformatted String
          val formatted = element.getAttribute("formatted")
          if (formatted.isNotEmpty() && !java.lang.Boolean.parseBoolean(formatted)) {
            return NOT_FORMATTED
          }

          // See if it's not a format string, e.g. "Battery charge is 100%!".
          // If so we want to record this name in a special list such that we can
          // make sure you don't attempt to reference this string from a String.format
          // call.
          val matcher = FORMAT.matcher(text)
          if (!matcher.find(j)) {
            return NOT_FORMATTED
          }
          val conversion = matcher.group(6)
          val conversionClass = getConversionClass(conversion[0])
          if (conversionClass == CONVERSION_CLASS_UNKNOWN || matcher.group(5) != null) {
            // Don't process any other strings here; some of them could
            // accidentally look like a string, e.g. "%H" is a hash code conversion
            // in String.format (and hour in Time formatting).
            return IGNORE
          }
          if (conversionClass == CONVERSION_CLASS_INTEGER && !foundPlural) {
            // See if there appears to be further text content here.
            // Look for whitespace followed by a letter, with no punctuation in between
            for (k in matcher.end() until m) {
              val nc = text[k]
              if (!Character.isWhitespace(nc)) {
                if (Character.isLetter(nc)) {
                  foundPlural = checkPotentialPlural(context, element, text, k)
                }
                break
              }
            }
          }
          found = true
          j++ // Ensure that when we process a "%%" we don't separately check the second %
        }
        j++
      }
      return if (found) {
        // Record it for analysis when seen in Java code
        FORMATTED
      } else {
        if (!isReference(text)) {
          NOT_FORMATTED
        } else {
          IGNORE
        }
      }
    }

    /**
     * Checks whether the text begins with a non-unit word, pointing to a string that should
     * probably be a plural instead. This
     */
    private fun checkPotentialPlural(
      context: XmlContext,
      element: Element,
      text: String,
      wordBegin: Int,
    ): Boolean {
      // This method should only be called if the text is known to start with a word
      assert(Character.isLetter(text[wordBegin]))
      var wordEnd = wordBegin
      while (wordEnd < text.length) {
        if (!Character.isLetter(text[wordEnd])) {
          break
        }
        wordEnd++
      }

      // Eliminate units, since those are not sentences you need to use plurals for, e.g.
      //   "Elevation gain: %1$d m (%2$d ft)"
      // We'll determine whether something is a unit by looking for
      // (1) Multiple uppercase characters (e.g. KB, or MiB), or better yet, uppercase characters
      //     anywhere but as the first letter
      // (2) No vowels (e.g. ft)
      // (3) Adjacent consonants (e.g. ft); this one can eliminate some legitimate
      //     English words as well (e.g. "the") so we should really limit this to
      //     letter pairs that are not common in English. This is probably overkill
      //     so not handled yet. Instead we use a simpler heuristic:
      // (4) Very short "words" (1-2 letters)
      if (wordEnd - wordBegin <= 2) {
        // Very short word (1-2 chars): possible unit, e.g. "m", "ft", "kb", etc
        return false
      }
      var hasVowel = false
      for (i in wordBegin until wordEnd) {
        // Uppercase character anywhere but first character: probably a unit (e.g. KB)
        val c = text[i]
        if (i > wordBegin && Character.isUpperCase(c)) {
          return false
        }
        if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u' || c == 'y') {
          hasVowel = true
        }
      }
      if (!hasVowel) {
        // No vowels: likely unit
        return false
      }
      val word = text.substring(wordBegin, wordEnd)

      // Some other known abbreviations that we don't want to count:
      if (word == "min") {
        return false
      }

      // This heuristic only works in English!
      if (isEnglishResource(context, true)) {
        val message =
          String.format(
            "Formatting %%d followed by words (\"%1\$s\"): " +
              "This should probably be a plural rather than a string",
            word,
          )
        context.report(POTENTIAL_PLURAL, element, context.getLocation(element), message)
        // Avoid reporting multiple errors on the same string
        // (if it contains more than one %d)
        return true
      }
      return false
    }

    private fun isSuppressed(context: Context, issue: Issue, source: Any?): Boolean {
      return if (source is Node) {
        context.driver.isSuppressed(null, issue, source as Node?)
      } else false
    }

    private fun isSuppressed(context: Context, issue: Issue, location: Location): Boolean {
      val source = location.source
      return isSuppressed(context, issue, source)
    }

    private fun isSuppressed(context: Context, issue: Issue, handle: Location.Handle?): Boolean {
      val source = handle!!.clientData
      return isSuppressed(context, issue, source) || handle.isSuppressed(context.driver, issue)
    }

    private fun checkTypes(
      context: Context,
      checkValid: Boolean,
      checkTypes: Boolean,
      name: String,
      list: List<Pair<Location.Handle, String>>,
    ) {
      val types: MutableMap<Int, String> = HashMap()
      val typeDefinition: MutableMap<Int, Location.Handle> = HashMap()

      for ((handle, formatString) in list) {
        for ((number, matcher) in getFormatArgumentSequenceWithIndex(formatString)) {
          val str = formatString.substring(matcher.start(), matcher.end())
          if (checkValid) {
            // Make sure it's a valid format string
            if (str.length > 2 && str[str.length - 2] == ' ') {
              val last = str[str.length - 1]
              // If you forget to include the conversion character, e.g.
              //   "Weight=%1$ g" instead of "Weight=%1$d g", then
              // you're going to end up with a format string interpreted as
              // "%1$ g". This means that the space character is interpreted
              // as a flag character, but it can only be a flag character
              // when used in conjunction with the numeric conversion
              // formats (d, o, x, X). If that's not the case, make a
              // dedicated error message
              if (last != 'd' && last != 'o' && last != 'x' && last != 'X') {
                if (isSuppressed(context, INVALID, handle)) {
                  return
                }
                val location = handle.resolve()
                val message =
                  String.format(
                    "Incorrect formatting string `%1\$s`; missing conversion " +
                      "character in '`%2\$s`'?",
                    name,
                    str,
                  )
                context.report(INVALID, location, message)
                // warned = true;
                continue
              }
            }
          }
          if (!checkTypes) {
            continue
          }

          val format = matcher.group(6)
          val currentFormat = types[number]
          if (currentFormat == null) {
            types[number] = format
            typeDefinition[number] = handle
          } else if (currentFormat != format && isIncompatible(currentFormat[0], format[0])) {
            if (isSuppressed(context, ARG_TYPES, handle)) {
              return
            }
            var location = handle.resolve()
            if (isSuppressed(context, ARG_TYPES, location)) {
              return
            }

            // Attempt to limit the location range to just the formatting
            // string in question
            location =
              refineLocation(context, location, formatString, matcher.start(), matcher.end())
            val otherLocation = typeDefinition[number]!!.resolve()
            otherLocation.message = "Conflicting argument type (`$currentFormat') here"
            location.secondary = otherLocation
            val f = otherLocation.file
            val message =
              String.format(
                Locale.US,
                "Inconsistent formatting types for argument #%1\$d in " +
                  "format string `%2\$s` ('%3\$s'): Found both '`%4\$s`' here and '`%5\$s`' " +
                  "in %6\$s",
                number,
                name,
                str,
                format,
                currentFormat,
                getFileNameWithParent(context.client, f),
              )
            // warned = true;
            context.report(ARG_TYPES, location, message)
            break
          }
        }
      }
    }

    /**
     * Returns true if two String.format conversions are "incompatible" (meaning that using these
     * two for the same argument across different translations is more likely an error than
     * intentional). Some conversions are incompatible, e.g. "d" and "s" where one is a number and
     * string, whereas others may work (e.g. float versus integer) but are probably not intentional.
     */
    private fun isIncompatible(conversion1: Char, conversion2: Char): Boolean {
      val class1 = getConversionClass(conversion1)
      val class2 = getConversionClass(conversion2)
      return class1 != class2 &&
        class1 != CONVERSION_CLASS_UNKNOWN &&
        class2 != CONVERSION_CLASS_UNKNOWN
    }

    private const val CONVERSION_CLASS_UNKNOWN = 0
    private const val CONVERSION_CLASS_STRING = 1
    private const val CONVERSION_CLASS_CHARACTER = 2
    private const val CONVERSION_CLASS_INTEGER = 3
    private const val CONVERSION_CLASS_FLOAT = 4
    private const val CONVERSION_CLASS_BOOLEAN = 5
    private const val CONVERSION_CLASS_HASHCODE = 6
    private const val CONVERSION_CLASS_PERCENT = 7
    private const val CONVERSION_CLASS_NEWLINE = 8
    private const val CONVERSION_CLASS_DATETIME = 9

    private fun getConversionClass(conversion: Char): Int {
      // See http://developer.android.com/reference/java/util/Formatter.html
      when (conversion) {
        't',
        'T' -> return CONVERSION_CLASS_DATETIME
        's',
        'S' -> return CONVERSION_CLASS_STRING
        'c',
        'C' -> return CONVERSION_CLASS_CHARACTER
        'd',
        'o',
        'x',
        'X' -> return CONVERSION_CLASS_INTEGER
        'f',
        'e',
        'E',
        'g',
        'G',
        'a',
        'A' -> return CONVERSION_CLASS_FLOAT
        'b',
        'B' -> return CONVERSION_CLASS_BOOLEAN
        'h',
        'H' -> return CONVERSION_CLASS_HASHCODE
        '%' -> return CONVERSION_CLASS_PERCENT
        'n' -> return CONVERSION_CLASS_NEWLINE
      }
      return CONVERSION_CLASS_UNKNOWN
    }

    private fun refineLocation(
      context: Context,
      location: Location,
      formatString: String,
      substringStart: Int,
      substringEnd: Int,
    ): Location {
      val startLocation = location.start
      val endLocation = location.end
      if (startLocation != null && endLocation != null) {
        val startOffset = startLocation.offset
        val endOffset = endLocation.offset
        if (startOffset >= 0) {
          val contents = context.client.readFile(location.file)
          if (endOffset <= contents.length && startOffset < endOffset) {
            val formatOffset = CharSequences.indexOf(contents, formatString, startOffset)
            if (formatOffset != -1 && formatOffset <= endOffset) {
              return create(
                location.file,
                contents,
                formatOffset + substringStart,
                formatOffset + substringEnd,
              )
            }
          }
        }
      }
      return location
    }

    /**
     * Check that the number of arguments in the format string is consistent across translations,
     * and that all arguments are used
     */
    private fun checkArity(
      context: Context,
      name: String,
      list: List<Pair<Location.Handle, String>>?,
    ) {
      // Check to make sure that the argument counts and types are consistent
      var prevCount = -1
      for (pair in list!!) {
        val indices: MutableSet<Int> = HashSet()
        val count = getFormatArgumentCount(pair.second, indices)
        val handle = pair.first
        if (prevCount != -1 && prevCount != count) {
          if (isSuppressed(context, ARG_COUNT, handle)) {
            return
          }
          val location = handle.resolve()
          if (isSuppressed(context, ARG_COUNT, location)) {
            return
          }
          val secondary = list[0].first.resolve()
          if (isSuppressed(context, ARG_COUNT, secondary)) {
            return
          }
          secondary.message = "Conflicting number of arguments ($prevCount) here"
          location.secondary = secondary
          val path = getFileNameWithParent(context.client, secondary.file)
          val message =
            String.format(
              Locale.US,
              "Inconsistent number of arguments in formatting string `%1\$s`; " +
                "found both %2\$d here and %3\$d in %4\$s",
              name,
              count,
              prevCount,
              path,
            )
          context.report(ARG_COUNT, location, message)
          break
        }
        for (i in 1..count) {
          if (!indices.contains(i)) {
            if (isSuppressed(context, ARG_COUNT, handle)) {
              return
            }
            val all: MutableSet<Int> = HashSet()
            for (j in 1 until count) {
              all.add(j)
            }
            all.removeAll(indices)
            val sorted: List<Int> = ArrayList(all).sorted()
            val location = handle.resolve()
            val message =
              String.format(
                "Formatting string '`%1\$s`' is not referencing numbered arguments %2\$s",
                name,
                sorted,
              )
            context.report(ARG_COUNT, location, message)
            break
          }
        }
        prevCount = count
      }
    }

    // See java.util.Formatter docs
    @JvmField
    val FORMAT: Pattern =
      Pattern.compile( // Generic format:
        //   %[argument_index$][flags][width][.precision]conversion
        //
        "%" + // Argument Index
          "(\\d+\\$)?" + // Flags
          "([-+#, 0(<]*)?" + // Width
          "(\\d+)?" + // Precision
          "(\\.\\d+)?" + // Conversion. These are all a single character, except date/time
          // conversions
          // which take a prefix of t/T:
          "([tT])?" + // The current set of conversion characters are
          // b,h,s,c,d,o,x,e,f,g,a,t (as well as all those as upper-case
          // characters), plus
          // n for newlines and % as a literal %. And then there are all the
          // time/date
          // characters: HIKLm etc. Just match on all characters here since there
          // should
          // be at least one.
          "([a-zA-Z%])"
      )

    // Return a sequence of match results at different arguments.
    // The user of this sequence is not supposed to save references to the match results.
    private fun getFormatArgumentSequence(s: String): Sequence<MatchResult> = sequence {
      val matcher = FORMAT.matcher(s)
      var index = 0
      while (matcher.find(index)) {
        index =
          when {
            matcher.group(6).let { it == "%" || it == "n" } -> matcher.end()
            // Make sure this is not an escaped '%'. If we're in an escape, ignore this result
            0 <= matcher.start() - 1 && s[matcher.start() - 1] == '\\' -> matcher.start() + 1
            else -> matcher.end().also { yield(matcher) }
          }
      }
    }

    private fun getFormatArgumentSequenceWithIndex(s: String): Sequence<Pair<Int, MatchResult>> {
      var nextNumber = 1
      return getFormatArgumentSequence(s).map { matcher ->
        // Shouldn't throw a number format exception since we've already
        // matched the pattern in the regexp
        val number =
          when (val numberString = matcher.group(1)) {
            null -> nextNumber++
            // Strip off trailing $
            else ->
              numberString.substring(0, numberString.length - 1).toInt().also {
                nextNumber = it + 1
              }
          }
        number to matcher
      }
    }

    /** Given a format string returns the format type of the given argument */
    @JvmStatic
    @VisibleForTesting
    fun getFormatArgumentType(s: String, argument: Int): String? =
      getFormatArgumentSequenceWithIndex(s)
        .find { (number, _) -> number == argument }
        ?.let { (_, matcher) -> matcher.group(6) }

    /**
     * Given a format string returns the number of required arguments. If the `seenArguments`
     * parameter is not null, put the indices of any observed arguments into it.
     */
    @JvmStatic
    fun getFormatArgumentCount(s: String, seenArguments: MutableSet<Int>?): Int =
      getFormatArgumentSequenceWithIndex(s)
        .map { (number, _) -> number }
        .onEach { seenArguments?.add(it) }
        .maxOrNull() ?: 0

    /** Given a format string returns whether it has any flags/width/precision modifiers. */
    fun hasFormatArgumentModifiers(s: String, argument: Int): Boolean =
      getFormatArgumentSequenceWithIndex(s)
        .find { (number, _) -> number == argument }
        ?.let { (_, matcher) ->
          // The regex for matching flags uses '*', so a format argument with no flags
          // returns "".
          !matcher.group(2).isNullOrEmpty() ||
            !matcher.group(3).isNullOrEmpty() ||
            !matcher.group(4).isNullOrEmpty()
        } ?: false

    /**
     * Determines whether the given [String.format] formatting string is "locale dependent", meaning
     * that its output depends on the locale. This is the case if it for example references decimal
     * numbers of dates and times.
     *
     * @param format the format string
     * @return true if the format is locale sensitive, false otherwise
     */
    @JvmStatic
    fun isLocaleSpecific(format: String): Boolean =
      getFormatArgumentSequence(format).any { matcher ->
        when (matcher.group(6).firstOrNull()) {
          'd',
          'e',
          'E',
          'f',
          'g',
          'G',
          't',
          'T' -> true
          else -> false
        }
      }

    /**
     * Checks a String.format call that is using a string that doesn't contain format placeholders.
     *
     * @param context the context to report errors to
     * @param call the AST node for the [String.format]
     * @param name the string name
     * @param handle the string location
     */
    private fun checkNotFormattedHandle(
      context: JavaContext,
      call: UCallExpression,
      name: String,
      handle: Location.Handle?,
    ) {
      if (isSuppressed(context, INVALID, handle)) {
        return
      }
      val location = context.getLocation(call)
      val secondary = handle!!.resolve()
      secondary.message = "This definition does not require arguments"
      location.secondary = secondary
      val message =
        String.format(
          "Format string '`%1\$s`' is not a valid format string so it should not be " +
            "passed to `String.format`",
          name,
        )
      context.report(INVALID, call, location, message)
    }

    private class TypeTest(private val prims: List<PsiType>, private val tags: List<String>) :
      (PsiType) -> Boolean {
      override fun invoke(t: PsiType) =
        t in prims || t is PsiClassType && t.getCanonicalText() in tags

      infix fun or(that: TypeTest): TypeTest = TypeTest(prims + that.prims, tags + that.tags)
    }

    private val isCharacterType =
      TypeTest(listOf(PsiTypes.charType()), listOf(TYPE_CHARACTER_WRAPPER))
    private val isBooleanType =
      TypeTest(listOf(PsiTypes.booleanType()), listOf(TYPE_BOOLEAN_WRAPPER))
    private val isIntType =
      TypeTest(
        listOf(PsiTypes.intType(), PsiTypes.longType(), PsiTypes.byteType(), PsiTypes.shortType()),
        listOf(TYPE_INTEGER_WRAPPER, TYPE_LONG_WRAPPER, TYPE_BYTE_WRAPPER, TYPE_SHORT_WRAPPER),
      )
    private val isFloatType =
      TypeTest(
        listOf(PsiTypes.floatType(), PsiTypes.doubleType()),
        listOf(TYPE_FLOAT_WRAPPER, TYPE_DOUBLE_WRAPPER),
      )
    private val isNumericType = isIntType or isFloatType
    private val isNumericOrBigNumberType =
      isNumericType or TypeTest(listOf(), listOf("java.math.BigInteger", "java.math.BigDecimal"))
  }
}
