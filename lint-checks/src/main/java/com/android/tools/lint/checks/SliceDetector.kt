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

import com.android.SdkConstants
import com.android.SdkConstants.ANDROID_URI
import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.TAG_CATEGORY
import com.android.SdkConstants.TAG_INTENT_FILTER
import com.android.SdkConstants.TAG_PROVIDER
import com.android.tools.lint.client.api.TYPE_LONG
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintMap
import com.android.tools.lint.detector.api.Location
import com.android.tools.lint.detector.api.PartialResult
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils
import com.android.tools.lint.detector.api.getMethodName
import com.android.tools.lint.detector.api.resolveManifestName
import com.android.utils.XmlUtils.getFirstSubTagByName
import com.android.utils.XmlUtils.getNextTagByName
import com.android.utils.next
import com.android.utils.subtag
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiVariable
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UPostfixExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.util.isConstructorCall
import org.w3c.dom.Element

/** Helps construct slices correctly. */
class SliceDetector : Detector(), SourceCodeScanner {
  companion object {
    private val IMPLEMENTATION = Implementation(SliceDetector::class.java, Scope.JAVA_FILE_SCOPE)

    /** Problems with slice registration and construction. */
    @JvmField
    val ISSUE =
      Issue.create(
        id = "Slices",
        briefDescription = "Slices",
        explanation =
          """
            This check analyzes usages of the Slices API and offers suggestions based \
            on best practices.
            """,
        category = Category.CORRECTNESS,
        priority = 6,
        severity = Severity.WARNING,
        androidSpecific = true,
        implementation = IMPLEMENTATION,
      )

    private const val ICON_CLASS = "android.graphics.drawable.Icon"
    private const val ICON_COMPAT_CLASS_1 = "androidx.core.graphics.drawable.IconCompat"
    private const val ICON_COMPAT_CLASS_2 = "android.support.v4.graphics.drawable.IconCompat"

    private const val SLICE_PROVIDER_CLASS_1 = "androidx.slice.SliceProvider"
    private const val SLICE_PROVIDER_CLASS_2 = "android.app.slice.SliceProvider"
    private const val SLICE_ACTION_CLASS = "androidx.slice.builders.SliceAction"
    private const val LIST_BUILDER_CLASS = "androidx.slice.builders.ListBuilder"
    private const val LIST_INPUT_RANGE_BUILDER_CLASS =
      "androidx.slice.builders.ListBuilder.InputRangeBuilder"
    private const val LIST_RANGE_BUILDER_CLASS = "androidx.slice.builders.ListBuilder.RangeBuilder"
    private const val LIST_HEADER_BUILDER_CLASS =
      "androidx.slice.builders.ListBuilder.HeaderBuilder"
    private const val GRID_ROW_BUILDER_CLASS = "androidx.slice.builders.GridRowBuilder"
    private const val GRID_ROW_CELL_BUILDER_CLASS =
      "androidx.slice.builders.GridRowBuilder.CellBuilder"
    private const val ROW_BUILDER_CLASS = "androidx.slice.builders.ListBuilder.RowBuilder"

    private const val CATEGORY_SLICE = "android.app.slice.category.SLICE"
    const val WARN_ABOUT_TOO_MANY_ROWS = false

    private const val KEY_DECLARATION = "declaration"
    private const val KEY_MAP_METHOD = "onMapMethod"

    /** Checks whether a provider is a slice provider. */
    fun isSliceProvider(provider: Element): Boolean {
      var intentFilter = getFirstSubTagByName(provider, TAG_INTENT_FILTER)
      while (intentFilter != null) {
        var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
        while (category != null) {
          val name = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
          if (CATEGORY_SLICE == name) {
            return true
          }
          category = getNextTagByName(category, TAG_CATEGORY)
        }
        intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
      }

      return false
    }
  }

  override fun applicableSuperClasses(): List<String> {
    return listOf(SLICE_PROVIDER_CLASS_1, SLICE_PROVIDER_CLASS_2)
  }

