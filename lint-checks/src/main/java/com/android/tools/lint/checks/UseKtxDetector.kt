/*
 * Copyright (C) 2025 The Android Open Source Project
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
import com.android.tools.lint.detector.api.XmlScanner
import com.android.tools.lint.detector.api.isBelow
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.isReturningLambdaResult
import com.android.tools.lint.detector.api.isScopingFunction
import com.android.tools.lint.detector.api.nextStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UBlockExpression
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UIfExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.ULocalVariable
import org.jetbrains.uast.ULoopExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReturnExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.USwitchExpression
import org.jetbrains.uast.UThisExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.skipParenthesizedExprDown
import org.jetbrains.uast.skipParenthesizedExprUp
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.tryResolve
import org.jetbrains.uast.visitor.AbstractUastVisitor

/** Suggests replacements in Kotlin code for KTX constructs. */
class UseKtxDetector : Detector(), SourceCodeScanner, XmlScanner {
  override fun getApplicableUastTypes(): List<Class<out UElement>> =
    listOf(UCallExpression::class.java)

  override fun createUastHandler(context: JavaContext): UElementHandler? {
    val sourcePsi = context.uastFile?.sourcePsi ?: return null
    if (!isKotlin(sourcePsi.language)) {
      return null
    }
    return object : UElementHandler() {
      override fun visitCallExpression(node: UCallExpression) {
        val method = node.resolve() ?: return
        val name = method.name
        checkBlockExtensions(context, node, method, name)
        checkMethodExtensions(context, node, method, name)
      }
    }
  }

  private fun checkBlockExtensions(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    name: String,
  ) {
    when (name) {
      "obtainStyledAttributes" -> {
        checkBlock(
          USE_KTX,
          context,
          node,
          method,
          name,
          "android.content.Context",
          "android.content.res.TypedArray",
          "Context",
          "withStyledAttributes",
          "androidx.core.content.withStyledAttributes",
          "androidx.core.content.ContextKt",
          isTarget = { _, methodName, targetMethod, _ ->
            methodName == "recycle" && targetMethod.isInClass("android.content.res.TypedArray")
          },
          // For the obtainStyledAttributes(int[] attrs) overload, we have to
          // introduce a null parameter; the extension method doesn't match a single IntArray.
          replaceArgList =
            if (method.parameterList.parametersCount == 1)
              "(null, ${node.valueArguments.firstOrNull()?.sourcePsi?.text}"
            else "",
        )
      }
      "edit" -> {
        checkBlock(
          USE_KTX,
          context,
          node,
          method,
          name,
          "android.content.SharedPreferences",
          "android.content.SharedPreferences.Editor",
          "SharedPreferences",
          "edit",
          "androidx.core.content.edit",
          "androidx.core.content.SharedPreferencesKt",
          isTarget = { _, methodName, targetMethod, _ ->
            (methodName == "apply" || methodName == "commit") &&
              targetMethod.isInClass("android.content.SharedPreferences.Editor")
          },
          removeArgList = true,
        )
      }
      "beginTransaction",
      "beginTransactionNonExclusive" -> {
        checkBlock(
          USE_KTX,
          context,
          node,
          method,
          name,
          "android.database.sqlite.SQLiteDatabase",
          "android.database.sqlite.SQLiteDatabase",
          "SQLiteDatabase",
          "transaction",
          "androidx.core.database.sqlite.transaction",
          "androidx.core.database.sqlite.SQLiteDatabaseKt",
          isTarget = { _, methodName, targetMethod, _ ->
            methodName == "endTransaction" &&
              targetMethod.isInClass("android.database.sqlite.SQLiteDatabase")
          },
          isRequiredCall = { targetMethod ->
            val methodName = targetMethod.name
            methodName == "setTransactionSuccessful" &&
              targetMethod.isInClass("android.database.sqlite.SQLiteDatabase")
          },
          replaceArgList = if (name == "beginTransactionNonExclusive") "(exclusive = false" else "",
          removeArgList = name != "beginTransactionNonExclusive",
        )
      }
      "save" -> {
        var newName = "withSave"
        var replaceArgs = ""
        var deleteName: String? = null
        val parent = skipParenthesizedExprUp(node.uastParent)
        if (parent is UQualifiedReferenceExpression) {
          val receiver = parent.receiver.skipParenthesizedExprDown()
          val next = skipParenthesizedExprUp(node.nextStatement())
          if (next is UQualifiedReferenceExpression) {
            val nextSelector = next.selector.skipParenthesizedExprDown()
            if (nextSelector is UCallExpression) {
              val nextName = nextSelector.methodName
              val nextReceiver = next.receiver.skipParenthesizedExprDown().tryResolve()
              //noinspection LintImplPsiEquals
              if (nextReceiver != null && nextReceiver == receiver.tryResolve()) {
                val mappedName =
                  when (nextName) {
                    "translate" -> "withTranslation"
                    "rotate" -> "withRotation"
                    "scale" -> "withScale"
                    "skew" -> "withSkew"
                    "concat" -> "withMatrix"
                    "clipRect",
                    "clipPath" -> {
                      val lastType =
                        nextSelector.valueArguments.lastOrNull()?.getExpressionType()?.canonicalText
                      if (lastType == "android.graphics.Region.Op") {
                        return
                      }
                      "withClip"
                    }
                    else -> null
                  }
                if (mappedName != null) {
                  deleteName = nextName
                  newName = mappedName
                  replaceArgs =
                    (nextSelector.sourcePsi as? KtCallExpression)?.valueArgumentList?.text ?: ""
                }
              }
            }
          }
        }
        checkBlock(
          USE_KTX,
          context,
          node,
          method,
          name,
          "android.graphics.Canvas",
          "android.graphics.Canvas",
          "Canvas",
          newName,
          "androidx.core.graphics.$newName",
          "androidx.core.graphics.CanvasKt",
          isTarget = { call, methodName, targetMethod, variable ->
            var ok = false
            if (methodName == "restore" || methodName == "restoreToCount") {
              ok = targetMethod.isInClass("android.graphics.Canvas")
              if (ok) {
                if (methodName == "restoreToCount") {
                  // Check that we're passing in the same variable
                  val reference = call.valueArguments.firstOrNull()?.tryResolve()
                  if (reference != null) {
                    //noinspection LintImplPsiEquals
                    ok = reference == variable?.javaPsi || reference == variable?.sourcePsi
                  }
                }
              }
            }
            ok
          },
          isRequiredCall =
            if (deleteName == null) null
            else
              { targetMethod ->
                val methodName = targetMethod.name
                methodName == deleteName && targetMethod.isInClass("android.graphics.Canvas")
              },
          replaceArgList = replaceArgs,
          removeArgList = replaceArgs.isEmpty(),
        )
      }
    }
  }

