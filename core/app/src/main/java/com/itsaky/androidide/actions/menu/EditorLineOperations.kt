package com.itsaky.androidide.actions.menu

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.R
import com.itsaky.androidide.actions.menu.api.CodeSnippet
import com.itsaky.androidide.actions.menu.api.CodeSymbol
import com.itsaky.androidide.editor.language.LanguageAnalysisBridge
import com.itsaky.androidide.editor.language.treesitter.TreeSitterLanguage
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.editor.utils.*
import com.itsaky.androidide.formatprovider.*
import com.itsaky.androidide.formatprovider.treesitter.*
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.treesitter.*
// import com.itsaky.androidide.editor.treesitter.*
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import java.io.File
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min
import android.widget.BaseAdapter
import android.widget.ImageView
import com.itsaky.androidide.actions.menu.codeoutline.*

private fun TSNode.getText(content: Content): CharSequence = content.subSequence(this.startByte / 2, this.endByte / 2)

/**
 * 中文注释: EditorLineOperations 对象包含了所有文本编辑器中行操作、导航、高级选择、多光标、格式化、代码片段和代码大纲的逻辑。
 * English annotation: The EditorLineOperations object contains the logic for all line operations, navigation, advanced selection, multiple cursors, formatting, code snippets, and code outline in the text editor.
 */
object EditorLineOperations {

    private const val PREFS_NAME = "EditorSettings"
    private const val KEY_READ_ONLY = "read_only_mode"


    private const val JAVA_SWITCH_QUERY = """(switch_expression) @switch"""
    private const val KOTLIN_WHEN_QUERY = """(when_expression) @switch"""
    private const val JAVA_IF_QUERY = """(if_statement) @if"""
    private const val KOTLIN_IF_QUERY = """(if_expression) @if"""