  override fun visitClass(context: JavaContext, declaration: UClass) {
    val sliceProvider = declaration.qualifiedName ?: return

    val onMapMethod =
      declaration.methods.firstOrNull {
        it.name == "onMapIntentToUri" && it.uastParameters.size == 1
      }

    if (onMapMethod != null) {
      if (context.driver.isSuppressed(context, ISSUE, onMapMethod as UAnnotated)) {
        return
      }
    } else if (context.driver.isSuppressed(context, ISSUE, declaration as UAnnotated)) {
      return
    }

    val onMapMethodLocation = onMapMethod?.let { context.getNameLocation(it) }
    val declarationLocation = context.getNameLocation(declaration)

    if (context.isGlobalAnalysis()) {
      checkManifest(context, sliceProvider, onMapMethodLocation, declarationLocation)
    } else {
      val partial = context.getPartialResults(ISSUE)
      if (onMapMethodLocation != null) {
        val map = LintMap()
        map.put(KEY_DECLARATION, declarationLocation)
        map.put(KEY_MAP_METHOD, onMapMethodLocation)
        partial.map().put(sliceProvider, map)
      } else {
        partial.map().put(sliceProvider, declarationLocation)
      }
    }
  }

  override fun checkPartialResults(context: Context, partialResults: PartialResult) {
    for ((_, map) in partialResults) {
      for (sliceProvider in map) {
        assert(sliceProvider.contains('.'))
        var onMapMethodLocation: Location?
        var declarationLocation = map.getLocation(sliceProvider)
        if (declarationLocation != null) {
          onMapMethodLocation = null
        } else {
          val providerMap = map.getMap(sliceProvider) ?: continue
          declarationLocation = providerMap.getLocation(KEY_DECLARATION) ?: continue
          onMapMethodLocation = providerMap.getLocation(KEY_MAP_METHOD)
        }
        checkManifest(context, sliceProvider, onMapMethodLocation, declarationLocation)
      }
    }
  }

  private fun checkManifest(
    context: Context,
    sliceProvider: String,
    onMapMethodLocation: Location?,
    declarationLocation: Location,
  ) {
    // Make sure slice provider is registered correctly in the manifest
    // Make sure this actions resource is registered in the manifest
    if (context.mainProject.isLibrary) {
      return
    }

    val mainProject = context.mainProject
    val mergedManifest = mainProject.mergedManifest ?: return
    val root = mergedManifest.documentElement ?: return
    val application = root.subtag(SdkConstants.TAG_APPLICATION) ?: return

    var provider = application.subtag(TAG_PROVIDER)
    while (provider != null) {
      val manifestName = resolveManifestName(provider, mainProject)
      if (sliceProvider == manifestName) {
        break
      }
      provider = provider.next(TAG_PROVIDER)
    }

    if (provider == null) {
      // Here we should report
      //    "This `SliceProvider` should be registered in the manifest"
      // but that's already done by an existing lint check, no need to
      // double up the reports
      return
    }

    var firstCategory: Element? = null
    var intentFilter = getFirstSubTagByName(provider, TAG_INTENT_FILTER)
    while (intentFilter != null) {
      var foundCategory = false
      var category = getFirstSubTagByName(intentFilter, TAG_CATEGORY)
      while (category != null) {
        val name = category.getAttributeNS(ANDROID_URI, ATTR_NAME)
        if (CATEGORY_SLICE == name) {
          foundCategory = true
          if (firstCategory == null) {
            firstCategory = category
          }
          break
        }
        category = getNextTagByName(category, TAG_CATEGORY)
      }

      if (!foundCategory) {
        val location =
          context.client
            .findManifestSourceLocation(intentFilter)
            ?.withSecondary(declarationLocation, "SliceProvider declaration")
        if (location != null) {
          context.report(
            ISSUE,
            location,
            "All `SliceProvider` filters require category slice to be set: " +
              " <category android:name=\"android.app.slice.category.SLICE\" />",
            null,
          )
        }
      }

      intentFilter = getNextTagByName(intentFilter, TAG_INTENT_FILTER)
    }

    // declares intent: firstCategory != null
    // requires intent: onMapMethod != null

    if (onMapMethodLocation != null && firstCategory == null) {
      context.report(
        ISSUE,
        onMapMethodLocation,
        "Define intent filters in your manifest on your " +
          "`<provider android:name=\"$sliceProvider\">`; otherwise " +
          "`onMapIntentToUri` will not be called",
      )
    } else if (firstCategory != null && onMapMethodLocation == null) {
      context.client.findManifestSourceLocation(firstCategory)?.let {
        declarationLocation.secondary = it
      }
      context.report(
        ISSUE,
        declarationLocation,
        "Implement `SliceProvider#onMapIntentToUri` to handle the intents " +
          "defined on your slice `<provider>` in your manifest",
      )
    }
  }