  /**
   * Analyzes code for open-block-close sections like
   *
   *     var array = obtainStyledAttributes(...)
   *     block()
   *     array.recycle()
   *
   * and
   *
   *     sharedPreferences.edit()
   *         .block()
   *         .commit()
   *
   * and suggests replacing them with extension functions instead.
   *
   * It handles mapping from start() to extension() for these 3 patterns:
   *
   * Variable:
   *
   *     val var = something().start()           something().extension() {
   *     var.operation()                   =>        operation()
   *     var.finish()                            }
   *
   * Chained Calls:
   *
   *     something().start()                    something().extension() {
   *       .operation()                   =>        operation()
   *       .finish()                            }
   *
   * Existing Apply block:
   *
   *     something().start().apply {            something().extension() {
   *        operation()                   =>        operation()
   *     }.finish()                             }
   *
   * Examples of these pairs of start() and finish() are SharedPreferences.edit() and apply(), and
   * Context.obtainStyledAttributes() and recycle().
   *
   * There are many other possible variations here which we don't flag because they're not as common
   * and makes the refactoring more tricky, such as going via intermediate variables, using a
   * mixture of the above patterns, having conditional patterns around the finish call, etc.
   *
   * We also look out for potential problems where we can't extract the code into a new block, for
   * example where there is a new variable declared inside the block referenced outside of it:
   *
   *     val var = something().start()           something().extension() {
   *     val resources = getResources()   =>        val resources = getResources()
   *     var.finish()                            }
   *     process(resources)                      process(resources) // INVALID - resources not a variable
   */
  private fun checkBlock(
    issue: Issue,
    context: JavaContext,
    startCall: UCallExpression,
    method: PsiMethod,
    startName: String,
    startClass: String,
    targetClass: String,
    extensionClass: String,
    extensionMethod: String,
    import: String,
    containingClass: String?,
    isTarget: (UCallExpression, String?, PsiMethod, ULocalVariable?) -> Boolean,
    isRequiredCall: ((PsiMethod) -> Boolean)? = null,
    allowNesting: Boolean = false,
    replaceArgList: String = "",
    removeArgList: Boolean = false,
  ) {
    if (!method.isInClass(startClass)) {
      return
    }

    // If we require the library to be present, make sure we can access this extension function
    if (
      REQUIRE_LIBRARY.getValue(context) &&
        containingClass != null &&
        context.evaluator.findClass(containingClass) == null
    ) {
      return
    }

    val call = startCall.sourcePsi as? KtCallExpression ?: return
    val variable = findVariable(startCall)

    val (target, requiredCall, chained, thisReferences, scopingFunction) =
      findBlockInfo(
        variable,
        startCall,
        startName,
        startClass,
        targetClass,
        allowNesting,
        isRequiredCall,
        isTarget,
      ) ?: return

    if (startCall.getParentOfType<UParenthesizedExpression>() != null) {
      // Unlikely but can break fixes if there
      return
    }

    val source = context.getContents() ?: call.containingFile?.text ?: return
    // delete the left hand side of the variable; we don't need that anymore
    val deleteLhsStart = variable?.sourcePsi?.startOffset ?: -1
    val deleteLhsEnd = variable?.uastInitializer?.sourcePsi?.startOffset ?: -1

    val replaceAnchorStart = startCall.methodIdentifier?.sourcePsi?.startOffset ?: return
    var replaceAnchorEnd = startCall.methodIdentifier?.sourcePsi?.endOffset ?: return

    val rParen = call.valueArgumentList?.rightParenthesis ?: return
    val rParenStart = rParen.startOffset
    val rParenEnd = rParenStart + 1

    var replacedAnchor = extensionMethod
    if (replaceArgList.isNotEmpty()) {
      replacedAnchor = extensionMethod + replaceArgList.removeSuffix(")")
      replaceAnchorEnd = rParenStart
    } else if (removeArgList) {
      replacedAnchor = extensionMethod
      replaceAnchorEnd = rParenStart
    }

    // Individual edit operations to be combined into a single composite lint fix
    val fixList = mutableListOf<LintFix>()

    // Delete variable declaration
    if (deleteLhsStart != -1) {
      fixList.add(
        fix()
          .replace()
          .range(Location.create(context.file, source, deleteLhsStart, deleteLhsEnd))
          .text(source.substring(deleteLhsStart, deleteLhsEnd))
          .with("")
          .build()
      )
    }

    // Replace ( obtainStyledAttributes with withStyledAttributes before the argument list
    fixList.add(
      fix()
        .replace()
        .range(Location.create(context.file, source, replaceAnchorStart, replaceAnchorEnd))
        .text(source.substring(replaceAnchorStart, replaceAnchorEnd))
        .with(replacedAnchor)
        .imports(import)
        .reformat(true)
        .build()
    )

    // Insert a "{" after the parameter list right parenthesis
    val argListReplacement =
      when {
        // Special hack needed for the commit-style target prefs where we need an extra
        // parameter
        target.methodName == "commit" &&
          targetClass == "android.content.SharedPreferences.Editor" -> "(commit = true) {"
        scopingFunction != null && scopingFunction.methodName == "with" -> ""
        removeArgList -> " {"
        else -> ") {"
      }
    fixList.add(
      fix()
        .replace()
        .range(Location.create(context.file, source, rParenStart, rParenEnd))
        .text(source.substring(rParenStart, rParenEnd))
        .with(argListReplacement)
        .build()
    )

    val receiver = target.receiver
    val targetPsi = target.sourcePsi
    val recycleStart =
      if (receiver?.skipParenthesizedExprDown().isNameOrThis())
        receiver?.sourcePsi?.startOffset ?: return
      else
        (targetPsi?.parent as? KtDotQualifiedExpression)?.operationTokenNode?.startOffset
          ?: receiver?.sourcePsi?.startOffset
          ?: targetPsi?.startOffset
          ?: return
    val recycleEnd = targetPsi?.endOffset ?: return
    var indent = true

    if (scopingFunction != null) {
      indent = false

      if (scopingFunction.methodName == "with") {
        val withStart = scopingFunction.sourcePsi?.startOffset ?: return
        val withArgStart =
          scopingFunction.valueArguments.firstOrNull()?.sourcePsi?.startOffset ?: return
        fixList.add(
          fix()
            .replace()
            .range(Location.create(context.file, source, withStart, withArgStart))
            .text(source.substring(withStart, withArgStart))
            .with("")
            .build()
        )
      }

      // also/run/let: remove the call opening; keep the block body
      val originalParent = skipParenthesizedExprUp(startCall.uastParent)
      var p = originalParent
      while (p is UQualifiedReferenceExpression) {
        val sourcePsi = p.sourcePsi
        if (p.receiver == originalParent) {
          val selector = p.selector
          if (selector is UCallExpression && selector == scopingFunction) {
            val args = selector.valueArguments
            val last = args.lastOrNull()?.sourcePsi ?: return
            if (last is KtLambdaExpression && sourcePsi is KtDotQualifiedExpression) {
              val end = last.leftCurlyBrace.startOffset + 1
              val offset = sourcePsi.operationTokenNode.startOffset
              fixList.add(
                fix()
                  .replace()
                  .range(Location.create(context.file, source, offset, end))
                  .text(source.substring(offset, end))
                  .with("")
                  .build()
              )
            } else {
              return
            }
          }
        }
        p = skipParenthesizedExprUp(p.uastParent)
      }
    } else if (chained) {
      val originalParent = skipParenthesizedExprUp(startCall.uastParent)
      var p = originalParent
      while (p is UQualifiedReferenceExpression) {
        val sourcePsi = p.sourcePsi
        if (p.receiver == originalParent) {
          if (sourcePsi is KtDotQualifiedExpression) {
            val offset = sourcePsi.operationTokenNode.startOffset
            fixList.add(
              fix()
                .replace()
                .range(Location.create(context.file, source, offset, offset + 1))
                .text(".")
                .with("")
                .build()
            )
          }
          break
        }
        p = skipParenthesizedExprUp(p.uastParent)
      }
    } else {
      if (requiredCall != null) {
        // Make sure the required call has no suffix or prefix with side effects
        val begin = source.lineBegin(requiredCall.startOffset)
        val end = source.lineEnd(requiredCall.endOffset) + 1 // +1: remove the \n as well
        fixList.add(
          fix()
            .replace()
            .range(Location.create(context.file, source, begin, end))
            .text(source.substring(begin, end))
            .with("")
            .build()
        )
      }
    }

    thisReferences.forEach { ref ->
      val sourcePsi = ref.sourcePsi
      if (sourcePsi != null) {
        val refStart = sourcePsi.startOffset
        var replacement = "this"
        var refEnd = sourcePsi.endOffset
        var content = source.substring(refStart, refEnd)
        if (source[refEnd] == '.') {
          content += "."
          refEnd++
          replacement = ""
        }
        fixList.add(
          fix()
            .replace()
            .range(Location.create(context.file, source, refStart, refEnd))
            .text(content)
            .with(replacement)
            .build()
        )
      }
    }

    // Remove final recycle call and replace with `}`
    fixList.add(
      fix()
        .replace()
        .range(Location.create(context.file, source, recycleStart, recycleEnd))
        .text(source.substring(recycleStart, recycleEnd))
        .with(if (scopingFunction != null) "" else "}")
        .build()
    )

    // Try to indent the lines in the block as well
    val containingFile = call.containingFile
    var lineBegin = source.lineEnd(rParenEnd) + 1
    val last = source.lastIndexOf('\n', recycleStart)
    while (indent && lineBegin <= last) {
      val lineEnd = source.lineEnd(lineBegin)

      if (
        source.isNotBlankAt(lineBegin, lineEnd) &&
          // Don't add indentation on the line we plan to delete
          (requiredCall == null ||
            lineEnd < requiredCall.startOffset ||
            lineBegin > requiredCall.endOffset)
      ) {
        // Make sure we don't add whitespace to any multi-line string literals
        // for example
        if (containingFile.findElementAt(lineBegin) is PsiWhiteSpace) {
          // Add indentation fix:
          fixList.add(
            fix()
              .replace()
              .range(Location.create(context.file, source, lineBegin, lineBegin))
              .text("")
              .with("    ")
              .build()
          )
        }
      }
      lineBegin = lineEnd + 1
    }

    val fix =
      fix()
        .name("Replace with the $extensionMethod extension function", true)
        .composite(fixList)
        .autoFix()

    // Make sure we don't have a symbol conflict
    if (context.definesConflictingSymbol(extensionMethod, import, isProperty = false)) {
      return
    }

    val location =
      context.getCallLocation(startCall, includeReceiver = true, includeArguments = true)
    val message = createMessage(extensionClass, extensionMethod, false, import)
    context.report(issue, startCall, location, message, fix)
  }

  /**
   * For a given call, returns the variable it's assigned to, or null if not assigned to a variable
   */
  fun findVariable(startCall: UCallExpression): ULocalVariable? {
    var curr = skipParenthesizedExprUp(startCall.uastParent)
    while (true) {
      if (curr is UQualifiedReferenceExpression) {
        curr = curr.uastParent ?: return null
      } else {
        break
      }
    }
    return curr as? ULocalVariable
  }

