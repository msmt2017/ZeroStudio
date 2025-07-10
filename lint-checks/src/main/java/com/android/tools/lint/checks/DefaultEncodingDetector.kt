/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.tools.lint.client.api.TYPE_INT
import com.android.tools.lint.client.api.TYPE_STRING
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Incident
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.LocationType
import com.android.tools.lint.detector.api.Platform.Companion.JDK_SET
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.isKotlin
import com.android.tools.lint.detector.api.notAndroidProject
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiMethod
import java.util.Locale
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UParenthesizedExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.evaluateString
import org.jetbrains.uast.skipParenthesizedExprDown

/** Detects accidental usages of default platform encoding. */
class DefaultEncodingDetector : Detector(), SourceCodeScanner {

  companion object Issues {
    private val IMPLEMENTATION =
      Implementation(DefaultEncodingDetector::class.java, Scope.JAVA_FILE_SCOPE)

    @JvmField
    val ISSUE =
      Issue.create(
        id = "DefaultEncoding",
        briefDescription = "Using Default Character Encoding",
        explanation =
          """
                Some APIs will implicitly use the default system character encoding \
                instead of UTF-8 when converting to or from bytes, such as when creating \
                a default `FileReader`.

                This is *usually* not correct; you only want to do this if you need to read \
                files created by other programs where they have deliberately written in the \
                same encoding. The default encoding varies from platform to platform and can \
                vary from locale to locale, so this makes it difficult to interpret files \
                containing non-ASCII characters.

                We recommend using UTF-8 everywhere.

                Note that on Android, the default file encoding is always UTF-8 (see \
                https://developer.android.com/reference/java/nio/charset/Charset#defaultCharset() \
                for more), so this lint check deliberately does not flag any problems in \
                Android code, since it is always safe to rely on the default character \
                encoding there.
                """,
        category = Category.CORRECTNESS,
        severity = Severity.ERROR,
        platforms = JDK_SET, // does not apply for Android, where the charset is always UTF-8
        implementation = IMPLEMENTATION,
        enabledByDefault = false,
      )

    private const val JAVA_IO_INPUT_STREAM_READER = "java.io.InputStreamReader"
    private const val JAVA_IO_FILE_INPUT_STREAM = "java.io.FileInputStream"
    private const val JAVA_IO_FILE_OUTPUT_STREAM = "java.io.FileOutputStream"
    private const val JAVA_IO_OUTPUT_STREAM_WRITER = "java.io.OutputStreamWriter"
    private const val JAVA_IO_INPUT_STREAM = "java.io.InputStream"
    private const val JAVA_IO_FILE_READER = "java.io.FileReader"
    private const val JAVA_UTIL_SCANNER = "java.util.Scanner"
    private const val JAVA_NIO_CHANNELS_READABLE_BYTE_CHANNEL =
      "java.nio.channels.ReadableByteChannel"
    private const val JAVA_IO_FILE = "java.io.File"
    private const val JAVA_NIO_FILE_PATH = "java.nio.file.Path"
    private const val JAVA_IO_OUTPUT_STREAM = "java.io.OutputStream"
    private const val JAVA_IO_PRINT_WRITER = "java.io.PrintWriter"
    private const val JAVA_IO_FILE_WRITER = "java.io.FileWriter"
    private const val JAVA_IO_FILE_DESCRIPTOR = "java.io.FileDescriptor"
    private const val JAVA_NIO_CHARSET_CHARSET = "java.nio.charset.Charset"
    private const val JAVA_IO_BYTE_ARRAY_OUTPUT_STREAM = "java.io.ByteArrayOutputStream"

    private const val JAVA_UTF8_CHARSET = "java.nio.charset.StandardCharsets.UTF_8"
    private const val KOTLIN_UTF8_CHARSET = "kotlin.text.Charsets.UTF_8"
    private const val DEFAULT_CHARSET =
      "java.nio.charset.Charset.defaultCharset()" // Same for both Java and Kotlin
    private const val TYPE_BYTE_ARRAY = "byte[]"
  }

  override fun getApplicableMethodNames(): List<String> = listOf("getBytes", "toString")