  override fun getApplicableConstructorTypes(): List<String> {
    return listOf(
      LIST_BUILDER_CLASS,
      ROW_BUILDER_CLASS,
      GRID_ROW_BUILDER_CLASS,
      GRID_ROW_CELL_BUILDER_CLASS,
      LIST_HEADER_BUILDER_CLASS,
      LIST_INPUT_RANGE_BUILDER_CLASS,
      LIST_RANGE_BUILDER_CLASS,
    )
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val method = node.getParentOfType(UMethod::class.java, true) ?: return
    val name = constructor.containingClass?.qualifiedName ?: return
    when (name) {
      LIST_BUILDER_CLASS -> checkListBuilder(context, node, method)
      ROW_BUILDER_CLASS -> checkRowBuilder(context, node, method)
      // GRID_ROW_BUILDER_CLASS -> checkGridRowBuilder(context, node, method)
      GRID_ROW_CELL_BUILDER_CLASS,
      LIST_HEADER_BUILDER_CLASS -> checkHasContent(name, context, node, method)
    }
  }

  private fun checkListBuilder(
    context: JavaContext,
    listBuilder: UCallExpression,
    method: UMethod,
  ) {
    val rows = findRows(listBuilder, method)
    if (rows.isEmpty()) {
      context.report(
        ISSUE,
        listBuilder,
        context.getLocation(listBuilder),
        "A slice should have at least one row added to it",
      )
      return
    }

    // If you're calling say ListBuilder.addGridRow(Consumer<Builder>) that
    // method is going to call a lambda with a single parameter which points
    // to the new Builder; we want to track those as references to our
    // target row.
    val initialReferences = mutableListOf<PsiVariable>()
    for (call in rows) {
      if (!call.isConstructorCall() && isBuildConsumer(call)) {
        val lambda = call.valueArguments[0].skipParenthesizedExprDown()
        if (lambda is ULambdaExpression) {
          val parameters = lambda.valueParameters
          val parameter = parameters[0].sourcePsi as? PsiParameter ?: continue
          initialReferences.add(parameter)
        }
      }
    }

    var primaryAction = false
    method.accept(
      object : DataFlowAnalyzer(rows, initialReferences) {
        override fun receiver(call: UCallExpression) {
          if (call.methodName == "setPrimaryAction") {
            primaryAction = true
          }
        }
      }
    )
    if (!primaryAction) {
      context.report(
        ISSUE,
        listBuilder,
        context.getLocation(listBuilder),
        "A slice should have a primary action set on one of its rows",
      )
      return
    }

    // Make sure that at least one of these defines at primary action
    var rowCount = 0
    val endActionItems = mutableListOf<UExpression>()
    method.accept(
      object : DataFlowAnalyzer(listOf(listBuilder)) {
        override fun receiver(call: UCallExpression) {
          val methodName = call.methodName
          if (call.methodName == "setPrimaryAction") {
            primaryAction = true
          } else if (methodName == "addAction") {
            val arguments = call.valueArguments
            if (arguments.isEmpty()) {
              return
            }
            val first = arguments[0].skipParenthesizedExprDown()
            val type = first?.getExpressionType()?.canonicalText
            if (type == SLICE_ACTION_CLASS) {
              endActionItems.add(first)
            }
          } else if (WARN_ABOUT_TOO_MANY_ROWS && (isAddRowMethod(methodName))) {
            rowCount++
            if (rowCount == 5) {
              context.report(
                ISSUE,
                listBuilder,
                context.getLocation(call),
                "Consider setting a see more action if more than 4 rows " +
                  "added to `ListBuilder`. Depending on where the slice is " +
                  "being displayed, all rows of content may not be visible, " +
                  "consider adding an intent to an activity with the rest " +
                  "of the content.",
              )
            }
          }
        }
      }
    )

    ensureSingleToggleType(
      endActionItems,
      context,
      "A mixture of slice actions and icons are not supported on a list, " +
        "add either actions or icons but not both",
    )
  }

  private fun isAddRowMethod(methodName: String?): Boolean {
    return methodName == "addRow" ||
      methodName == "addInputRange" ||
      methodName == "addRange" ||
      methodName == "addGridRow"
  }