  /**
   * Information about a particular call (such as `beginTransaction`) which could potentially be
   * replaced with an extension block call.
   *
   * This is a result object for [findBlockInfo].
   */
  private class BlockInfo(
    /**
     * The target call we were looking for. For example, for `obtainStyledAttributes` we're looking
     * for `TypedArray#recycle`.
     */
    val targetCall: UCallExpression,
    /**
     * If not null, a pointer to the required call. For example, for `beginTransaction` (which has
     * [targetCall] `endTransaction` we also require the call `setTransactionSuccessful`.
     */
    val requiredCall: KtElement?,
    /**
     * Whether the calls in the block are chained (for example,
     * `beginTransaction().setTransactionSuccessful().endTransaction()`, rather than `val t =
     * beginTransaction(); t.setTransactionSuccessful(); t.endTransaction()`.)
     */
    val chained: Boolean,
    /**
     * A list of all the references in the newly formed block which are referring to the variable
     * and need to be replaced with "this" instead. For example, if we have
     *
     * ```
     *  database.beginTransaction()
     *  create(database)
     *  database.setTransactionSuccessful()
     *  database.endTransaction()
     * ```
     *
     * the `create(database)` call needs to be changed to `create(this)` in the replacement:
     *
     *  ```
     *  database.transaction() {
     *      create(this)
     *  }
     *  ```
     */
    val thisReferences: List<USimpleNameReferenceExpression>,
    /**
     * If this call body is using a scoping function (like also, run, let, with, etc.), that scoping
     * function is provided here.
     */
    val scopingFunction: UCallExpression?,
  ) {
    operator fun component1() = targetCall

    operator fun component2() = requiredCall

    operator fun component3() = chained

    operator fun component4() = thisReferences

    operator fun component5() = scopingFunction
  }

  /**
   * Analyzes a method call to see if it looks like it matches the potential block extension method,
   * and if so, return a [BlockInfo].
   */
  private fun findBlockInfo(
    variable: ULocalVariable?,
    startCall: UCallExpression,
    startName: String,
    startClass: String,
    targetClass: String,
    allowNesting: Boolean,
    isRequiredCall: ((PsiMethod) -> Boolean)?,
    isTarget: (UCallExpression, String?, PsiMethod, ULocalVariable?) -> Boolean,
  ): BlockInfo? {
    val block = startCall.getParentOfType<UBlockExpression>(true) ?: return null
    val receiverIsThis = startClass == targetClass
    val variablePsi =
      variable?.javaPsi ?: if (receiverIsThis) startCall.receiver?.tryResolve() else null
    val variableSourcePsi = variable?.sourcePsi

    var start: UCallExpression? = null
    var target: UCallExpression? = null
    var requiredCall: KtElement? = null
    var extractable = true
    var chained = false
    var scopingFunction: UCallExpression? = null
    var letLambda: PsiElement? = null
    val thisReferences = mutableListOf<USimpleNameReferenceExpression>()
    block.accept(
      object : AbstractUastVisitor() {
        private fun foundStart() = start != null

        private fun foundEnd() = target != null

        private var variables = mutableListOf<PsiElement>()

        init {
          if (variable != null) {
            variable.sourcePsi?.let { variables.add(it) }
            variable.javaPsi?.let { variables.add(it) }
          }
        }

        override fun visitLocalVariable(node: ULocalVariable): Boolean {
          // Record any newly declared variables inside the start to end range.
          // We want to make sure they aren't referenced AFTER the end.
          if (foundStart() && !foundEnd()) {
            node.sourcePsi?.let { variables.add(it) }
            node.javaPsi?.let { variables.add(it) }
          }
          return super.visitVariable(node)
        }

        override fun visitCallExpression(node: UCallExpression): Boolean {
          val resolved = node.resolve() ?: return super.visitCallExpression(node)

          if (start == null && node == startCall) {
            start = node

            // See if we have a scoping function call on it
            // e.g. obtainStyledAttributes(...).run { ...; recycle() }
            val parent = skipParenthesizedExprUp(node.uastParent)
            val parentParent = skipParenthesizedExprUp(parent?.uastParent)
            if (parentParent is UQualifiedReferenceExpression) {
              if (parentParent.receiver.skipParenthesizedExprDown() === parent) {
                val selector = parentParent.selector
                if (selector is UCallExpression && isScopingFunction(selector)) {
                  // apply, run, let.
                  scopingFunction = selector
                  if (selector.methodName == "let") {
                    // collect `it`-references
                    letLambda =
                      (selector.valueArguments.singleOrNull() as? ULambdaExpression)?.sourcePsi
                  }
                }
              }
            } else if (parentParent is UCallExpression && isScopingFunction(parentParent)) {
              // with
              scopingFunction = parentParent
            }
          } else if (
            start != null &&
              target == null &&
              !allowNesting &&
              resolved.name == startName &&
              resolved.isInClass(startClass)
          ) {
            extractable = false
            return true
          } else if (start != null && (target == null || !allowNesting)) {
            if (
              isTarget(node, resolved.name, resolved, variable) &&
                (variable == null ||
                  node.getParentOfType(
                    UBlockExpression::class.java,
                    true,
                    UIfExpression::class.java,
                    USwitchExpression::class.java,
                  ) == variable.getParentOfType<UBlockExpression>())
            ) {
              if (target != null && !allowNesting) {
                extractable = false
                return true
              }
              val parent = skipParenthesizedExprUp(node.uastParent)
              chained = parent != null && start.isBelow(parent, true)

              if (!chained && !sameBlockParent(start, node)) {
                return false
              }

              if (!chained && scopingFunction != null) {
                // Make sure it's the last statement in the block
                var prev: UElement = node
                var curr = node.uastParent
                val lambda =
                  // Most scoping functions take a single argument but with takes 2
                  (scopingFunction.valueArguments.lastOrNull() as? ULambdaExpression)?.body
                while (curr != null && curr != lambda) {
                  if (curr is UQualifiedReferenceExpression) {
                    if (
                      prev !== curr.selector ||
                        !curr.receiver.skipParenthesizedExprDown().isNameOrThis()
                    ) {
                      extractable = false
                      return false
                    }
                  } else if (curr is UParenthesizedExpression || curr is UReturnExpression) {
                    // OK
                  } else {
                    extractable = false
                    return false
                  }
                  prev = curr
                  curr = curr.uastParent
                }
                if (
                  curr === lambda &&
                    lambda is UBlockExpression &&
                    prev !== lambda.expressions.last()
                ) {
                  // Make sure it's the last call in the block
                  extractable = false
                  return false
                }
              }

              target = node

              // Make sure we don't try to rewrite the final "variable.recycle()" call
              // into "this.recycle()" since we'll be deleting this one

              if (parent is UQualifiedReferenceExpression) {
                thisReferences.remove(parent.receiver.skipParenthesizedExprDown())
              }
            } else if (
              target == null &&
                requiredCall == null &&
                isRequiredCall != null &&
                isRequiredCall(resolved)
            ) {
              if (!sameBlockParent(start, node)) {
                extractable = false
              }

              var c: UElement? = node.uastParent
              while (c != null && extractable) {
                if (c is UQualifiedReferenceExpression) {
                  val receiver = c.receiver.skipParenthesizedExprDown()
                  if (receiver is USimpleNameReferenceExpression) {
                    thisReferences.remove(receiver)
                  } else {
                    extractable = false
                  }
                  if (c.selector is UCallExpression && c.selector != node) {
                    // Make sure we don't have some suffix call on the result that potentially
                    // has a side effect
                    extractable = false
                  }
                } else {
                  break
                }
                requiredCall = c.sourcePsi as? KtElement
                c = skipParenthesizedExprUp(c.uastParent) ?: break
              }
            }
          }
          return super.visitCallExpression(node)
        }

        @Suppress("LintImplPsiEquals")
        override fun visitSimpleNameReferenceExpression(
          node: USimpleNameReferenceExpression
        ): Boolean {
          // Make sure we aren't accessing a new variable OUTSIDE the extraction range
          if (foundEnd() && extractable) {
            val resolved = node.resolve()
            if (variables.contains(resolved) && target != null && !node.isBelow(target, true)) {
              extractable = false
            }
          } else if (foundStart() && !foundEnd()) {
            val resolved = node.resolve()
            if (letLambda != null && resolved is PsiParameter && resolved.parent == letLambda) {
              thisReferences.add(node)
            }
            if (variablePsi != null) {
              val resolved = node.resolve()
              if (resolved != null && (resolved == variableSourcePsi || resolved == variablePsi)) {
                thisReferences.add(node)
              }
            }
          }

          return super.visitSimpleNameReferenceExpression(node)
        }
      }
    )

    return if (extractable && target != null && !(isRequiredCall != null && requiredCall == null)) {
      BlockInfo(target, requiredCall, chained, thisReferences, scopingFunction)
    } else {
      null
    }
  }

