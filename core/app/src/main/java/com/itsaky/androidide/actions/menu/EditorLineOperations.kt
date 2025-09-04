package com.itsaky.androidide.actions.menu // Or com.itsaky.androidide.utils if preferred

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import com.itsaky.androidide.resources.R
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Cursor
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * 中文注释: EditorLineOperations 对象包含了所有文本编辑器中行操作的逻辑。
 * English annotation: The EditorLineOperations object contains the logic for all line operations in the text editor.
 */
object EditorLineOperations {

    // 中文注释: 定义注释符号
    // English annotation: Define the comment symbol
    private const val COMMENT_PREFIX = "// "

    /**
     * 中文注释: 复制当前行或选中的文本到剪贴板。
     * English annotation: Copies the current line or selected text to the clipboard.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun copyLine(editor: CodeEditor, context: Context): Boolean {
        val cursor = editor.cursor
        if (cursor.isSelected) {
            editor.copyText()
        } else {
            val i = cursor.left().line
            editor.setSelectionRegion(i, 0, i, editor.text.getColumnCount(i))
            editor.copyText(false)
        }
        // Optional: if you want a toast here
        // SketchwareUtil.showMessage(context, "Text has been copied to clipboard")
        return true
    }

    /**
     * 中文注释: 剪切当前行或选中的文本。
     * English annotation: Cuts the current line or selected text.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun cutLine(editor: CodeEditor, context: Context): Boolean {
        editor.cutLine()
        return true
    }

    /**
     * 中文注释: 删除当前行或选中的行。
     * English annotation: Deletes the current line or selected lines.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun deleteLine(editor: CodeEditor, context: Context): Boolean {
        val cursor = editor.cursor
        if (cursor.isSelected) {
            editor.deleteText()
        } else {
            val currentLine = cursor.left().line
            val nextLine = currentLine + 1

            if (nextLine == editor.lineCount) {
                editor.setSelectionRegion(
                    currentLine, 0,
                    currentLine, editor.text.getColumnCount(currentLine)
                )
            } else {
                editor.setSelectionRegion(
                    currentLine, 0,
                    nextLine, 0
                )
            }
            editor.deleteText()
        }
        return true
    }

    /**
     * 中文注释: 清空当前行或选中的行内容。
     * English annotation: Empties the content of the current line or selected lines.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
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

    /**
     * 中文注释: 用剪贴板内容替换当前行或选中的行内容。
     * English annotation: Replaces the content of the current line or selected lines with the clipboard content.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
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

    /**
     * 中文注释: 复制当前行或选中的行，并粘贴到下一行。
     * English annotation: Duplicates the current line or selected lines and pastes them on the next line.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
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

                content.insert(
                    endLine, content.getColumnCount(endLine),
                    duplicateContent.toString()
                )

                val newStartLine = endLine + 1
                val newEndLine = endLine + 1 + (endLine - startLine)
                editor.setSelectionRegion(
                    newStartLine, 0,
                    newEndLine, content.getColumnCount(newEndLine)
                )
            } else {
                val line = cursor.leftLine
                val column = cursor.leftColumn
                val lineContent = content.getLineString(line)

                content.insert(
                    line, content.getColumnCount(line), "\n" + lineContent
                )
                editor.setSelection(line, column)
            }
        } finally {
            content.endBatchEdit()
        }
        return true
    }

    /**
     * 中文注释: 将选中的文本转换为大写或小写。
     * English annotation: Converts the selected text to uppercase or lowercase.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @param toUpper 中文注释: 如果为 true 则转换为大写，否则转换为小写。 English annotation: True to convert to uppercase, false to convert to lowercase.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun convertUpperLowerCase(editor: CodeEditor, context: Context, toUpper: Boolean): Boolean {
        val content = editor.text
        val cursor = editor.cursor

        if (!cursor.isSelected) {
            // SketchwareUtil.showMessage(context, "Please select the text to be converted first") // Optional Toast
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

    /**
     * 中文注释: 增加当前行或选中区域的缩进。
     * English annotation: Increases the indentation of the current line or selected region.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
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

    /**
     * 中文注释: 减少当前行或选中区域的缩进。
     * English annotation: Decreases the indentation of the current line or selected region.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
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

                while (spacesToRemove < lineText.length &&
                    lineText[spacesToRemove] == ' ') {
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

    /**
     * 中文注释: 切换当前行或选中区域的注释状态。
     * English annotation: Toggles the comment state of the current line or selected region.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun toggleComment(editor: CodeEditor, context: Context): Boolean {
        val cursor = editor.cursor
        val text = editor.text

        text.beginBatchEdit()
        try {
            if (cursor.isSelected) {
                val startLine = cursor.leftLine
                val endLine = cursor.rightLine

                var allCommented = true
                for (line in startLine..endLine) {
                    val lineStr = text.getLineString(line)
                    val firstCharPos = getFirstNonWhitespace(lineStr)

                    // Check if the line starts with the comment prefix after leading whitespace
                    if (!lineStr.substring(firstCharPos).startsWith(COMMENT_PREFIX)) {
                        allCommented = false
                        break
                    }
                }

                for (line in startLine..endLine) {
                    val lineStr = text.getLineString(line)
                    val firstCharPos = getFirstNonWhitespace(lineStr)

                    if (firstCharPos >= lineStr.length) {
                        continue // Skip empty or all-whitespace lines
                    }

                    if (allCommented) {
                        // Uncomment: remove the prefix
                        if (lineStr.substring(firstCharPos).startsWith(COMMENT_PREFIX)) {
                            editor.setSelectionRegion(line, firstCharPos, line, firstCharPos + COMMENT_PREFIX.length)
                            editor.deleteText()
                        }
                    } else {
                        // Comment: add the prefix
                        if (!lineStr.substring(firstCharPos).startsWith(COMMENT_PREFIX)) {
                            editor.setSelection(line, firstCharPos)
                            editor.commitText(COMMENT_PREFIX)
                        }
                    }
                }

                editor.setSelectionRegion(startLine, 0, endLine, text.getColumnCount(endLine))
            } else {
                // Single line toggle
                val line = cursor.leftLine
                val lineStr = text.getLineString(line)
                val firstCharPos = getFirstNonWhitespace(lineStr)

                if (firstCharPos < lineStr.length) {
                    if (lineStr.substring(firstCharPos).startsWith(COMMENT_PREFIX)) {
                        // Uncomment
                        editor.setSelectionRegion(line, firstCharPos, line, firstCharPos + COMMENT_PREFIX.length)
                        editor.deleteText()
                    } else {
                        // Comment
                        editor.setSelection(line, firstCharPos)
                        editor.commitText(COMMENT_PREFIX)
                    }
                }
            }
        } finally {
            text.endBatchEdit()
        }
        return true
    }

    /**
     * 中文注释: 获取给定行中第一个非空白字符的索引。
     * English annotation: Gets the index of the first non-whitespace character in a given line.
     * @param line 中文注释: 要检查的行字符串。 English annotation: The line string to check.
     * @return Int 中文注释: 第一个非空白字符的索引，如果行全是空白则返回行长度。 English annotation: The index of the first non-whitespace character, or the line length if the line is all whitespace.
     */
    private fun getFirstNonWhitespace(line: String): Int {
        for (i in line.indices) {
            if (!Character.isWhitespace(line[i])) {
                return i
            }
        }
        return line.length
    }