    // Helper extension functions to fix TreeSitter errors
    private fun TSTree.executeQuery(language: TSLanguage, query: String): List<TSQueryMatch> {
        return try {
            val tsQuery = TSQuery.create(language, query)
            val matches = mutableListOf<TSQueryMatch>()
            TSQueryCursor.create().use {
                it.exec(tsQuery, this.rootNode)
                var match = it.nextMatch()
                while(match != null) {
                    matches.add(match)
                    match = it.nextMatch()
                }
            }
            tsQuery.close()
            matches
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun TSTree.findSmallestNodeContaining(startByte: Int, endByte: Int, predicate: ((TSNode) -> Boolean)? = null): TSNode? {
        var smallest: TSNode? = null
        fun search(node: TSNode) {
            if (node.startByte <= startByte && node.endByte >= endByte) {
                if (predicate == null || predicate(node)) {
                    val currentSmallest = smallest
                    if (currentSmallest == null || (node.endByte - node.startByte < currentSmallest.endByte - currentSmallest.startByte)) {
                        smallest = node
                    }
                }
                for (i in 0 until node.childCount) {
                    search(node.getChild(i))
                }
            }
        }
        search(this.rootNode)
        return smallest
    }

    private fun TSNode.toLspRange(content: Content): Range {
        val start = content.getCharPositionForByte(this.startByte)
        val end = content.getCharPositionForByte(this.endByte)
        return Range(Position(start.line, start.column), Position(end.line, end.column))
    }

    private fun io.github.rosemoe.sora.text.TextRange.toLspRange(): Range {
        return Range(Position(this.start.line, this.start.column), Position(this.end.line, this.end.column))
    }

    private fun IDEEditor.getCursorBytePosition(): Int {
        return this.text.getCharIndex(this.cursor.leftLine, this.cursor.leftColumn) * 2
    }

    internal fun getLanguage(editor: IDEEditor): String? {
        return editor.file?.extension?.lowercase()?.let {
            if (it == "kts") "kt" else it
        }
    }


    /**
     * 中文注释: 用于管理光标导航历史的单例对象，以实现“上一个/下一个位置”功能。
     */
    val navigationHistory = NavigationHistory()

    @Suppress("UNUSED_PARAMETER")
    fun copyLine(editor: CodeEditor, context: Context): Boolean {
        val cursor = editor.cursor
        if (cursor.isSelected) {
            editor.copyText()
        } else {
            val i = cursor.leftLine
            editor.setSelectionRegion(i, 0, i, editor.text.getColumnCount(i))
            editor.copyText(false)
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun cutLine(editor: CodeEditor, context: Context): Boolean {
        editor.cutLine()
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun deleteLine(editor: CodeEditor, context: Context): Boolean {
        val cursor = editor.cursor
        if (cursor.isSelected) {
            editor.deleteText()
        } else {
            val currentLine = cursor.leftLine
            val nextLine = currentLine + 1
            if (nextLine == editor.lineCount) {
                editor.setSelectionRegion(currentLine, 0, currentLine, editor.text.getColumnCount(currentLine))
            } else {
                editor.setSelectionRegion(currentLine, 0, nextLine, 0)
            }
            editor.deleteText()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun emptyLine(editor: CodeEditor, context: Context): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        val startLine = cursor.leftLine
        val endLine = if (cursor.isSelected) cursor.rightLine else startLine
        content.beginBatchEdit()
        try {
            for (line in endLine downTo startLine) {
                val lineEnd = content.getColumnCount(line)
                if (lineEnd > 0) {
                    content.delete(line, 0, line, lineEnd)
                }
            }
        } finally {
            content.endBatchEdit()
        }
        editor.setSelection(startLine, 0)
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun replaceLine(editor: CodeEditor, context: Context): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        content.beginBatchEdit()
        try {
            if (cursor.isSelected) {
                val startLine = cursor.leftLine
                val endLine = cursor.rightLine
                val endCol = content.getColumnCount(endLine)
                content.delete(startLine, 0, endLine, endCol)
                editor.pasteText()
                editor.setSelection(startLine, 0)
            } else {
                val line = cursor.leftLine
                val lineLength = content.getColumnCount(line)
                content.delete(line, 0, line, lineLength)
                editor.pasteText()
                editor.setSelection(line, 0)
            }
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun duplicateLine(editor: CodeEditor, context: Context): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        content.beginBatchEdit()
        try {
            if (cursor.isSelected) {
                val startLine = cursor.leftLine
                val endLine = cursor.rightLine
                val duplicateContent = StringBuilder("\n")
                for (i in startLine..endLine) {
                    duplicateContent.append(content.getLineString(i)).append(if (i < endLine) "\n" else "")
                }
                content.insert(endLine, content.getColumnCount(endLine), duplicateContent.toString())
                val newStartLine = endLine + 1
                val newEndLine = endLine + 1 + (endLine - startLine)
                editor.setSelectionRegion(newStartLine, 0, newEndLine, content.getColumnCount(newEndLine))
            } else {
                val line = cursor.leftLine
                val column = cursor.leftColumn
                val lineContent = content.getLineString(line)
                content.insert(line, content.getColumnCount(line), "\n" + lineContent)
                editor.setSelection(line + 1, column)
            }
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun convertUpperLowerCase(editor: CodeEditor, context: Context, toUpper: Boolean): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        if (!cursor.isSelected) {
            return false
        }
        content.beginBatchEdit()
        try {
            val startLine = cursor.leftLine
            val startCol = cursor.leftColumn
            val endLine = cursor.rightLine
            val endCol = cursor.rightColumn
            val selectedText = content.subContent(startLine, startCol, endLine, endCol).toString()
            val convertedText = if (toUpper) selectedText.uppercase() else selectedText.lowercase()
            content.replace(startLine, startCol, endLine, endCol, convertedText)
            editor.setSelectionRegion(startLine, startCol, endLine, startCol + convertedText.length)
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun increaseIndent(editor: CodeEditor, context: Context): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        val tabWidth = editor.tabWidth
        val tabSpaces = " ".repeat(tabWidth)
        content.beginBatchEdit()
        try {
            if (cursor.isSelected) {
                val startLine = cursor.leftLine
                val endLine = cursor.rightLine
                for (line in startLine..endLine) {
                    content.insert(line, 0, tabSpaces)
                }
            } else {
                val line = cursor.leftLine
                val column = cursor.leftColumn
                content.insert(line, column, tabSpaces)
                editor.setSelection(line, column + tabWidth)
            }
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    @Suppress("UNUSED_PARAMETER")
    fun decreaseIndent(editor: CodeEditor, context: Context): Boolean {
        val content = editor.text
        val cursor = editor.cursor
        val tabWidth = editor.tabWidth
        content.beginBatchEdit()
        try {
            val startLine = if (cursor.isSelected) cursor.leftLine else cursor.leftLine
            val endLine = if (cursor.isSelected) cursor.rightLine else cursor.leftLine
            for (line in startLine..endLine) {
                val lineText = content.getLineString(line)
                var spacesToRemove = 0
                while (spacesToRemove < lineText.length && lineText[spacesToRemove] == ' ') {
                    spacesToRemove++
                }
                if (spacesToRemove > 0) {
                    val removeCount = if (spacesToRemove >= tabWidth) tabWidth else spacesToRemove
                    content.delete(line, 0, line, removeCount)
                }
            }
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    fun jumpToLine(editor: CodeEditor, context: Context): Boolean {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_jump_to_line, null as ViewGroup?)
        val textInputLayout = view.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = view.findViewById<EditText>(R.id.editText)
        val hint = "Line number [1-${editor.lineCount}]"
        textInputLayout.hint = hint
        val builder = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.jump_to_line_title))
            .setView(view as View)
            .setPositiveButton(context.getString(R.string.ok_button)) { _, _ -> } // Handled manually
            .setNegativeButton(context.getString(R.string.cancel_button), null)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.rounded_dialog_background))
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val input = editText.text.toString()
            if (input.isEmpty()) {
                textInputLayout.error = context.getString(R.string.enter_something_error)
            } else {
                try {
                    val lineNumber = input.toInt()
                    if (lineNumber in 1..editor.lineCount) {
                        editor.jumpToLine(lineNumber - 1)
                        dialog.dismiss()
                    } else {
                        textInputLayout.error = context.getString(R.string.value_out_of_range_error)
                    }
                } catch (e: NumberFormatException) {
                    textInputLayout.error = context.getString(R.string.invalid_number_error)
                } catch (e: Exception) {
                    textInputLayout.error = e.message
                }
            }
        }
        return true
    }

    private sealed class CommentStyle {
        data class Line(val prefix: String) : CommentStyle()
        data class Block(val prefix: String, val suffix: String) : CommentStyle()
    }

    private fun getCommentStyle(file: File): CommentStyle? {
        return when (file.extension.lowercase()) {
            "c", "cpp", "h", "hpp", "cc", "cxx", "hh", "java", "kt", "kts", "groovy", "gvy", "gy", "gsh", "scala", "sc", "cs", "fs", "fsi", "fsx", "js", "mjs", "cjs", "jsx", "ts", "mts", "cts", "tsx", "php", "phtml", "go", "swift", "rs", "d", "vala", "cr", "dart", "zig", "glsl", "vert", "frag", "tesc", "tese", "geom", "comp", "adoc", "asciidoc", "pas", "p", "pp", "angelscript", "as", "ceylon" -> CommentStyle.Line("// ")
            "py", "pyw", "pyc", "pyd", "rpy", "rb", "rbw", "rake", "ru", "pl", "pm", "t", "pod", "sh", "bash", "zsh", "fish", "ksh", "csh", "ash", "ps1", "psm1", "psd1", "tcl", "el", "conf", "cfg", "ini", "desktop", "properties", "toml", "yml", "yaml", "tf", "tfvars", "hcl", "nginx.conf", "conf.d", "apache.conf", "httpd.conf", "prefs", "gitignore", "gitconfig", "gitattributes", "gitmodules", "dockerfile", "containerfile", "makefile", "mk", "mak", "cmake", "cmakelists.txt", "scons","smali",  "r", "rscript" -> CommentStyle.Line("# ")
            "sql", "ddl", "dml", "tsql", "pls", "pck", "pks", "pkb", "lua", "hs", "lhs", "ada", "adb", "ads" -> CommentStyle.Line("-- ")
            "lisp", "cl", "clisp", "lsp", "scm", "ss", "rkt", "asm", "s", "inc" -> CommentStyle.Line("; ")
            "vb", "vbs" -> CommentStyle.Line("' ")
            "m", "matlab" -> CommentStyle.Line("% ")
            "f", "f77", "f90", "f95", "for" -> CommentStyle.Line("! ")
            "prolog", "plg" -> CommentStyle.Line("% ")
            "erl", "hrl" -> CommentStyle.Line("% ")
            "vim", "vimrc", "gvimrc" -> CommentStyle.Line("\" ")
            "rst", "rest" -> CommentStyle.Line(".. ")
            "xml", "html", "xhtml", "htm", "svg", "astro", "axml", "xsl", "xslt", "xsd", "wsdl", "plist", "jsp", "asp", "aspx", "cshtml", "svelte", "md", "markdown", "mdx", "vue" -> CommentStyle.Block("<!-- ", " -->")
            "css", "scss", "sass", "less", "jsonc" -> CommentStyle.Block("/* ", " */")
            "ml", "mli" -> CommentStyle.Block("(* ", " *)")
            "mustache", "hbs" -> CommentStyle.Block("{{! ", " }}")
            "pug", "jade" -> CommentStyle.Line("//- ")
            "jinja", "jinja2", "j2", "twig" -> CommentStyle.Block("{# ", " #}")
            else -> null
        }
    }

    fun toggleComment(editor: CodeEditor, file: File): Boolean {
        val style = getCommentStyle(file) ?: return false
        editor.text.beginBatchEdit()
        try {
            when (style) {
                is CommentStyle.Line -> handleLineComment(editor, style.prefix)
                is CommentStyle.Block -> handleBlockComment(editor, style.prefix, style.suffix)
            }
        } finally {
            editor.text.endBatchEdit()
        }
        return true
    }

    private fun handleLineComment(editor: CodeEditor, prefix: String) {
        val cursor = editor.cursor
        val text = editor.text
        if (cursor.isSelected) {
            val startLine = cursor.leftLine
            val endLine = cursor.rightLine
            var allCommented = true
            for (line in startLine..endLine) {
                if (text.getLineString(line).isNotBlank() && !text.getLineString(line).trimStart().startsWith(prefix)) {
                    allCommented = false
                    break
                }
            }
            for (line in startLine..endLine) {
                if (text.getLineString(line).isBlank()) continue
                val firstCharPos = getFirstNonWhitespace(text.getLineString(line))
                if (allCommented) {
                    if (text.getLineString(line).substring(firstCharPos).startsWith(prefix)) {
                        text.delete(line, firstCharPos, line, firstCharPos + prefix.length)
                    }
                } else {
                    if (!text.getLineString(line).substring(firstCharPos).startsWith(prefix)) {
                        text.insert(line, firstCharPos, prefix)
                    }
                }
            }
            editor.setSelectionRegion(startLine, 0, endLine, text.getColumnCount(endLine))
        } else {
            val line = cursor.leftLine
            val lineStr = text.getLineString(line)
            val firstCharPos = getFirstNonWhitespace(lineStr)
            if (firstCharPos < lineStr.length) {
                if (lineStr.substring(firstCharPos).startsWith(prefix)) {
                    text.delete(line, firstCharPos, line, firstCharPos + prefix.length)
                } else {
                    text.insert(line, firstCharPos, prefix)
                }
            }
        }
    }

    private fun handleBlockComment(editor: CodeEditor, prefix: String, suffix: String) {
        val cursor = editor.cursor
        val text = editor.text
        if (!cursor.isSelected) {
            val line = cursor.leftLine
            editor.setSelectionRegion(line, 0, line, text.getColumnCount(line))
        }
        val start = cursor.left()
        val end = cursor.right()
        val selectedText = text.subContent(start.line, start.column, end.line, end.column).toString()
        val trimmedText = selectedText.trim()
        val trimmedPrefix = prefix.trim()
        val trimmedSuffix = suffix.trim()

        if (trimmedText.startsWith(trimmedPrefix) && trimmedText.endsWith(trimmedSuffix)) {
            val startIndexInSelection = selectedText.indexOf(trimmedPrefix)
            val endIndexInSelection = selectedText.lastIndexOf(trimmedSuffix)
            text.delete(end.line, start.column + endIndexInSelection, end.line, start.column + endIndexInSelection + trimmedSuffix.length)
            text.delete(start.line, start.column + startIndexInSelection, start.line, start.column + startIndexInSelection + trimmedPrefix.length)
        } else {
            text.insert(end.line, end.column, suffix)
            text.insert(start.line, start.column, prefix)
        }
    }

    fun toggleReadOnly(editor: CodeEditor, context: Context): Boolean {
        val newEditableState = !editor.isEditable
        editor.isEditable = newEditableState
        saveReadOnlyState(context, !newEditableState)
        return !newEditableState
    }

    fun applyReadOnlyState(editor: CodeEditor, context: Context) {
        editor.isEditable = !isReadOnly(context)
    }

    fun isReadOnly(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_READ_ONLY, false)
    }

    private fun saveReadOnlyState(context: Context, isReadOnly: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_READ_ONLY, isReadOnly).apply()
    }

    fun goToPreviousLocation(editor: CodeEditor): Boolean {
        if (!editor.cursor.isSelected) {
            navigationHistory.add(editor.cursor.left())
        }
        val previousPosition = navigationHistory.back()
        return if (previousPosition != null) {
            editor.setSelection(previousPosition.line, previousPosition.column)
            true
        } else false
    }

    fun goToNextLocation(editor: CodeEditor): Boolean {
        if (!editor.cursor.isSelected) {
            navigationHistory.add(editor.cursor.left())
        }
        val nextPosition = navigationHistory.forward()
        return if (nextPosition != null) {
            editor.setSelection(nextPosition.line, nextPosition.column)
            true
        } else false
    }

    private fun getFirstNonWhitespace(line: String): Int {
        for (i in line.indices) {
            if (!Character.isWhitespace(line[i])) return i
        }
        return line.length
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val SMART_SELECTION_STACK_KEY = R.id.tag_smart_selection_stack

    @Suppress("UNCHECKED_CAST")
    private fun getSmartSelectionStack(editor: CodeEditor): MutableList<Range> {
        var stack = editor.getTag(SMART_SELECTION_STACK_KEY) as? MutableList<Range>
        if (stack == null) {
            stack = mutableListOf()
            editor.setTag(SMART_SELECTION_STACK_KEY, stack)
        }
        return stack
    }

    fun expandSelection(editor: IDEEditor): Boolean {
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return false
        val stack = getSmartSelectionStack(editor)
        val currentRange: Range
        val nodeToExpandFrom: TSNode
        if (stack.isEmpty()) {
            if (editor.cursor.isSelected) {
                currentRange = editor.cursorLSPRange
            } else {
                editor.selectCurrentWord()
                if (!editor.cursor.isSelected) return false
                currentRange = editor.cursorLSPRange
            }
            val startByte = editor.text.getCharIndex(currentRange.start) * 2
            val endByte = editor.text.getCharIndex(currentRange.end) * 2
            nodeToExpandFrom = tree.findSmallestNodeContaining(startByte, endByte) ?: return false
            stack.add(nodeToExpandFrom.toLspRange(editor.text))
        } else {
            currentRange = stack.last()
            val startByte = editor.text.getCharIndex(currentRange.start) * 2
            val endByte = editor.text.getCharIndex(currentRange.end) * 2
            nodeToExpandFrom = tree.findSmallestNodeContaining(startByte, endByte) ?: return false
        }
        var parentNode = nodeToExpandFrom.parent
        while (parentNode != null && parentNode.startByte == nodeToExpandFrom.startByte && parentNode.endByte == nodeToExpandFrom.endByte) {
            parentNode = parentNode.parent
        }
        if (parentNode == null) return false
        val newRange = parentNode.toLspRange(editor.text)
        if (newRange == currentRange) return false
        stack.add(newRange)
        editor.setSelection(newRange)
        return true
    }

    fun shrinkSelection(editor: IDEEditor): Boolean {
        val stack = getSmartSelectionStack(editor)
        if (stack.size <= 1) {
            if (stack.isNotEmpty()) {
                val lastKnownRange = stack.first()
                stack.clear()
                editor.setSelection(lastKnownRange.start)
            }
            return false
        }
        stack.removeAt(stack.lastIndex)
        val previousRange = stack.last()
        editor.setSelection(previousRange)
        return true
    }

    fun addCursorAbove(editor: CodeEditor): Boolean {
        if (editor.cursor.getCursorCount() > 1) {
            return false
        }
        val currentPos = editor.cursor.left()
        if (currentPos.line == 0) {
            return false
        }
        editor.cursor.addCursor(currentPos.line - 1, currentPos.column)
        return true
    }

    fun addCursorBelow(editor: CodeEditor): Boolean {
        if (editor.cursor.getCursorCount() > 1) {
            return false
        }
        val currentPos = editor.cursor.left()
        if (currentPos.line >= editor.lineCount - 1) {
            return false
        }
        editor.cursor.addCursor(currentPos.line + 1, currentPos.column)
        return true
    }

    fun selectAllOccurrences(editor: CodeEditor): Boolean {
        val cursor = editor.cursor
        if (cursor.getCursorCount() > 1) {
            return false
        }
        if (!cursor.isSelected) {
            editor.selectCurrentWord()
            if (!cursor.isSelected) {
                return false
            }
        }
        val selectedText = editor.text.subSequence(cursor.left().index, cursor.right().index).toString()
        if (selectedText.isNullOrEmpty()) {
            return false
        }
        val searcher = editor.searcher
        val searchOptions = EditorSearcher.SearchOptions(true, true)
        searcher.search(selectedText, searchOptions)
        val results = mutableListOf<io.github.rosemoe.sora.text.TextRange>()
        while (searcher.gotoNext()) {
            results.add(editor.cursor.range)
        }
        if (results.size <= 1) {
            searcher.stopSearch()
            return false
        }
        cursor.clearSelection()
        for (result in results) {
            cursor.addSelection(result.start.line, result.start.column, result.end.line, result.end.column)
        }
        searcher.stopSearch()
        return true
    }

    // region: 代码格式化 (Code Formatting) - 终极扩展区域
    
    private val formatters: Map<String, CodeFormatter> =
        mapOf<String, CodeFormatter>(
            "xml" to XmlFormatter(),
            "java" to JavaFormatter(JavaFormatOptions()),
            // "kt" to KotlinFormatter(KotlinFormatOptions()),
            // "kts" to KotlinFormatter(KotlinFormatOptions()),

            "gradle" to GradleGroovyFormatter(),
            "smali" to SmaliFormatter(),
            "html" to HtmlFormatter(),
            "htm" to HtmlFormatter(),
            "css" to CssFormatter(),
            "scss" to CssFormatter(),
            "less" to CssFormatter(),
            "c" to CLikeTreeSitterFormatter("c"),
            "cpp" to CLikeTreeSitterFormatter("cpp"), "cxx" to CLikeTreeSitterFormatter("cpp"), "cc" to CLikeTreeSitterFormatter("cpp"),
            "h" to CLikeTreeSitterFormatter("c"), "hpp" to CLikeTreeSitterFormatter("cpp"), "hh" to CLikeTreeSitterFormatter("cpp"),
            "cs" to CLikeTreeSitterFormatter("csharp"),
            "objc" to CLikeTreeSitterFormatter("objectivec"), "m" to CLikeTreeSitterFormatter("objectivec"),
            "js" to CLikeTreeSitterFormatter("javascript"), "mjs" to CLikeTreeSitterFormatter("javascript"), "cjs" to CLikeTreeSitterFormatter("javascript"),
            "jsx" to CLikeTreeSitterFormatter("javascript"), 
            "ts" to CLikeTreeSitterFormatter("typescript"),
            "tsx" to CLikeTreeSitterFormatter("tsx"),
            "py" to PythonTreeSitterFormatter(),
            "sh" to ShellTreeSitterFormatter(), "bash" to ShellTreeSitterFormatter(), "zsh" to ShellTreeSitterFormatter(), "ksh" to ShellTreeSitterFormatter(),
            "go" to GoTreeSitterFormatter(),
            "rust" to CLikeTreeSitterFormatter("rust"), "rs" to CLikeTreeSitterFormatter("rust"),
            "ruby" to RubyTreeSitterFormatter(), "rb" to RubyTreeSitterFormatter(),
            "lua" to LuaTreeSitterFormatter(),
            "php" to CLikeTreeSitterFormatter("php"),
            "perl" to ShellTreeSitterFormatter(), "pl" to ShellTreeSitterFormatter(),
            "swift" to CLikeTreeSitterFormatter("swift"),
            "json" to JsonFormatter(),
            "jsonc" to JsonFormatter(),
            "yaml" to YamlFormatter(), "yml" to YamlFormatter(),
            "toml" to TomlFormatter(),
            "ini" to IniFormatter(),
            "properties" to IniFormatter(),
            "conf" to IniFormatter(),
            "cfg" to IniFormatter(),
            "sql" to SqlFormatter(),
            "md" to MarkdownFormatter(), "markdown" to MarkdownFormatter(),
            "dockerfile" to DockerfileFormatter(), "Dockerfile" to DockerfileFormatter(),
            "cmake" to IniFormatter(),
            "gitignore" to object : CodeFormatter {
                override fun format(source: String): String {
                    return source.lines().filter { l -> l.isNotBlank() }.joinToString("\n")
                }
            },
        )
    
    fun formatCode(editor: CodeEditor, file: File): Boolean {
        val formatter = formatters[file.extension.lowercase(Locale.ROOT)] ?: formatters[file.name] ?: return false

        try {
            val originalText: String
            val selectionRange: Range?

            if (editor.cursor.isSelected) {
                selectionRange = (editor as? IDEEditor)?.cursorLSPRange
                    ?: Range(
                        Position(editor.cursor.leftLine, editor.cursor.leftColumn),
                        Position(editor.cursor.rightLine, editor.cursor.rightColumn)
                    )
                originalText = editor.text.subSequence(editor.cursor.left().index, editor.cursor.right().index).toString()
            } else {
                selectionRange = null
                originalText = editor.text.toString()
            }

            if (originalText.isBlank()) return true

            val formattedText = formatter.format(originalText)

            if (formattedText != originalText) {
                 editor.text.beginBatchEdit()
                 try {
                    if (selectionRange != null) {
                        editor.text.replace(
                            selectionRange.start.line, selectionRange.start.column,
                            selectionRange.end.line, selectionRange.end.column,
                            formattedText
                        )
                    } else {
                        val cursor = editor.cursor.left()
                        editor.setText(formattedText)
                        if(cursor.line < editor.lineCount) {
                           val col = min(cursor.column, editor.text.getColumnCount(cursor.line))
                           editor.setSelection(cursor.line, col)
                        }
                    }
                } finally {
                    editor.text.endBatchEdit()
                }
            }
            return true
        } catch (e: Exception) {
            System.err.println("Code formatting failed for ${file.name}: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    // endregion

    private val snippets by lazy {
        mapOf(
            "java" to listOf(
                CodeSnippet("sysout", "System.out.println($0);", "Prints to the standard output stream."),
                CodeSnippet("fori", "for (int i = 0; i < $1; i++) {\n\t$0\n}", "Creates a for loop."),
                CodeSnippet("psvm", "public static void main(String[] args) {\n\t$0\n}", "Main method declaration."),
                CodeSnippet("try", "try {\n\t$0\n} catch (Exception e) {\n\te.printStackTrace();\n}", "Try-catch block."),
                CodeSnippet("meth", "public void %s() {\n\t$0\n}", "Creates a public method."),
                CodeSnippet("cls", "public class %s {\n\t$0\n}", "Creates a public class.")
            ),
            "kt" to listOf(
                CodeSnippet("fun", "fun %s() {\n\t$0\n}", "Creates a function."),
                CodeSnippet("main", "fun main(args: Array<String>) {\n\t$0\n}", "Main function for command-line app."),
                CodeSnippet("comp", "companion object {\n\t$0\n}", "Creates a companion object."),
                CodeSnippet("for", "for (item in $1) {\n\t$0\n}", "Creates a for loop.")
            ),
            "xml" to listOf(
                CodeSnippet("comment", "<!-- $0 -->", "Inserts an XML comment."),
                CodeSnippet("LinearLayoutV", "<LinearLayout\n\tandroid:layout_width=\"match_parent\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:orientation=\"vertical\">\n\t$0\n</LinearLayout>", "Vertical LinearLayout."),
                CodeSnippet("LinearLayoutH", "<LinearLayout\n\tandroid:layout_width=\"match_parent\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:orientation=\"horizontal\">\n\t$0\n</LinearLayout>", "Horizontal LinearLayout."),
                CodeSnippet("TextView", "<TextView\n\tandroid:layout_width=\"wrap_content\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:text=\"$1\" />", "A simple TextView."),
                CodeSnippet("Button", "<Button\n\tandroid:layout_width=\"wrap_content\"\n\tandroid:layout_height=\"wrap_content\"\n\tandroid:text=\"$1\" />", "A simple Button.")
            ),
            "sh" to listOf(
                CodeSnippet("if", "if [ $1 ]; then\n\t$0\nfi", "If statement."),
                CodeSnippet("for", "for i in $1; do\n\t$0\ndone", "For loop."),
                CodeSnippet("header", "#!/bin/bash\n\n$0", "Adds a shebang for bash.")
            )
        )
    }

    fun showAndInsertSnippet(editor: CodeEditor, context: Context, file: File) {
        val lang = file.extension.lowercase()
        val langKey = if (lang == "gradle.kts") "kts" else lang
        val langSnippets = snippets[langKey] ?: snippets[lang] ?: return
        val dialogView = LayoutInflater.from(context).inflate(R.layout.action_menu_dialog_insert_snippet, null as ViewGroup?)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinner_snippets)
        val nameInputLayout = dialogView.findViewById<TextInputLayout>(R.id.input_layout_name)
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_text_name)
        val descriptionText = dialogView.findViewById<TextView>(R.id.text_description)
        val snippetNames = langSnippets.map { it.name }
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, snippetNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedSnippet = langSnippets[position]
                descriptionText.text = selectedSnippet.description
                nameInputLayout.visibility = if (selectedSnippet.requiresNameInput) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        AlertDialog.Builder(context)
            .setTitle("Insert Code Snippet")
            .setView(dialogView as View)
            .setPositiveButton("Insert") { _, _ ->
                val selectedSnippet = langSnippets[spinner.selectedItemPosition]
                val name = if (selectedSnippet.requiresNameInput) nameEditText.text.toString() else ""
                var finalCode = selectedSnippet.template.replace("%s", name).replace(Regex("\\$\\d+"), "")
                val finalCursorOffset = finalCode.indexOf("$0")
                if (finalCursorOffset != -1) {
                    finalCode = finalCode.replace("$0", "")
                }
                val startCursor = editor.cursor.left()
                editor.text.insert(startCursor.line, startCursor.column, finalCode)
                if (finalCursorOffset != -1) {
                    val newCursorPos = editor.text.getCharPositionForByte(startCursor.index * 2 + finalCursorOffset * 2) // Byte offset
                    editor.setSelection(newCursorPos.line, newCursorPos.column)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }


    private val symbolQueries by lazy {
        mapOf(
            "java" to """
                (class_declaration name: (identifier) @name) @class
                (method_declaration name: (identifier) @name parameters: (formal_parameters) @params) @method
                (field_declaration (variable_declarator name: (identifier) @name)) @field
            """.trimIndent(),
            "kt" to """
                (class_declaration name: (simple_identifier) @name) @class
                (function_declaration name: (simple_identifier) @name parameters: (function_value_parameters) @params) @method
                (property_declaration name: (simple_identifier) @name) @field
            """.trimIndent()
        )
    }

    fun getCodeStructure(editor: IDEEditor): List<CodeSymbol> {
        val lang = editor.file?.extension?.lowercase() ?: return emptyList()
        val queryScm = symbolQueries[lang] ?: return emptyList()
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return emptyList()
        val langImpl = LanguageAnalysisBridge.getTsLanguage(editor) ?: return emptyList()
        val content = editor.text
        val symbols = mutableListOf<CodeSymbol>()
        val query = TSQuery.create(langImpl, queryScm)
        val matches = tree.executeQuery(langImpl, queryScm)
        matches.forEach { match: TSQueryMatch ->
            val node = match.captures[0].node
            val nameNode = match.captures.find { c -> query.getCaptureNameForId(c.index) == "name" }?.node
            if (nameNode != null) {
                val name = nameNode.getText(content).toString()
                val range = node.toLspRange(content)
                val selectionRange = nameNode.toLspRange(content)

                val classCapture = match.captures.find { c -> query.getCaptureNameForId(c.index) == "class" }
                val methodCapture = match.captures.find { c -> query.getCaptureNameForId(c.index) == "method" }
                val fieldCapture = match.captures.find { c -> query.getCaptureNameForId(c.index) == "field" }

                when {
                    classCapture != null -> {
                        symbols.add(CodeSymbol(name, "", "Class", range, selectionRange))
                    }
                    methodCapture != null -> {
                        val params = match.captures.find { c -> query.getCaptureNameForId(c.index) == "params" }?.node?.getText(content)?.toString() ?: "()"
                        val bodyNode = node.getChildByFieldName("body")
                        val bodyRange = bodyNode?.toLspRange(content)
                        symbols.add(CodeSymbol(name, params, "Method", range, selectionRange, bodyRange))
                    }
                    fieldCapture != null -> {
                        val typeNode = node.parent?.getChildByFieldName("type")
                        val detail = typeNode?.getText(content)?.toString() ?: ""
                        symbols.add(CodeSymbol(name, detail, "Field", range, selectionRange))
                    }
                }
            }
        }
        query.close()
        return symbols.sortedBy { it.range.start.line }
    }

/**
     * Shows the code outline dialog for the current editor.
     * This is now a proxy to the new CodeOutlineManager.
     */
    fun showCodeOutline(editor: IDEEditor, context: Context) {
        CodeOutlineManager.show(editor, context)
    }
    
    private fun Position.isBefore(other: Position): Boolean {
        return this.line < other.line || (this.line == other.line && this.column < other.column)
    }

    // private fun showRenameDialog(editor: IDEEditor, context: Context, symbol: CodeSymbol) {
        // val input = EditText(context).apply { setText(symbol.name) }
        // AlertDialog.Builder(context)
            // .setTitle("Rename Symbol")
            // .setView(input)
            // .setPositiveButton("Rename") { _, _ ->
                // val newName = input.text.toString()
                // if (newName.isNotBlank() && newName != symbol.name) {
                    // renameSymbolInFile(editor, symbol.name, newName)
                // }
            // }
            // .setNegativeButton("Cancel", null)
            // .show()
    // }

    // private fun renameSymbolInFile(editor: CodeEditor, oldName: String, newName: String) {
        // val searcher = editor.searcher
        // searcher.search(oldName, EditorSearcher.SearchOptions(true, true))
        // val replacements = mutableListOf<Pair<Range, String>>()
        // while (searcher.gotoNext()) {
            // replacements.add(editor.cursor.range.toLspRange() to newName)
        // }
        // searcher.stopSearch()
        // editor.text.beginBatchEdit()
        // try {
            // replacements.asReversed().forEach { (range, text) ->
                // editor.text.replace(range.start.line, range.start.column, range.end.line, range.end.column, text)
            // }
        // } finally {
            // editor.text.endBatchEdit()
        // }
    // }

    /**
     * 检查光标当前是否在可转换的 switch/when 语句中
     */
    fun isCursorInSwitch(editor: IDEEditor): Boolean {
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return false
        val cursorByte = editor.getCursorBytePosition()
        // 简单检查节点类型即可
        val switchNode = tree.findSmallestNodeContaining(cursorByte, cursorByte) { node ->
            node.type == "switch_expression" || node.type == "when_expression"
        }
        return switchNode != null
    }

    /**
     * 检查光标当前是否在可转换的 if-else 链中
     */
    fun isCursorInConvertibleIfElse(editor: IDEEditor): Boolean {
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return false
        val cursorByte = editor.getCursorBytePosition()
        val ifNode = tree.findSmallestNodeContaining(cursorByte, cursorByte) { node ->
            node.type == "if_statement" || node.type == "if_expression"
        } ?: return false

        val topIfNode = findTopmostIfNode(ifNode)
        return analyzeIfElseChain(topIfNode, editor) != null
    }

    /**
     * 将 Switch/When 语句转换为 If-Else 链
     */
    fun convertSwitchToIfElse(editor: IDEEditor): Boolean {
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return false
        val lang = getLanguage(editor) ?: return false
        val content = editor.text

        val cursorByte = editor.getCursorBytePosition()
        val switchNode = tree.findSmallestNodeContaining(cursorByte, cursorByte) { node ->
            node.type == "switch_expression" || node.type == "when_expression"
        } ?: return false

        val subjectNode = switchNode.getChildByFieldName("condition")
            ?: switchNode.getChildByFieldName("subject")
            ?: return false
        val subject = subjectNode.getText(content).toString()
        val bodyNode = switchNode.getChildByFieldName("body") ?: return false

        val baseIndent = " ".repeat(switchNode.getStartPoint().column)

        val builder = StringBuilder()
        var isFirstIf = true
        var defaultCaseBody: String? = null

        val caseGroups = groupSwitchCasesWithFallthrough(bodyNode, lang, content)

        for (group in caseGroups) {
            val bodyText = extractBodyText(group.bodyNode, lang, content)
                ?.lines()
                ?.joinToString("\n$baseIndent\t")
                ?.trim() ?: ""

            if (group.isDefault) {
                defaultCaseBody = bodyText
                continue
            }

            if (group.conditions.isEmpty()) continue

            val conditionExpression = group.conditions.joinToString(" || ") { condition ->
                if (lang == "kt" && !condition.contains(" ")) {
                    "$subject == $condition"
                } else {
                    "$subject == $condition"
                }
            }

            if (isFirstIf) {
                builder.append("if ($conditionExpression) {\n")
                isFirstIf = false
            } else {
                builder.append(" else if ($conditionExpression) {\n")
            }
            builder.append("$baseIndent\t$bodyText\n")
            builder.append("$baseIndent}")
        }

        if (defaultCaseBody != null) {
            builder.append(" else {\n")
            builder.append("$baseIndent\t$defaultCaseBody\n")
            builder.append("$baseIndent}")
        }

        if (builder.isEmpty()) return false

        val range = switchNode.toLspRange(content)
        editor.text.replace(range.start.line, range.start.column, range.end.line, range.end.column, builder.toString())

        return true
    }

    /**
     * 将 If-Else 链转换为 Switch/When 语句
     */
    fun convertIfElseToSwitch(editor: IDEEditor): Boolean {
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return false
        val content = editor.text
        val lang = getLanguage(editor) ?: return false

        val cursorByte = editor.getCursorBytePosition()
        val ifNode = tree.findSmallestNodeContaining(cursorByte, cursorByte) { node ->
            node.type == "if_statement" || node.type == "if_expression"
        } ?: return false

        val topIfNode = findTopmostIfNode(ifNode)
        val analysis = analyzeIfElseChain(topIfNode, editor) ?: return false
        val subject = analysis.subject

        val baseIndent = " ".repeat(topIfNode.getStartPoint().column)
        val builder = StringBuilder()

        if (lang == "java") {
            builder.append("switch ($subject) {\n")
            analysis.cases.forEach { (values, body) ->
                builder.append("$baseIndent    case ${values.joinToString(", ")} -> {\n")
                builder.append("$baseIndent        ${body.lines().joinToString("\n$baseIndent        ")}\n")
                builder.append("$baseIndent    }\n")
            }
            if (analysis.defaultBody != null) {
                builder.append("$baseIndent    default -> {\n")
                builder.append("$baseIndent        ${analysis.defaultBody.lines().joinToString("\n$baseIndent        ")}\n")
                builder.append("$baseIndent    }\n")
            }
            builder.append("$baseIndent}")
        } else { // Kotlin
            builder.append("when ($subject) {\n")
            analysis.cases.forEach { (values, body) ->
                builder.append("$baseIndent    ${values.joinToString(", ")} -> {\n")
                builder.append("$baseIndent        ${body.lines().joinToString("\n$baseIndent        ")}\n")
                builder.append("$baseIndent    }\n")
            }
            if (analysis.defaultBody != null) {
                builder.append("$baseIndent    else -> {\n")
                builder.append("$baseIndent        ${analysis.defaultBody.lines().joinToString("\n$baseIndent        ")}\n")
                builder.append("$baseIndent    }\n")
            }
            builder.append("$baseIndent}")
        }

        val range = topIfNode.toLspRange(content)
        editor.text.replace(range.start.line, range.start.column, range.end.line, range.end.column, builder.toString())

        return true
    }

    private data class IfElseChainAnalysis(
        val subject: String,
        val cases: List<Pair<List<String>, String>>,
        val defaultBody: String?
    )

    private data class SwitchCaseGroup(
        val conditions: List<String>,
        val bodyNode: TSNode?,
        val isDefault: Boolean = false
    )

    private fun findTopmostIfNode(startNode: TSNode): TSNode {
        var topNode = startNode
        while (topNode.parent?.type in listOf("if_statement", "if_expression", "block")) {
            topNode = topNode.parent!!
        }
        return topNode
    }

    private fun extractBodyText(bodyNode: TSNode?, lang: String, content: Content): String? {
        bodyNode ?: return null
        var text = bodyNode.getText(content).toString().trim()

        if (lang == "kt" && text.startsWith("->")) {
            text = text.substring(2).trim()
        }

        if (bodyNode.type == "block" || text.startsWith('{')) {
            val childrenText = (0 until bodyNode.namedChildCount).map { bodyNode.getNamedChild(it).getText(content).toString() }.joinToString("\n")
            if (childrenText.isNotEmpty()) return childrenText.trim()

            if (text.startsWith('{') && text.endsWith('}')) {
                 return text.substring(1, text.length - 1).trim()
            }
        }

        if (text.endsWith("break;")) {
            text = text.removeSuffix("break;").trim()
        }
        return text
    }

    private fun groupSwitchCasesWithFallthrough(bodyNode: TSNode, lang: String, content: Content): List<SwitchCaseGroup> {
        val groups = mutableListOf<SwitchCaseGroup>()
        val children = (0 until bodyNode.childCount).map { bodyNode.getChild(it) }
        val caseNodes = if (lang == "java") {
            children.filter { it.type == "switch_block_statement_group" }
        } else {
            children.filter { it.type == "when_entry" }
        }

        var currentConditions = mutableListOf<String>()

        for (caseNode in caseNodes) {
            val caseChildren = (0 until caseNode.childCount).map { caseNode.getChild(it) }
            val labels = if (lang == "java") caseChildren.filter { it.type == "switch_label" } else caseChildren.filter { it.type.endsWith("pattern") }
            val bodyContentNodes = if (lang == "java") caseChildren.filterNot { it.type == "switch_label" } else listOfNotNull(caseNode.getChildByFieldName("body"))

            val hasBreak = bodyContentNodes.any { node -> (0 until node.childCount).map{node.getChild(it)}.any { it.type == "break_statement" } } || bodyContentNodes.any { it.type == "throw_statement" }
            val isDefault = labels.any { it.getText(content).toString().startsWith("default") || it.getText(content).toString().startsWith("else") }

            currentConditions.addAll(labels.map { it.getText(content).toString().removePrefix("case ").trim() }.filterNot { it.startsWith("default") || it.startsWith("else") })

            if (bodyContentNodes.isNotEmpty()) {
                val effectiveBodyNode = bodyContentNodes.first()
                groups.add(SwitchCaseGroup(currentConditions.toList(), effectiveBodyNode, isDefault))
                currentConditions = mutableListOf()
            }

            if (hasBreak) {
                currentConditions = mutableListOf()
            }
        }
        return groups
    }

    private fun analyzeIfElseChain(topIfNode: TSNode, editor: IDEEditor): IfElseChainAnalysis? {
        var commonSubject: String? = null
        val cases = mutableListOf<Pair<List<String>, String>>()
        var defaultBody: String? = null
        var currentNode: TSNode? = topIfNode
        val lang = getLanguage(editor) ?: "java"
        val content = editor.text

        while (currentNode != null && (currentNode.type == "if_statement" || currentNode.type == "if_expression")) {
            val conditionNode = currentNode.getChildByFieldName("condition") ?: return null

            val analysisResult = analyzeCondition(conditionNode, commonSubject, content) ?: return null
            if (commonSubject == null) {
                commonSubject = analysisResult.subject
            }
            val values = analysisResult.values

            val thenNode = currentNode.getChildByFieldName("consequence") ?: return null
            val body = extractBodyText(thenNode, lang, content) ?: ""

            cases.add(values to body)

            val elseNode = currentNode.getChildByFieldName("alternative")
            if (elseNode == null) {
                currentNode = null
            } else {
                val children = (0 until elseNode.childCount).map { elseNode.getChild(it) }
                val nextIf = children.find { it.type == "if_statement" || it.type == "if_expression" }
                if (nextIf != null) {
                    currentNode = nextIf
                } else {
                    defaultBody = extractBodyText(elseNode, lang, content)
                    currentNode = null
                }
            }
        }

        return if (commonSubject != null && cases.isNotEmpty()) {
            IfElseChainAnalysis(commonSubject, cases, defaultBody)
        } else {
            null
        }
    }

    private data class ConditionAnalysis(val subject: String, val values: List<String>)

    private fun analyzeCondition(conditionNode: TSNode, expectedSubject: String?, content: Content): ConditionAnalysis? {
        when (conditionNode.type) {
            "binary_expression" -> {
                val operator = (0 until conditionNode.childCount).map { conditionNode.getChild(it) }.find { !it.isNamed }?.getText(content).toString()
                val left = conditionNode.getChildByFieldName("left") ?: return null
                val right = conditionNode.getChildByFieldName("right") ?: return null

                return when (operator) {
                    "==" -> {
                        val (subject, value) = if (isIdentifier(left)) left to right else right to left
                        if (!isIdentifier(subject)) return null

                        val subjectText = subject.getText(content).toString()
                        if (expectedSubject != null && subjectText != expectedSubject) return null

                        ConditionAnalysis(subjectText, listOf(value.getText(content).toString()))
                    }
                    "||" -> {
                        val leftAnalysis = analyzeCondition(left, expectedSubject, content) ?: return null
                        val rightAnalysis = analyzeCondition(right, leftAnalysis.subject, content) ?: return null
                        ConditionAnalysis(leftAnalysis.subject, leftAnalysis.values + rightAnalysis.values)
                    }
                    else -> null
                }
            }
            else -> return null
        }
    }

    private fun isIdentifier(node: TSNode): Boolean {
        return node.type in listOf("identifier", "simple_identifier", "field_expression")
    }
}

class NavigationHistory(private val capacity: Int = 100) {
    private val history = mutableListOf<CharPosition>()
    private var currentIndex = -1

    fun add(position: CharPosition) {
        if (history.isNotEmpty() && currentIndex >= 0 && currentIndex < history.size && history[currentIndex] == position) {
            return
        }
        if (currentIndex < history.size - 1) {
            history.subList(currentIndex + 1, history.size).clear()
        }
        history.add(position)
        currentIndex++
        if (history.size > capacity) {
            history.removeAt(0)
            currentIndex--
        }
    }

    fun back(): CharPosition? {
        if (canGoBack()) {
            currentIndex--
            return history[currentIndex]
        }
        return null
    }

    fun forward(): CharPosition? {
        if (canGoForward()) {
            currentIndex++
            return history[currentIndex]
        }
        return null
    }

    fun canGoBack(): Boolean = currentIndex > 0
    fun canGoForward(): Boolean = currentIndex < history.size - 1
    fun clear() {
        history.clear()
        currentIndex = -1
    }
}