  /** Returns true if the given expression is a simple name or `this`. */
  private fun UExpression?.isNameOrThis(): Boolean {
    return this is USimpleNameReferenceExpression || this is UThisExpression
  }

  private fun UExpression.parentBlock(): UExpression? {
    var curr = uastParent
    while (curr != null) {
      if (
        curr is UBlockExpression ||
          curr is UIfExpression ||
          curr is USwitchExpression ||
          curr is ULoopExpression
      ) {
        // TODO: Use analysis API here to look at returns self
        val parent = curr.uastParent
        if (parent is ULambdaExpression) {
          val pParent = parent.uastParent
          if (pParent is UCallExpression && isReturningLambdaResult(pParent)) {
            curr = pParent
            continue
          }
        }
        return curr
      }
      curr = curr.uastParent
    }
    return null
  }

  /**
   * Given an opening and closing call, returns whether they are in the same logical block. For
   * example, for `beginTransaction` and `endTransaction` below, the following two examples are both
   * at the same level:
   *
   *     database.beginTransaction
   *     ...
   *     database.endTransaction()
   *     database.beginTransaction
   *     try {
   *     } finally {
   *         database.endTransaction()
   *     }
   *
   * but this is not:
   *
   *     database.beginTransaction
   *     if (close)
   *         database.endTransaction()
   */
  private fun sameBlockParent(start: UExpression, end: UExpression): Boolean {
    val startParent = start.parentBlock() ?: return false
    val targetParent = end.parentBlock() ?: return false
    if (startParent != targetParent) {
      val parent = targetParent.uastParent
      return parent is UTryExpression && parent.uastParent === startParent
    }

    return true
  }

  /**
   * Analyzes code for calls (and property references) where that call can be replaced with an
   * extension function or extension property instead.
   *
   * It handles various scenarios:
   * * A static utility method is replaced with an extension instance method, e.g.
   *   rewriting`Uri.parse(url)` into `url.toUri()`
   * * Reordering arguments
   * * Dropping arguments that are already default, e.g. replacing `Bitmap.createBitmap(width,
   *   height, Bitmap.Config.ARGB_8888)` with `createBitmap(width, height)` (but only when the
   *   bitmap type argument is ARGB_8888, the default)
   * * Combining the result of a call and a comparison into a single method call, such as
   *   `array.indexOfValue(value) >= 0` into `array.containsValue(value)`
   *
   * In the following, [node] is the function call to [method], named [methodName], with descriptor
   * [descriptor], in [containingClass]. The suggested replacement is extension method named
   * [extensionMethod], in [extensionPackage], on receiver class [extensionClass], defined in JVM
   * class [extensionJvmClass].
   *
   * If this method call is then compared (==, !=, >, >=, etc) with a constant, the comparison
   * operator is passed in as [operator], and the constant as [rhs]. The constant is in Java source
   * format, so 0 would be the string "0".
   *
   * If the signatures match exactly, there is no argument mapping, but otherwise, the
   * [argumentMapping] string has characters defining how each parameter in the replaced method
   * should map to the parameters in the extension mapping. The meanings of the digits are:
   * * `R` - this argument should be the receiver
   * * '0'-'9' - this argument should be mapped to the numbered parameter.
   *
   * For example, the string "R02" says that the first parameter should now be the *receiver* for
   * the extension, the second parameter should be the first parameter in the extension call, the
   * third parameter should also be the third parameter in the replacement.
   *
   * The [defaultArguments] correspond to the parameter default values of the extension function. An
   * empty string means that there is no default. This method will drop arguments to the call if
   * they match the existing default (and there aren't any non-default arguments remaining).
   *
   * Finally, the [isProperty] method indicates whether the extension is a property rather than a
   * function.
   *
   * This method should return `true` if there was a match; otherwise `false`.
   */
  @Suppress("LintImplPsiEquals")
  private fun checkCall(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    methodName: String,
    descriptor: String,
    containingClass: String,
    extensionMethod: String,
    extensionClass: String,
    extensionJvmClass: String,
    extensionPackage: String,
    operator: String?,
    rhs: String,
    argumentMapping: String?,
    isProperty: Boolean,
    defaultArguments: List<String>?,
  ): Boolean {
    if (containingClass.isNotEmpty() && !method.isInClass(containingClass)) return false
    if (descriptor.isNotEmpty()) {
      val desc = context.evaluator.getMethodDescription(method, false, false)
      if (desc != descriptor) {
        return false
      }
    }

    val sourcePsi = node.sourcePsi ?: return false
    val replacedElement: KtExpression
    val call: KtCallExpression?

    // If this extension method requires an operator comparison,
    // match it in the AST and make sure the comparison value matches
    // as well.
    if (operator != null) {
      val binary = sourcePsi.parentOfType<KtBinaryExpression>() ?: return false
      if (!binary.isSameComparison(operator, rhs, methodName)) {
        return false
      }

      replacedElement = binary
      val left = binary.left?.skipParenthesizedExprDown() ?: return false
      call =
        left as? KtCallExpression
          ?: if (left is KtDotQualifiedExpression) {
            if (left.selectorExpression is KtCallExpression) {
              left.selectorExpression as KtCallExpression
            } else if (left.selectorExpression is KtNameReferenceExpression) {
              null
            } else {
              return false
            }
          } else if (left is KtNameReferenceExpression) {
            null
          } else {
            return false
          }
    } else {
      call = sourcePsi as? KtCallExpression ?: return false

      val parent = sourcePsi.parent
      replacedElement =
        if (parent is KtDotQualifiedExpression) {
          if (parent.selectorExpression == call) parent else call
        } else call
    }

    // Map call arguments over to extension arguments
    val receiver: KtExpression?
    val arguments: List<KtExpression?>
    val newArgs: Array<KtExpression?>
    if (call != null) {
      arguments = getArgumentsInParameterOrder(call) ?: return false
      val argumentInfo =
        computeArgumentMapping(call, arguments, defaultArguments, argumentMapping) ?: return false
      newArgs = argumentInfo.extensionArgs
      receiver = argumentInfo.receiver
    } else {
      receiver = null
      newArgs = emptyArray()
      arguments = emptyList()
    }

    // Make sure we don't have a symbol conflict
    val import = "$extensionPackage.$extensionMethod"
    if (context.definesConflictingSymbol(extensionMethod, import, isProperty)) {
      return false
    }

    // If we require the library to be present, make sure we can access this extension function
    if (REQUIRE_LIBRARY.getValue(context)) {
      val containingClass =
        if (extensionJvmClass.contains('.')) extensionJvmClass
        else {
          "$extensionPackage.$extensionJvmClass"
        }
      if (context.evaluator.findClass(containingClass) == null) {
        return false
      }
    }

    val replacement =
      generateCallReplacement(
        extensionPackage,
        extensionClass,
        extensionMethod,
        descriptor,
        receiver,
        sourcePsi.parent,
        call,
        isProperty,
        defaultArguments,
        context,
        argumentMapping,
        arguments,
        newArgs,
      )

    val location = context.getLocation(replacedElement)
    val fix =
      fix()
        .name(createFixMessage(extensionClass, extensionMethod, isProperty), true)
        .replace()
        .range(location)
        .text(replacedElement.text)
        .with(replacement)
        .imports(*(if (extensionPackage.isNotBlank()) arrayOf(import) else emptyArray()))
        .reformat(true)
        .autoFix()
        .build()

    val message = createMessage(extensionClass, extensionMethod, isProperty, extensionPackage)
    context.report(USE_KTX, node, location, message, fix)
    return true
  }

  /**
   * Checks whether the given [symbol] (with fully qualified name [import]) has a conflict in this
   * source file. This is the case if there is a method of the same name in the file, or if there is
   * an import of a symbol with the same name (other than the fully qualified name itself.)
   */
  private fun JavaContext.definesConflictingSymbol(
    symbol: String,
    import: String,
    isProperty: Boolean,
  ): Boolean {
    val ktFile = psiFile as? KtFile ?: return false
    // Already imported a conflicting name?
    for (directive in ktFile.importDirectives) {
      val name = directive.importedName?.asString()
      if (name == symbol) {
        return directive.importedFqName?.asString() != import
      }
    }
    // First check whether the symbol appears anywhere in the file; if not
    // we don't have to visit it.
    val contents = getContents() ?: ktFile.text
    if (!contents.containsIdentifier(symbol)) {
      // No reference to the same symbol name
      return false
    }
    // Visit the file looking for conflicting declarations.
    // (In theory we could skip conflicting declarations not reachable from
    // the current context, such as methods in sibling nested classes etc.)
    var found = false
    ktFile.accept(
      object : KtTreeVisitorVoid() {
        override fun visitNamedFunction(function: KtNamedFunction) {
          // We *could* restrict the conflict check to only
          // look for conflicting functions for imported extension
          // functions, and for conflicting properties for imported
          // extension functions, but this can still lead to some
          // confusion for the user even if it doesn't confuse the
          // compiler, so we don't skip the below check if !isProperty
          // (and we don't have a check for "isProperty" in visitProperty
          // below.)
          if (function.name == symbol && !function.isLocal) {
            found = true
          }
          super.visitNamedFunction(function)
        }

        override fun visitProperty(property: KtProperty) {
          if (property.name == symbol && !property.isLocal) {
            found = true
          }
          super.visitProperty(property)
        }
      }
    )

    return found
  }