  private fun ensureSingleToggleType(
    endActionItems: MutableList<UExpression>,
    context: JavaContext,
    message: String,
  ) {
    if (endActionItems.size >= 2) {
      var custom: UExpression? = null
      var default: UExpression? = null
      for (action in endActionItems) {
        val constructorCall = findSliceActionConstructor(action) ?: continue
        val constructorMethod = constructorCall.resolve() ?: continue
        if (isCustomToggle(constructorMethod)) {
          custom = action
        } else {
          default = action
        }

        if (custom != null && default != null) {
          val location =
            context
              .getLocation(custom)
              .withSecondary(context.getLocation(default), "Conflicting action type here")
          context.report(ISSUE, custom, location, message)
          break
        }
      }
    }
  }

  /**
   * Given a list builder construction, returns all the row builder constructor calls initialized
   * with that list builder.
   */
  private fun findRows(node: UCallExpression, method: UMethod): List<UCallExpression> {
    val rows = mutableListOf<UCallExpression>()
    method.accept(
      object : DataFlowAnalyzer(listOf(node)) {
        override fun argument(call: UCallExpression, reference: UElement) {
          if (call.isConstructorCall()) {
            val qualifiedName = call.resolve()?.containingClass?.qualifiedName ?: return
            when (qualifiedName) {
              LIST_INPUT_RANGE_BUILDER_CLASS,
              LIST_RANGE_BUILDER_CLASS,
              LIST_HEADER_BUILDER_CLASS,
              GRID_ROW_BUILDER_CLASS,
              GRID_ROW_CELL_BUILDER_CLASS, // Not sure about this one
              ROW_BUILDER_CLASS -> {
                rows.add(call)
              }
            }
          }
        }

        override fun receiver(call: UCallExpression) {
          if (isBuildConsumer(call)) {
            rows.add(call)
          }

          if (isAddRowMethod(getMethodName(call))) {
            call.valueArguments.firstOrNull()?.let {
              val arg = it.skipParenthesizedExprDown()
              if (arg is UCallExpression) {
                argument(arg, arg)
              } else if (arg is UQualifiedReferenceExpression) {
                var curr: UElement = arg
                while (true) {
                  if (curr is UQualifiedReferenceExpression) {
                    val selector = curr.selector
                    if (selector.isConstructorCall()) {
                      argument(selector as UCallExpression, selector)
                      break
                    }
                    curr = curr.receiver.skipParenthesizedExprDown() ?: break
                  } else if (curr is UPostfixExpression && curr.operator.text == "!!") {
                    curr = curr.operand.skipParenthesizedExprDown() ?: break
                  } else if (curr is UCallExpression) {
                    argument(curr, curr)
                    break
                  } else {
                    break
                  }
                }
              }
            }
          }
        }
      }
    )

    return rows
  }

  private fun isBuildConsumer(call: UCallExpression): Boolean {
    // A handful of methods actually initialize new row builders inside and then
    // invoke a lambda on the new builder. We recognize these as methods that take
    // a single Consumer parameter where the generic type is a Builder.
    // Some examples:
    //    main/java/androidx/slice/builders/ListBuilder.java
    //        public ListBuilder addRow(@NonNull Consumer<RowBuilder> c) { }
    //        public ListBuilder addGrid(@NonNull Consumer<GridBuilder> c) { }
    //        public ListBuilder addGridRow(@NonNull Consumer<GridRowBuilder> c) { }
    //        ...
    if (call.valueArgumentCount != 1) {
      return false
    }

    val calledMethod = call.resolve() ?: return false
    val arg = calledMethod.parameterList.parameters.firstOrNull() ?: return false
    val type = arg.type.canonicalText
    if (type.startsWith("androidx.core.util.Consumer<") && type.endsWith("Builder>")) {
      return true
    }

    return false
  }

  private fun isContentMethod(methodName: String): Boolean {
    // Methods on RowBuilder, HeaderBuilder, CellBuilder etc that add content items
    return when (methodName) {
      "setTitle",
      "setPrimaryAction",
      "addText",
      "addTitleText",
      "setSubtitle",
      "addEndItem",
      "setTitleItem",
      "addImage",
      "setSummarySubtitle",
      "setSummary" -> true
      else -> false
    }
  }