    /**
     * 中文注释: 显示“跳转到行”对话框，允许用户输入行号并跳转。
     * English annotation: Displays the "Jump to line" dialog, allowing the user to enter a line number and jump to it.
     * @param editor 中文注释: CodeEditor 实例。 English annotation: The CodeEditor instance.
     * @param context 中文注释: 应用上下文。 English annotation: The application context.
     * @return Boolean 中文注释: 如果操作成功则返回 true。 English annotation: True if the operation was successful.
     */
    fun jumpToLine(editor: CodeEditor, context: Context): Boolean {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_jump_to_line, null)
        val textInputLayout = view.findViewById<TextInputLayout>(R.id.textInputLayout)
        val editText = view.findViewById<android.widget.EditText>(R.id.editText)

        val hint = "Line number [1-${editor.lineCount}]"
        textInputLayout.hint = hint

        val builder = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.jump_to_line_title))
            .setView(view)
            .setPositiveButton(context.getString(R.string.ok_button)) { dialog, _ -> } // Handled manually
            .setNegativeButton(context.getString(R.string.cancel_button), null)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ContextCompat.getDrawable(context, R.drawable.rounded_dialog_background)) // Example background
        // Notify_MT.Dlg_Style(dialog) // Optional: if Notify_MT is available
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val input = editText.text.toString()
            if (input.isEmpty()) {
                textInputLayout.error = context.getString(R.string.enter_something_error)
            } else {
                try {
                    val lineNumber = input.toInt()
                    if (lineNumber >= 1 && lineNumber <= editor.lineCount) {
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
}