  /** Result info from [computeArgumentMapping] */
  private class ArgumentMapping(
    /** If non-null, the given argument should become the new receiver instead */
    val receiver: KtExpression?,
    /**
     * The argument expressions from the original call rearranged to map to the correct parameter
     * order for the extension call (and some arguments possibly dropped)
     */
    val extensionArgs: Array<KtExpression?>,
  )

  /**
   * Compute mapping from the current method [call] [arguments], to the new extension functions
   * arguments (and receiver).
   *
   * We need to rewrite the arguments to this call according to the argument map. The argument
   * mapping is a string corresponding to the positions of the arguments in the call. For example,
   * consider
   *
   * `Array.binarySearch(element: T, comparator: Comparator<in T>, fromIndex: Int, toIndex: Int):
   * Int = Arrays.binarySearch(this, fromIndex, toIndex, element, comparator)`
   *
   * For this declaration, the mapping string is "R2301", corresponding to the 5 arguments to a call
   * to Arrays.binarySearch.
   *
   * For example, for this call: Arrays.binarySearch(array, 0, length, key, comparator) the mapping
   * R2301 works out like this:
   * * R: the first argument in the current call should be used as the receiver for the extension
   *   call
   * * 2: the second argument in the current call should be the third (because we count starting
   *   with 0) argument in the extension call
   * * 3: the third argument in the current call should be the fourth argument in the extension call
   * * 0: the fourth argument in the current call should be the first argument in the extension call
   * * 1: the fifth argument in the current call should be the second argument in the extension call
   */
  private fun computeArgumentMapping(
    call: KtCallExpression,
    arguments: List<KtExpression?>,
    defaultArguments: List<String>?,
    argumentMapping: String?,
  ): ArgumentMapping? {
    if (arguments.size > MAX_ARGUMENTS) {
      return null
    }
    val extensionArguments = arrayOfNulls<KtExpression>(MAX_ARGUMENTS)

    var receiver: KtExpression? = null
    if (argumentMapping != null) {
      var last = -1
      for (i in arguments.indices) {
        if (i == argumentMapping.length) {
          // varargs arguments
          for (j in i until arguments.size) {
            extensionArguments[++last] = arguments[j]
          }
          break
        }
        val target = argumentMapping[i]
        val arg = arguments[i]
        if (arg == null) {
          if (target == 'R') {
            // We expect this parameter to be dropped
            continue
          } else {
            if (target.isDigit()) {
              val position = target - '0'
              if (
                defaultArguments != null &&
                  position < defaultArguments.size &&
                  defaultArguments[position].isNotEmpty()
              ) {
                // We didn't supply an argument for this parameter, but that's fine,
                // because it maps to an extension parameter that has a default.
                continue
              }
            }

            if (i == arguments.size - 1) {
              // Varargs parameter where we didn't supply anything
              extensionArguments[i] = null
              break
            }

            return null
          }
        }
        if (target == 'R') {
          receiver = arg
        } else {
          val position = target - '0'
          extensionArguments[position] = arg
          last = position
        }
      }
    }
    return ArgumentMapping(receiver, extensionArguments)
  }

  @Suppress("LintImplPsiEquals")
  private fun generateCallReplacement(
    extensionPackage: String,
    extensionClass: String,
    extensionMethod: String,
    descriptor: String,
    receiver: KtExpression?,
    parent: PsiElement?,
    call: KtCallExpression?,
    isProperty: Boolean,
    defaultArguments: List<String>?,
    context: JavaContext,
    argumentMapping: String?,
    args: List<KtExpression?>,
    extensionArguments: Array<KtExpression?>,
  ): String {
    val useArraySyntax = isArrayGetExtension(extensionMethod, descriptor)
    val isArraySet = useArraySyntax && extensionMethod == "set"
    val useInfixSyntax = isInfixExtension(extensionMethod)

    val sb = StringBuilder()

    if (receiver != null) {
      val needsParens = receiver is KtBinaryExpression
      if (needsParens) {
        sb.append('(')
      }
      sb.append(receiver.text)
      if (needsParens) {
        sb.append(')')
      }
      sb.append('.')
    } else if (extensionClass.isEmpty()) {
      // going from utility method to another utility or top level method: delete
    } else if (parent is KtDotQualifiedExpression && parent.receiverExpression != call) {
      sb.append(parent.receiverExpression.text)
      if (useInfixSyntax) {
        sb.append(' ')
      } else if (!useArraySyntax) {
        sb.append('.')
      }
    }
    if (!useArraySyntax) {
      sb.append(extensionMethod)
    }

    if (!isProperty) {
      // The argument-mapping maps between the position in the argument list and
      // the corresponding position in the new call list. The default arguments
      // list on the other maps each element to the corresponding position in the
      // new call's parameter list. Therefore, we need to compare arguments with
      // their mapped position.
      if (defaultArguments != null && REMOVE_DEFAULTS.getValue(context)) {
        for (i in defaultArguments.size - 1 downTo 0) {
          val defaultArgument = defaultArguments[i]
          if (defaultArgument == "") {
            // All the defaults are at the end; no more defaults
            break
          }

          val argumentIndex = argumentMapping?.indexOf('0' + i) ?: i
          val argument = args[argumentIndex] ?: break
          // See if the argument matches the default. For now,
          // we're simply doing a source-textual comparison; ideally,
          // we'd do a full comparison here (e.g. comparing using fully
          // qualified names, resolving type aliases, etc.)
          val argumentText = argument.text
          if (
            defaultArgument == argumentText ||
              defaultArgument.endsWith(argumentText) ||
              argumentText.endsWith(".$defaultArgument")
          ) {
            extensionArguments[i] = null
          } else {
            break
          }
        }
      }

      val size = extensionArguments.indexOf(null)

      var skipParens = size == 1 && extensionArguments[0] is KtLambdaExpression || useInfixSyntax
      if (!skipParens) {
        if (useArraySyntax) {
          sb.append('[')
        } else {
          sb.append('(')
        }
      } else {
        sb.append(' ')
      }
      for (i in 0 until size) {
        val arg = extensionArguments[i] ?: break
        if (!skipParens && i == size - 1 && arg is KtLambdaExpression) {
          sb.append(") ")
          skipParens = true
        }

        val argText = arg.text
        sb.append(argText)
        if (isArraySet && i == size - 2) {
          sb.append("] = ")
        } else {
          sb.append(", ")
        }
      }

      // Using call order here, not [args] which are arranged in parameter order, not
      // necessarily the order used in the source
      val hadTrailingComma = call?.valueArguments?.lastOrNull()?.nextSibling?.text == ","
      if (!hadTrailingComma && sb.endsWith(", ")) {
        sb.setLength(sb.length - 2)
      }

      if (!skipParens) {
        if (useArraySyntax) {
          if (!isArraySet) {
            sb.append(']')
          }
        } else {
          sb.append(')')
        }
      }
    }

    return sb.toString()
  }

  private fun KaSession.getArgumentToParameterIndices(resolvedCall: KaFunctionCall<*>): IntArray {
    val called = resolvedCall.symbol
    val parameters = called.valueParameters
    val callArgumentMapping = resolvedCall.argumentMapping
    val result = IntArray(callArgumentMapping.size) { -1 }
    // The argument mapping is a LinkedHashMap, an ordered map,
    // corresponding to the argument order
    var argumentIndex = 0
    for ((_, parameterSignature) in callArgumentMapping) {
      val parameterIndex = parameters.indexOf(parameterSignature.symbol)
      result[argumentIndex++] = parameterIndex
    }

    return result
  }

  private fun getArgumentsInParameterOrder(
    call: KtCallExpression,
    argumentToParameterIndices: IntArray,
    parameterToArgumentIndices: IntArray,
  ): List<KtExpression?> {
    val arguments = call.valueArguments
    val result = ArrayList<KtExpression?>(parameterToArgumentIndices.size)

    for (i in parameterToArgumentIndices.indices) {
      val argumentIndex = parameterToArgumentIndices[i]
      if (argumentIndex == -1) {
        result.add(null)
      } else if (argumentIndex >= 0) {
        result.add(arguments[argumentIndex].getArgumentExpression())
      } else {
        val index = -(argumentIndex + 2)
        // We have a varargs parameter
        for (j in argumentToParameterIndices.indices) {
          if (argumentToParameterIndices[j] == index) {
            result.add(arguments[j].getArgumentExpression())
          }
        }
      }
    }

    return result
  }