  private fun checkHasContent(
    qualifiedName: String,
    context: JavaContext,
    node: UCallExpression,
    method: UMethod,
  ) {
    val analyzer =
      object : TargetMethodDataFlowAnalyzer(listOf(node)) {
        override fun isTargetMethodName(name: String): Boolean {
          return isContentMethod(name)
        }

        // Only filtering by name
        override fun isTargetMethod(name: String, method: PsiMethod?): Boolean = true
      }

    if (method.isMissingTarget(analyzer, allowEscape = true)) {
      val name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
      warnMissingContent(name, context, node)
    }
  }

  private fun checkRowBuilder(context: JavaContext, node: UCallExpression, method: UMethod) {
    var timestamp: UCallExpression? = null
    var endActionItem: UCallExpression? = null
    val endActionItems = mutableListOf<UExpression>()
    var endIconItem: UCallExpression? = null
    var hasContent = false
    method.accept(
      object : DataFlowAnalyzer(listOf(node)) {
        override fun receiver(call: UCallExpression) {
          val methodName = call.methodName ?: return
          if ((methodName == "addEndItem" || methodName == "setTitleItem")) {
            hasContent = true
            val arguments = call.valueArguments
            if (arguments.isEmpty()) {
              return
            }
            val first = arguments[0].skipParenthesizedExprDown()
            val type = first?.getExpressionType()?.canonicalText
            if (arguments.size == 1 && type == TYPE_LONG) {
              if (timestamp != null) {
                val location =
                  context
                    .getLocation(call)
                    .withSecondary(context.getLocation(timestamp!!), "Earlier timestamp here")
                context.report(
                  ISSUE,
                  call,
                  location,
                  "`RowBuilder` can only have one timestamp added to it, " +
                    "remove one of your timestamps",
                )
              } else {
                timestamp = call
              }
            } else if (type == SLICE_ACTION_CLASS) {
              if (endIconItem != null) {
                val location =
                  context
                    .getLocation(call)
                    .withSecondary(context.getLocation(endIconItem!!), "Earlier icon here")
                context.report(
                  ISSUE,
                  call,
                  location,
                  "`RowBuilder` cannot have a mixture of icons and slice " +
                    "actions added to the end items",
                )
              }
              endActionItem = call
              endActionItems.add(first)
            } else if (
              type == ICON_CLASS || type == ICON_COMPAT_CLASS_1 || type == ICON_COMPAT_CLASS_2
            ) {
              if (endActionItem != null) {
                val location =
                  context
                    .getLocation(call)
                    .withSecondary(
                      context.getLocation(endActionItem!!),
                      "Earlier slice action here",
                    )
                context.report(
                  ISSUE,
                  call,
                  location,
                  "`RowBuilder` cannot have a mixture of icons and slice " +
                    "actions added to the end items",
                )
              }
              endIconItem = call
            }
          } else if (isContentMethod(methodName)) {
            hasContent = true
          }
        }
      }
    )

    val message = "`RowBuilder` should not have a mixture of default and custom toggles"
    ensureSingleToggleType(endActionItems, context, message)

    if (!hasContent) {
      warnMissingContent("RowBuilder", context, node)
    }
  }

  private fun warnMissingContent(builder: String, context: JavaContext, node: UCallExpression) {
    context.report(
      ISSUE,
      node,
      context.getLocation(node),
      "`$builder` should have a piece of content added to it",
    )
  }

  private fun isCustomToggle(method: PsiMethod): Boolean {
    return method.parameterList.parametersCount == 3
  }

  private fun findSliceActionConstructor(node: UElement): UCallExpression? {
    when {
      node is UReferenceExpression -> {
        if (node is UQualifiedReferenceExpression && node.selector is UCallExpression) {
          return findSliceActionConstructor(node.selector)
        }
        val resolved = node.resolve() ?: return null
        if (resolved is ULocalVariable) {
          val initializer = resolved.uastInitializer?.skipParenthesizedExprDown() ?: return null
          return findSliceActionConstructor(initializer)
        } else if (resolved is PsiLocalVariable) {
          val initializer =
            UastLintUtils.findLastAssignment(resolved, node)?.skipParenthesizedExprDown()
              ?: return null
          return findSliceActionConstructor(initializer)
        }
      }
      node is UCallExpression && node.isConstructorCall() -> {
        val name = node.resolve()?.containingClass?.qualifiedName
        if (name == SLICE_ACTION_CLASS) {
          return node
        }
      }
      node is UQualifiedReferenceExpression -> return findSliceActionConstructor(node.selector)
      node is UParenthesizedExpression -> return findSliceActionConstructor(node.expression)
    }
    return null
  }
}