  override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
    val name = method.name
    val evaluator = context.evaluator
    if (
      name == "toString" &&
        node.valueArgumentCount == 0 &&
        evaluator.isMemberInClass(method, JAVA_IO_BYTE_ARRAY_OUTPUT_STREAM)
    ) {
      report(context, node, method, JAVA_IO_BYTE_ARRAY_OUTPUT_STREAM)
    } else if (
      name == "getBytes" && evaluator.isMemberInClass(method, TYPE_STRING) && !haveCharset(node, 0)
    ) {
      val constant = node.receiver?.evaluateString()
      if (constant != null && constant.all { it.code <= 128 }) {
        return
      }
      report(context, node, method, TYPE_STRING)
    }
  }

  override fun getApplicableConstructorTypes(): List<String> =
    listOf(
      TYPE_STRING, // Not kotlin.String, which defaults to UTF-8
      JAVA_IO_FILE_READER,
      JAVA_IO_FILE_WRITER,
      JAVA_IO_PRINT_WRITER,
      JAVA_IO_INPUT_STREAM_READER,
      JAVA_IO_OUTPUT_STREAM_WRITER,
      JAVA_UTIL_SCANNER,
    )

  private fun haveCharset(call: UCallExpression, firstStringCharsetIndex: Int = 1): Boolean {
    val valueArguments = call.valueArguments
    for (i in valueArguments.indices) {
      val argument = valueArguments[i]
      val type = argument.getExpressionType()?.canonicalText ?: ""
      if (type.startsWith(JAVA_NIO_CHARSET_CHARSET)) { // includes CharsetEncoder and CharsetDecoder
        return true
      }
      if (i >= firstStringCharsetIndex && type == TYPE_STRING) {
        // Charset provided by name; we only allow this in second or later arguments
        // (FileReader/FileWriter/PrintWriter takes path as a string in argument 0,
        //  Scanner takes a source).
        return true
      }

      if (argument.sourcePsi?.text?.contains("Charset") == true) {
        error("Unexpected")
      }
    }

    return false
  }

  override fun visitConstructor(
    context: JavaContext,
    node: UCallExpression,
    constructor: PsiMethod,
  ) {
    val qualifiedName = constructor.containingClass?.qualifiedName ?: return
    val evaluator = context.evaluator
    when (qualifiedName) {
      TYPE_STRING -> {
        val expressionType = node.valueArguments.firstOrNull()?.getExpressionType() ?: return
        val canonicalText = expressionType.canonicalText
        if (canonicalText == TYPE_BYTE_ARRAY && !haveCharset(node)) {
          report(context, node, constructor, qualifiedName)
        }
      }
      JAVA_IO_FILE_READER,
      JAVA_IO_FILE_WRITER -> {
        if (!haveCharset(node)) {
          if (
            node.valueArgumentCount == 1 &&
              node.valueArguments[0].getExpressionType()?.canonicalText == JAVA_IO_FILE_DESCRIPTOR
          ) {
            return
          }
          report(context, node, constructor, qualifiedName)
        }
      }
      JAVA_IO_PRINT_WRITER -> {
        val expressionType = node.valueArguments.firstOrNull()?.getExpressionType() ?: return
        val canonicalText = expressionType.canonicalText
        // PrintWriter(String)
        // PrintWriter(String, String)
        // PrintWriter(String, Charset)
        if (
          (canonicalText == TYPE_STRING ||
            // PrintWriter(File)
            // PrintWriter(File, String)
            // PrintWriter(File, Charset)
            canonicalText == JAVA_IO_FILE ||
            // PrintWriter(OutputStream)
            // PrintWriter(OutputStream, boolean)
            // PrintWriter(OutputStream, Charset)
            canonicalText.endsWith("Stream") ||
            evaluator.extendsClass(
              evaluator.getTypeClass(expressionType),
              JAVA_IO_OUTPUT_STREAM,
            )) && !haveCharset(node)
        ) {
          report(context, node, constructor, qualifiedName)
        }
      }
      JAVA_IO_INPUT_STREAM_READER,
      JAVA_IO_OUTPUT_STREAM_WRITER -> {
        if (!haveCharset(node)) {
          report(context, node, constructor, qualifiedName)
        }
      }
      JAVA_UTIL_SCANNER -> {
        when (constructor.parameterList.parameters.firstOrNull()?.type?.canonicalText ?: "") {
          JAVA_NIO_CHANNELS_READABLE_BYTE_CHANNEL,
          JAVA_IO_INPUT_STREAM,
          JAVA_IO_FILE,
          JAVA_NIO_FILE_PATH -> {
            if (!haveCharset(node)) {
              report(context, node, constructor, qualifiedName)
            }
          }
        // Other scanner constructor types such as String and Readable are safe
        }
      }
    }
  }

  private fun report(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    qualifiedName: String,
  ) {
    val charset = if (isKotlin(node.lang)) "Charsets.UTF_8" else "StandardCharsets.UTF_8"
    val typeName = qualifiedName.substringAfterLast('.')

    val message =
      when {
        qualifiedName == TYPE_STRING ->
          "This string will be interpreted with the default system encoding instead of a " +
            "specific charset which is usually a mistake"
        typeName.startsWith("File") ->
          "This file will be ${
                if (typeName.endsWith("Writer")) "written" else "read"
                } with the default system encoding " +
            "instead of a specific charset which is usually a mistake"
        qualifiedName == JAVA_IO_BYTE_ARRAY_OUTPUT_STREAM ->
          "This string will be decoded with the default system encoding instead of a " +
            "specific charset which is usually a mistake"
        else ->
          "This `$typeName` will use the default system encoding instead of a " +
            "specific charset which is usually a mistake"
      }

    val fix = createFix(context, node, method, qualifiedName)
    val fixSuggestion =
      if (fix != null) {
        if (fix is LintFix.LintFixGroup && fix.type == LintFix.GroupType.ALTERNATIVES) {
          fix.fixes[0].getDisplayName()
        } else {
          fix.getDisplayName()
        }
      } else {
        "add `$charset`"
      }
    val incident =
      Incident(
        ISSUE,
        context.getLocation(node),
        "$message; ${fixSuggestion?.replaceFirstChar { it.lowercase(Locale.US) }}?",
      )
    incident.fix(fix)
    context.report(incident, notAndroidProject())
  }

  private fun createFix(
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    qualifiedName: String,
  ): LintFix? {
    val fixes = mutableListOf<LintFix>()
    addFix(fixes, context, node, method, qualifiedName)
    return if (fixes.size > 1) {
      fix().alternatives(*fixes.toTypedArray())
    } else {
      fixes.firstOrNull()
    }
  }

  private fun addFix(
    fixes: MutableList<LintFix>,
    context: JavaContext,
    node: UCallExpression,
    method: PsiMethod,
    qualifiedName: String,
  ) {
    val isKotlin = isKotlin(node.lang)
    val name = method.name
    val call = node.methodIdentifier?.name ?: method.name
    val evaluator = context.evaluator

    when (qualifiedName) {
      TYPE_STRING -> {
        if (name == "getBytes") {
          // Java: String.getBytes() => String.getBytes(charset)
          // Kotlin: String.getBytes()/String.bytes => String.encodeToByteArray /
          // String.toByteArray(charset)
          if (isKotlin) {
            val pattern =
              if (node.methodIdentifier?.name == "bytes") "bytes" else """getBytes\s*\(\s*\)"""
            fixes.add(
              fix()
                .name("Replace with `encodeToByteArray()`")
                .replace()
                .pattern(pattern)
                .with("encodeToByteArray()")
                .shortenNames()
                .build()
            )
            fixes.add(
              fix()
                .name("Replace with `toByteArray(UTF_8)`")
                .replace()
                .pattern(pattern)
                .with("toByteArray($KOTLIN_UTF8_CHARSET)")
                .shortenNames()
                .build()
            )
            fixes.add(
              fix()
                .name("Replace with `toByteArray(defaultCharset())`")
                .replace()
                .pattern(pattern)
                .with("toByteArray($DEFAULT_CHARSET)")
                .shortenNames()
                .build()
            )
          } else {
            val oldPattern = """\(\s*\)"""
            fixes.add(
              fix()
                .name("Add charset argument, `getBytes(UTF_8)`")
                .replace()
                .pattern(oldPattern)
                .with("($JAVA_UTF8_CHARSET)")
                .shortenNames()
                .build()
            )
            fixes.add(
              fix()
                .name("Add charset argument, `getBytes(defaultCharset())`")
                .replace()
                .pattern(oldPattern)
                .with("($DEFAULT_CHARSET)")
                .shortenNames()
                .build()
            )
          }
        } else {
          // new String(byteArray)
          // Java: new String(byte[]) => new String(byte[], charset)
          // Java: new String(byte[], int, int) => new String(byte[], int, int, charset)
          // Kotlin: java.lang.String(ByteArray) (already using UTF-8), String(ByteArray, CharSet)
          // Can only fix the two signatures that take an extra charset:
          if (
            evaluator.parametersMatch(method, TYPE_BYTE_ARRAY) ||
              evaluator.parametersMatch(method, TYPE_BYTE_ARRAY, TYPE_INT, TYPE_INT)
          ) {
            addCharsetArgFixes(context, fixes, isKotlin, call)
          }
        }
      }
      JAVA_IO_INPUT_STREAM_READER,
      JAVA_IO_OUTPUT_STREAM_WRITER,
      JAVA_IO_FILE_READER,
      JAVA_IO_FILE_WRITER -> {
        // Kotlin: FileReader(f) => f.reader()/(charset) (or optionally bufferedReader too, probably
        // better)
        // Kotlin: BufferedReader(FileReader(f)) => f.bufferedReader()/(charset)
        // Java: new FileReader(f) => new InputStreamReader(new FileInputStream(f), charset)
        // Java11: new FileReader(f) => new FileReader(f, charset)
        //
        // Kotlin: FileWriter(f) => f.writer()/(charset) (or optionally bufferedWriter too, probably
        // better)
        // Kotlin: BufferedWriter(FileWriter(f)) => f.bufferedWriter()/(charset)
        // Java: new FileWriter(f) => new OutputStreamWriter(new FileOutputStream(f), charset)
        // Java11: new FileWriter(f) => new FileWriter(f, charset)
        val fileBased = qualifiedName == JAVA_IO_FILE_READER || qualifiedName == JAVA_IO_FILE_WRITER
        if (fileBased && !evaluator.parameterHasType(method, 0, JAVA_IO_FILE)) {
          return
        }
        val reading =
          qualifiedName == JAVA_IO_FILE_READER || qualifiedName == JAVA_IO_INPUT_STREAM_READER
        if (isKotlin) {
          val outer = getOuterBufferWrapper(node)
          val rangeNode = outer ?: node
          val range = context.getLocation(rangeNode, LocationType.ALL)
          val replacement =
            if (outer != null) {
              if (reading) "bufferedReader" else "bufferedWriter"
            } else if (reading) {
              "reader"
            } else {
              "writer"
            }
          val arg = node.valueArguments.singleOrNull()?.skipParenthesizedExprDown()
          val receiver = if (arg is USimpleNameReferenceExpression) "${arg.identifier}." else ""
          val oldPattern = """(.*$call\s*\(\s*([^)]+)\s*\).*)"""
          fixes.add(
            fix()
              .name("Replace with `$receiver$replacement()` (uses UTF-8)")
              .replace()
              .pattern(oldPattern)
              .with("\\k<2>.$replacement()")
              .shortenNames()
              .range(range)
              .build()
          )
          fixes.add(
            fix()
              .name("Replace with `$receiver$replacement(defaultCharset())`")
              .replace()
              .pattern(oldPattern)
              .with("\\k<2>.$replacement($JAVA_UTF8_CHARSET)")
              .shortenNames()
              .range(range)
              .build()
          )
        } else {
          if (context.project.javaLanguageLevel.isAtLeast(LanguageLevel.JDK_11)) {
            addCharsetArgFixes(context, fixes, false, call, LanguageLevel.JDK_11)
          } else if (fileBased) {
            val replacement =
              if (reading)
                "new $JAVA_IO_INPUT_STREAM_READER(new $JAVA_IO_FILE_INPUT_STREAM(\\k<2>), %1\$s)"
              else
                "new $JAVA_IO_OUTPUT_STREAM_WRITER(new $JAVA_IO_FILE_OUTPUT_STREAM(\\k<2>), %1\$s)"
            val range = context.getLocation(node, LocationType.ALL)
            val oldPattern = """(.*\(\s*([^)]+)\s*\).*)"""
            val summary =
              if (reading) "InputStreamReader(FileInputStream("
              else "OutputStreamWriter(FileOutputStream(..., "
            fixes.add(
              fix()
                .name("Replace with `$summary..., UTF8)`")
                .replace()
                .pattern(oldPattern)
                .with(String.format(replacement, JAVA_UTF8_CHARSET))
                .shortenNames()
                .range(range)
                .build()
            )
            fixes.add(
              fix()
                .name("Replace with `$summary..., defaultCharset())`")
                .replace()
                .pattern(oldPattern)
                .with(String.format(replacement, DEFAULT_CHARSET))
                .shortenNames()
                .range(range)
                .build()
            )
          }
        }
      }
      JAVA_UTIL_SCANNER,
      JAVA_IO_PRINT_WRITER -> {
        // Java/Kotlin: PrintWriter(x) => PrintWriter(x, charset)
        // Java/Kotlin: Scanner(file) => Scanner(file, charset)
        // Java/Kotlin: Scanner(path) => Scanner(path, charset)
        // Java/Kotlin: Scanner(inputStream) => Scanner(inputStream, charset)
        // Java/Kotlin: Scanner(ReadableByteChannel) => Scanner(ReadableByteChannel, charset)
        // Consider supporting on older Java levels:
        // Java: new PrintWriter(os, auto) => new PrintWriter(new OutputStreamWriter(os, charset),
        // auto)
        // Kotlin: PrintWriter(os, auto) => PrintWriter(OutputStreamWriter(os, charset).buffered(),
        // auto)

        addCharsetArgFixes(context, fixes, isKotlin, call, LanguageLevel.JDK_10)
      }
    }
  }

  // Creates simple fixes which just appends the charset as the last parameter in the call
  private fun addCharsetArgFixes(
    context: JavaContext,
    fixes: MutableList<LintFix>,
    isKotlin: Boolean,
    name: String,
    languageLevel: LanguageLevel = LanguageLevel.JDK_1_7,
  ) {
    // Just add a Charset
    if (context.project.javaLanguageLevel.isAtLeast(languageLevel)) {
      val utf8 = if (isKotlin) KOTLIN_UTF8_CHARSET else JAVA_UTF8_CHARSET
      val pattern = """\(\s*(.*)\)"""
      fixes.add(
        fix()
          .name("Add charset argument, `$name(..., UTF_8)`")
          .replace()
          .pattern(pattern)
          .with("\\k<1>, $utf8")
          .shortenNames()
          .build()
      )
      fixes.add(
        fix()
          .name("Add charset argument, `$name(..., defaultCharset())`")
          .replace()
          .pattern(pattern)
          .with("\\k<1>, $DEFAULT_CHARSET")
          .shortenNames()
          .build()
      )
    }
  }

  // For a node "x", if we have BufferedWriter(x) or java.io.BufferedWriter(x), returns
  // that outer BufferedWriter call, otherwise null
  private fun getOuterBufferWrapper(node: UCallExpression): UExpression? {
    var curr = node.uastParent
    if (curr is UQualifiedReferenceExpression) {
      curr = curr.uastParent
    }
    while (curr != null) {
      if (curr is UQualifiedReferenceExpression) {
        val selector = curr.selector
        if (selector is UCallExpression && isBufferWrapper(selector.resolve())) {
          return curr
        }
        return null
      } else if (curr is UCallExpression) {
        if (isBufferWrapper(curr.resolve())) {
          return curr
        }
        return null
      } else if (curr !is UParenthesizedExpression) {
        break
      }
      curr = curr.uastParent
    }
    return null
  }

  private fun isBufferWrapper(method: PsiMethod?): Boolean {
    val containingClass = method?.containingClass?.qualifiedName ?: return false
    return containingClass.startsWith("java.io.Buffered")
  }
}