  /**
   * Given an array where each element in order represents the supplied arguments to a call and the
   * array value is its corresponding parameter index, returns the corresponding array of elements
   * in parameter order, where the array value corresponds to the index in the input array of the
   * corresponding argument. A value of -1 means that no value was supplied to the corresponding
   * parameter. If the value is less than -1, The last value may be negative; this means that there
   * were multiple values supplied here as a varargs value (We don't just use the negative index, we
   * use `-(x+2)`, since we're reserving -1 for "not supplied").
   */
  private fun IntArray.reverse(parameterCount: Int): IntArray {
    val result = IntArray(parameterCount) { -1 }

    for (i in indices) {
      val parameterIndex = this[i]
      if (parameterIndex != -1) {
        if (result[parameterIndex] != -1) {
          result[parameterIndex] = -(parameterIndex + 2)
        } else {
          result[parameterIndex] = i
        }
      }
    }

    return result
  }

  private fun getArgumentsInParameterOrder(call: KtCallExpression): List<KtExpression?>? {
    analyze(call) {
      val resolvedCall: KaFunctionCall<*>? = call.resolveToCall()?.successfulFunctionCallOrNull()
      if (resolvedCall != null) {
        val argumentToParameterIndices = getArgumentToParameterIndices(resolvedCall)

        val parameterCount = resolvedCall.symbol.valueParameters.size

        val parameterToArgumentIndices = argumentToParameterIndices.reverse(parameterCount)
        val arguments =
          getArgumentsInParameterOrder(call, argumentToParameterIndices, parameterToArgumentIndices)
        return arguments
      }
    }

    return null
  }

  private fun createMessage(
    extensionClass: String,
    extensionMethod: String,
    property: Boolean,
    import: String,
  ): String {
    val isStdlib = import.isBlank() || import.startsWith("kotlin.")
    val library = if (isStdlib) "stdlib" else "KTX"
    val type = if (property) "property" else "function"
    return if (extensionClass.isEmpty()) {
      "Use the $library $type `$extensionMethod` instead?"
    } else {
      "Use the $library extension $type `$extensionClass.$extensionMethod` instead?"
    }
  }

  private fun createFixMessage(
    extensionClass: String,
    extensionMethod: String,
    property: Boolean,
  ): String {
    val type =
      "${if (extensionClass.isNotEmpty()) "extension " else ""}${if (property) "property" else "function"}"
    return "Replace with the $extensionMethod $type"
  }

  private fun checkMethodExtensions(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    name: String,
  ) {
    fun check(
      containingClass: String,
      descriptor: String,
      extensionMethod: String,
      extensionPackage: String,
      extensionClass: String,
      extensionJvmClass: String,
      op: String? = null,
      rhs: String = "",
      argmap: String? = null,
      property: Boolean = false,
      defaults: List<String>? = null,
    ): Boolean {
      return checkCall(
        context,
        node,
        method,
        name,
        descriptor,
        containingClass,
        extensionMethod,
        extensionClass,
        extensionJvmClass,
        extensionPackage,
        op,
        rhs,
        argmap,
        property,
        defaults,
      )
    }

    when (name) {

      // network utilities

      "parse" -> {
        /* fun String.toUri(): Uri = Uri.parse(this) */
        check(
          "android.net.Uri",
          "(Ljava.lang.String;)",
          "toUri",
          "androidx.core.net",
          "String",
          "UriKt",
          argmap = "R",
        )
      }

      // text utilities

      "getTrimmedLength" -> {
        /* fun CharSequence.trimmedLength(): Int = TextUtils.getTrimmedLength(this) */
        check(
          "android.text.TextUtils",
          "(Ljava.lang.CharSequence;)",
          "trimmedLength",
          "androidx.core.text",
          "CharSequence",
          "CharSequenceKt",
          argmap = "R",
        )
      }
      "isDigitsOnly" -> {
        /* fun CharSequence.isDigitsOnly(): Boolean = TextUtils.isDigitsOnly(this) */
        check(
          "android.text.TextUtils",
          "(Ljava.lang.CharSequence;)",
          "isDigitsOnly",
          "androidx.core.text",
          "CharSequence",
          "CharSequenceKt",
          argmap = "R",
        )
      }
      "fromHtml" -> {
        /* fun String.parseAsHtml(flags: Int = FROM_HTML_MODE_LEGACY, imageGetter: ImageGetter? = null, tagHandler: TagHandler? = null): Spanned = HtmlCompat.fromHtml(this, flags, imageGetter, tagHandler) */
        check(
          "androidx.core.text.HtmlCompat",
          "(Ljava.lang.String;ILandroid.text.Html.ImageGetter;Landroid.text.Html.TagHandler;)",
          "parseAsHtml",
          "androidx.core.text",
          "String",
          "HtmlKt",
          argmap = "R012",
          defaults = listOf("androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY", "null", "null"),
        )
      }
      "toHtml" -> {
        /* fun Spanned.toHtml(option: Int = TO_HTML_PARAGRAPH_LINES_CONSECUTIVE): String = HtmlCompat.toHtml(this, option) */
        check(
          "androidx.core.text.HtmlCompat",
          "(Landroid.text.Spanned;I)",
          "toHtml",
          "androidx.core.text",
          "Spanned",
          "HtmlKt",
          argmap = "R0",
          defaults = listOf("androidx.core.text.HtmlCompat.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE"),
        )
      }
      "htmlEncode" -> {
        /* fun String.htmlEncode(): String = TextUtils.htmlEncode(this) */
        check(
          "android.text.TextUtils",
          "(Ljava.lang.String;)",
          "htmlEncode",
          "androidx.core.text",
          "String",
          "StringKt",
          argmap = "R",
        )
      }
      "getLayoutDirectionFromLocale" -> {
        /* val Locale.layoutDirection: Int get() = TextUtils.getLayoutDirectionFromLocale(this) */
        check(
          "android.text.TextUtils",
          "(Ljava.util.Locale;)",
          "layoutDirection",
          "androidx.core.text",
          "Locale",
          "LocaleKt",
          argmap = "R",
          property = true,
        )
      }

      // graphics utilities

      "createBitmap" -> {
        /* fun createBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap { return Bitmap.createBitmap(width, height, config) } */
        check(
          "android.graphics.Bitmap",
          "(IILandroid.graphics.Bitmap.Config;)",
          "createBitmap",
          "androidx.core.graphics",
          "",
          "BitmapKt",
          argmap = "012",
          defaults = listOf("", "", "android.graphics.Bitmap.Config.ARGB_8888"),
        )
        /* fun createBitmap(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888, hasAlpha: Boolean = true, colorSpace: ColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)): Bitmap { return Bitmap.createBitmap(width, height, config, hasAlpha, colorSpace) } */
        check(
          "android.graphics.Bitmap",
          "(IILandroid.graphics.Bitmap.Config;ZLandroid.graphics.ColorSpace;)",
          "createBitmap",
          "androidx.core.graphics",
          "",
          "BitmapKt",
          argmap = "01234",
        )
      }
      "createScaledBitmap" -> {
        /* fun Bitmap.scale(width: Int, height: Int, filter: Boolean = true): Bitmap { return Bitmap.createScaledBitmap(this, width, height, filter) } */
        check(
          "android.graphics.Bitmap",
          "(Landroid.graphics.Bitmap;IIZ)",
          "scale",
          "androidx.core.graphics",
          "Bitmap",
          "BitmapKt",
          argmap = "R012",
          defaults = listOf("", "", "true"),
        )
      }
      "BitmapDrawable" -> {
        /* fun Bitmap.toDrawable(resources: Resources): BitmapDrawable = BitmapDrawable(resources, this) */
        check(
          "android.graphics.drawable.BitmapDrawable",
          "(Landroid.content.res.Resources;Landroid.graphics.Bitmap;)",
          "toDrawable",
          "androidx.core.graphics.drawable",
          "Bitmap",
          "BitmapDrawableKt",
          argmap = "0R",
        )
      }
      "ColorDrawable" -> {
        /* fun Int.toDrawable(): ColorDrawable = ColorDrawable(this) */
        check(
          "android.graphics.drawable.ColorDrawable",
          "(I)",
          "toDrawable",
          "androidx.core.graphics.drawable",
          "Int",
          "ColorDrawableKt",
          argmap = "R",
        )
      }

      // color utilities

      "red" -> {
        /* val Long.red: Float get() = Color.red(this) */
        check(
          "android.graphics.Color",
          "(J)",
          "red",
          "androidx.core.graphics",
          "Long",
          "ColorKt",
          argmap = "R",
          property = true,
        )
      }
      "green" -> {
        /* val Long.green: Float get() = Color.green(this) */
        check(
          "android.graphics.Color",
          "(J)",
          "green",
          "androidx.core.graphics",
          "Long",
          "ColorKt",
          argmap = "R",
          property = true,
        )
      }
      "blue" -> {
        /* val Long.blue: Float get() = Color.blue(this) */
        check(
          "android.graphics.Color",
          "(J)",
          "blue",
          "androidx.core.graphics",
          "Long",
          "ColorKt",
          argmap = "R",
          property = true,
        )
      }
      "alpha" -> {
        /* val Long.alpha: Float get() = Color.alpha(this) */
        check(
          "android.graphics.Color",
          "(J)",
          "alpha",
          "androidx.core.graphics",
          "Long",
          "ColorKt",
          argmap = "R",
          property = true,
        )
      }
      "parseColor" -> {
        /* fun String.toColorInt(): Int = Color.parseColor(this) */
        check(
          "android.graphics.Color",
          "(Ljava.lang.String;)",
          "toColorInt",
          "androidx.core.graphics",
          "String",
          "ColorKt",
          argmap = "R",
        )
      }
      "toArgb" -> {
        /* fun Long.toColorInt(): Int = Color.toArgb(this) */
        check(
          "android.graphics.Color",
          "(J)",
          "toColorInt",
          "androidx.core.graphics",
          "Long",
          "ColorKt",
          argmap = "R",
        )
      }

      // navigation utilities

      "findNavController" -> {
        /* fun Activity.findNavController(viewId: Int): NavController = Navigation.findNavController(this, viewId) */
        check(
          "androidx.navigation.Navigation",
          "(Landroid.app.Activity;I)",
          "findNavController",
          "androidx.navigation",
          "Activity",
          "ActivityKt",
          argmap = "R0",
        )
        /* fun View.findNavController(): NavController = Navigation.findNavController(this) */
        check(
          "androidx.navigation.Navigation",
          "(Landroid.view.View;)",
          "findNavController",
          "androidx.navigation",
          "View",
          "ViewKt",
          argmap = "R",
        )
      }

      // properties (size()->size) and operator utilities (size() == 0 -> isEmpty())

      "size" -> {
        val containingClass = method.containingClass?.qualifiedName ?: return
        when (containingClass) {
          "androidx.collection.LongSparseArrayKt" -> {
            /* val <T> LongSparseArray<T>.size: Int get() = size() */
            check(
              "",
              "()",
              "size",
              "androidx.collection",
              "LongSparseArray",
              "LongSparseArrayKt",
              property = true,
            )
          }
          "android.util.LongSparseArray" -> {
            /* fun <T> LongSparseArray<T>.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.util",
              "LongSparseArray",
              "LongSparseArrayKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun <T> LongSparseArray<T>.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.util",
                "LongSparseArray",
                "LongSparseArrayKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val <T> LongSparseArray<T>.size: Int get() = size() */
              check(
                "",
                "()",
                "size",
                "androidx.core.util",
                "LongSparseArray",
                "LongSparseArrayKt",
                property = true,
              )
          }
          "android.util.SparseArray" -> {
            /* fun <T> SparseArray<T>.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.util",
              "SparseArray",
              "SparseArrayKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun <T> SparseArray<T>.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.util",
                "SparseArray",
                "SparseArrayKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val <T> SparseArray<T>.size: Int get() = size() */
              check(
                "",
                "()",
                "size",
                "androidx.core.util",
                "SparseArray",
                "SparseArrayKt",
                property = true,
              )
          }
          "android.util.SparseBooleanArray" -> {
            /* fun SparseBooleanArray.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.util",
              "SparseBooleanArray",
              "SparseBooleanArrayKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun SparseBooleanArray.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.util",
                "SparseBooleanArray",
                "SparseBooleanArrayKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val SparseBooleanArray.size: Int get() = size() */
              check(
                "",
                "()",
                "size",
                "androidx.core.util",
                "SparseBooleanArray",
                "SparseBooleanArrayKt",
                property = true,
              )
          }
          "android.util.SparseIntArray" -> {
            /* fun SparseIntArray.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.util",
              "SparseIntArray",
              "SparseIntArrayKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun SparseIntArray.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.util",
                "SparseIntArray",
                "SparseIntArrayKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val SparseIntArray.size: Int get() = size() */
              check(
                "",
                "()",
                "size",
                "androidx.core.util",
                "SparseIntArray",
                "SparseIntArrayKt",
                property = true,
              )
          }
          "android.util.SparseLongArray" -> {
            /* fun SparseLongArray.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.util",
              "SparseLongArray",
              "SparseLongArrayKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun SparseLongArray.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.util",
                "SparseLongArray",
                "SparseLongArrayKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val SparseLongArray.size: Int get() = size() */
              check(
                "",
                "()",
                "size",
                "androidx.core.util",
                "SparseLongArray",
                "SparseLongArrayKt",
                property = true,
              )
          }
          "android.view.Menu" -> {
            /* fun Menu.isEmpty(): Boolean = size() == 0 */
            check(
              "",
              "()",
              "isEmpty",
              "androidx.core.view",
              "Menu",
              "MenuKt",
              op = "==",
              rhs = "0",
            ) ||
              /* fun Menu.isNotEmpty(): Boolean = size() != 0 */
              check(
                "",
                "()",
                "isNotEmpty",
                "androidx.core.view",
                "Menu",
                "MenuKt",
                op = "!=",
                rhs = "0",
              ) ||
              /* val Menu.size: Int get() = size() */
              check("", "()", "size", "androidx.core.view", "Menu", "MenuKt", property = true)
          }
        }
      }
      "getChildCount" -> {
        /* fun ViewGroup.isEmpty(): Boolean = childCount == 0 */
        check(
          "android.view.ViewGroup",
          "()",
          "isEmpty",
          "androidx.core.view",
          "ViewGroup",
          "ViewGroupKt",
          op = "==",
          rhs = "0",
        ) ||
          /* fun ViewGroup.isNotEmpty(): Boolean = childCount != 0 */
          check(
            "android.view.ViewGroup",
            "()",
            "isNotEmpty",
            "androidx.core.view",
            "ViewGroup",
            "ViewGroupKt",
            op = "!=",
            rhs = "0",
          ) ||
          /* val ViewGroup.size: Int get() = childCount */
          check(
            "android.view.ViewGroup",
            "()",
            "size",
            "androidx.core.view",
            "ViewGroup",
            "ViewGroupKt",
            property = true,
          )
      }
      "indexOfChild" -> {
        /* fun ViewGroup.contains(view: View): Boolean = indexOfChild(view) != -1 */
        check(
          "android.view.ViewGroup",
          "(Landroid.view.View;)",
          "contains",
          "androidx.core.view",
          "ViewGroup",
          "ViewGroupKt",
          op = "!=",
          rhs = "-1",
          argmap = "0",
        )
      }
      "getVisibility" -> {
        /* val public inline var View.isVisible: Boolean get() = visibility == View.VISIBLE set(value) { visibility = if (value) View.VISIBLE else View.GONE } */
        check(
          "android.view.View",
          "()",
          "isVisible",
          "androidx.core.view",
          "View",
          "ViewKt",
          op = "==",
          rhs = "android.view.View.VISIBLE",
          property = true,
        ) ||
          /* val public inline var View.isInvisible: Boolean get() = visibility == View.INVISIBLE set(value) { visibility = if (value) View.INVISIBLE else View.VISIBLE } */
          check(
            "android.view.View",
            "()",
            "isInvisible",
            "androidx.core.view",
            "View",
            "ViewKt",
            op = "==",
            rhs = "android.view.View.INVISIBLE",
            property = true,
          ) ||
          /* val public inline var View.isGone: Boolean get() = visibility == View.GONE set(value) { visibility = if (value) View.GONE else View.VISIBLE } */
          check(
            "android.view.View",
            "()",
            "isGone",
            "androidx.core.view",
            "View",
            "ViewKt",
            op = "==",
            rhs = "android.view.View.GONE",
            property = true,
          )
      }
      "indexOfValue" -> {
        val containingClass = method.containingClass?.qualifiedName ?: return
        when (containingClass) {
          "android.util.LongSparseArray" -> {
            /* fun <T> LongSparseArray<T>.containsValue(value: T): Boolean = indexOfValue(value) >= 0 */
            check(
              "",
              "(Ljava.lang.Object;)",
              "containsValue",
              "androidx.core.util",
              "LongSparseArray",
              "LongSparseArrayKt",
              op = ">=",
              rhs = "0",
              argmap = "0",
            )
          }
          "android.util.SparseArray" -> {
            /* fun <T> SparseArray<T>.containsValue(value: T): Boolean = indexOfValue(value) >= 0 */
            check(
              "",
              "(Ljava.lang.Object;)",
              "containsValue",
              "androidx.core.util",
              "SparseArray",
              "SparseArrayKt",
              op = ">=",
              rhs = "0",
              argmap = "0",
            )
          }
          "android.util.SparseBooleanArray" -> {
            /* fun SparseBooleanArray.containsValue(value: Boolean): Boolean = indexOfValue(value) >= 0 */
            check(
              "",
              "(Z)",
              "containsValue",
              "androidx.core.util",
              "SparseBooleanArray",
              "SparseBooleanArrayKt",
              op = ">=",
              rhs = "0",
              argmap = "0",
            )
          }
          "android.util.SparseIntArray" -> {
            /* fun SparseIntArray.containsValue(value: Int): Boolean = indexOfValue(value) >= 0 */
            check(
              "",
              "(I)",
              "containsValue",
              "androidx.core.util",
              "SparseIntArray",
              "SparseIntArrayKt",
              op = ">=",
              rhs = "0",
              argmap = "0",
            )
          }
          "android.util.SparseLongArray" -> {
            /* fun SparseLongArray.containsValue(value: Long): Boolean = indexOfValue(value) >= 0 */
            check(
              "",
              "(J)",
              "containsValue",
              "androidx.core.util",
              "SparseLongArray",
              "SparseLongArrayKt",
              op = ">=",
              rhs = "0",
              argmap = "0",
            )
          }
        }
      }

      // infix methods (x.intersect(y) -> x and y)
      "intersect" -> {
        /* fun <T : Comparable<T>> Range<T>.and(other: Range<T>): Range<T> = intersect(other) */
        check(
          "android.util.Range",
          "(Landroid.util.Range;)",
          "and",
          "androidx.core.util",
          "Range",
          "RangeKt",
          argmap = "0",
        )
      }

      // array access methods (menu.getItem(x) -> menu[x])
      "getItem" -> {
        /* fun Menu.get(index: Int): MenuItem = getItem(index) */
        check(
          "android.view.Menu",
          "(I)",
          "get",
          "androidx.core.view",
          "Menu",
          "MenuKt",
          argmap = "0",
        )
      }
      "getPixel" -> {
        /* fun Bitmap.get(x: Int, y: Int): Int = getPixel(x, y) */
        check(
          "android.graphics.Bitmap",
          "(II)",
          "get",
          "androidx.core.graphics",
          "Bitmap",
          "BitmapKt",
          argmap = "01",
        )
      }
      "setPixel" -> {
        /* fun Bitmap.set(x: Int, y: Int, color: Int): Unit = setPixel(x, y, color) */
        check(
          "android.graphics.Bitmap",
          "(III)",
          "set",
          "androidx.core.graphics",
          "Bitmap",
          "BitmapKt",
          argmap = "012",
        )
      }
    }
  }

  companion object Issues {
    /** Max number of supported arguments in the replaced extension function */
    private const val MAX_ARGUMENTS = 10
    private val IMPLEMENTATION = Implementation(UseKtxDetector::class.java, Scope.JAVA_FILE_SCOPE)

    // Possible options: whether to remove defaults
    // Whether to preserve named parameters (we'll be reordering)

    val REMOVE_DEFAULTS =
      BooleanOption(
        "remove-defaults",
        "Whether to skip arguments that match the defaults provided by the extension",
        true,
        """
        Extensions often provide default values for some of the parameters. \
        For example:
        ```kotlin
        fun Path.readLines(charset: Charset = Charsets.UTF_8): List<String> { return Files.readAllLines(this, charset) }
        ```
        This lint check will by default automatically omit parameters that \
        match the default, so if your code was calling

        ```kotlin
        Files.readAllLines(file, Charset.UTF_8)
        ```
        lint would replace this with
        ```kotlin
        file.readLines()
        ```
        rather than

        ```kotlin
        file.readLines(Charset.UTF_8
        ```
        You can turn this behavior off using this option.
        """,
      )

    val REQUIRE_LIBRARY =
      BooleanOption(
        "require-present",
        "Whether to only offer extensions already available",
        true,
        """
        This option lets you only have lint suggest extension replacements if those \
        extensions are already available on the class path (in other words, you're already \
        depending on the library containing the extension method.)
        """,
      )

    /** Suggests equivalent but simpler KTX constructs. */
    @JvmField
    val USE_KTX =
      Issue.create(
          id = "UseKtx",
          briefDescription = "Use KTX extension function",
          explanation =
            """
          The Android KTX libraries decorates the Android platform SDK as well \
          as various libraries with more convenient extension functions available \
          from Kotlin, allowing you to use default parameters, named parameters, \
          and more.
          """,
          category = Category.PRODUCTIVITY, // Maybe MAD, or READABILITY, or SIMPLICITY, .... ?
          priority = 6,
          severity = Severity.WARNING,
          androidSpecific = true,
          implementation = IMPLEMENTATION,
        )
        .setOptions(listOf(REMOVE_DEFAULTS, REQUIRE_LIBRARY))

    private fun isArrayGetExtension(extensionMethod: String, descriptor: String): Boolean {
      if (extensionMethod == "get" || extensionMethod == "set") {
        val lastIndex =
          if (extensionMethod == "get") descriptor.length - 1 else descriptor.length - 2
        for (i in 1 until lastIndex) {
          if (descriptor[i] != 'I') {
            return false
          }
        }
        return true
      }

      return false
    }

    private fun isInfixExtension(extensionMethod: String): Boolean {
      /// (This is not a general purpose mechanism; it only includes infix methods
      // from our lookup tables)
      when (extensionMethod) {
        "and",
        "or" -> return true
        else -> return false
      }
    }
  }
}

fun KtCallExpression.callName(): String = this.calleeExpression?.text ?: ""

fun KtBinaryExpression.getOperatorText(): String {
  return (operationToken as? KtSingleValueToken)?.value ?: operationToken.toString()
}

fun KtBinaryExpression.isSameComparison(
  expectedOperator: String,
  expectedValue: String,
  methodName: String,
): Boolean {
  val value = right?.text ?: return false
  val operator = getOperatorText()

  if (operator == expectedOperator) {
    if (value.equalsIgnoringSpace(expectedValue)) {
      return true
    } else if (
      expectedValue.endsWith(value) && expectedValue[expectedValue.length - value.length - 1] == '.'
    ) {
      return true
    } else {
      // Type alias?
      val lastDot = expectedValue.lastIndexOf('.')
      val nameLength = expectedValue.length - (lastDot + 1)
      if (
        lastDot != -1 &&
          expectedValue.length - nameLength > 0 &&
          expectedValue.regionMatches(
            expectedValue.length - nameLength,
            value,
            value.length - nameLength,
            nameLength,
          )
      ) {
        val resolved = right.toUElement()?.tryResolve()
        if (resolved is PsiField) {
          val resolvedQualifiedName = "${resolved.containingClass?.qualifiedName}.${resolved.name}"
          if (resolvedQualifiedName == expectedValue) {
            return true
          }
        }
      }
    }
  }

  // For size comparisons, consider "> 0" the same as "!= 0" since nobody
  // thinks size can be negative (size, childCount, etc.)
  if (
    (methodName.contains("size", true) || methodName.contains("count", true)) &&
      expectedOperator == "!=" &&
      expectedValue == "0" &&
      operator == ">" &&
      value == "0"
  ) {
    return true
  }

  // For index comparisons, consider >= 0 and != -1 as the same.
  // (indexOfValue, indexOfKey, indexOfChild, indexOf, etc.)
  if (
    methodName.startsWith("index") &&
      (expectedOperator == ">=" &&
        expectedValue == "0" &&
        operator == "!=" &&
        value.equalsIgnoringSpace("-1")) ||
      (expectedOperator == "!=" && expectedValue == "-1" && operator == ">=" && value == "0")
  ) {
    return true
  }

  return false
}

fun PsiMethod?.isInClass(name: String?): Boolean {
  this ?: return false
  name ?: return false
  val qualifiedName = containingClass?.qualifiedName ?: return false

  return qualifiedName == name
}

private fun CharSequence.lineEnd(start: Int): Int {
  return this.indexOf('\n', start).let { if (it == -1) length else it }
}

private fun CharSequence.lineBegin(start: Int): Int {
  return this.lastIndexOf('\n', start).let { if (it == -1) 0 else it + 1 }
}

private fun CharSequence.isNotBlankAt(start: Int, end: Int): Boolean {
  return (start until end).any { !this[it].isWhitespace() }
}

/**
 * Returns true if two strings have identical characters while ignoring optional whitespaces -- e.g.
 * "foo.bar" and "foo . bar " are considered the same string.
 */
fun String.equalsIgnoringSpace(other: String): Boolean {
  var i = 0
  var j = 0
  val n = this.length
  val jn = other.length
  while (true) {
    while (i < n && this[i].isWhitespace()) i++
    while (j < jn && other[j].isWhitespace()) j++
    if (i == n) return j == jn
    else if (j == jn) return false else if (this[i] != other[j]) return false
    i++
    j++
  }
}

/**
 * Returns true if this sequence contains a given identifier (and not as part of another longer
 * identifier, e.g. it is surrounded by "word" boundaries)
 */
fun CharSequence.containsIdentifier(identifier: String): Boolean {
  var i = 0
  val n = length
  while (true) {
    i = indexOf(identifier, i)
    if (i == -1) {
      return false
    }
    if (
      (i == 0 || !this[i - 1].isJavaIdentifierPart()) &&
        (i + identifier.length == n || !this[i + identifier.length].isJavaIdentifierPart())
    ) {
      return true
    }
    i++
  }
}

fun KtExpression.skipParenthesizedExprDown(): KtExpression {
  var curr = this
  while (curr is KtParenthesizedExpression) {
    curr = curr.expression ?: return curr
  }
  return curr
}
