package com.itsaky.androidide.formatprovider.treesitter

import com.itsaky.androidide.editor.language.LanguageAnalysisBridge
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.formatprovider.CodeFormatter
import com.itsaky.androidide.treesitter.*

/**
 * 一个复杂的代码格式化器，利用 Tree-sitter 解析库。
 * 这个基类提供了遍历语法树和应用由 Tree-sitter 查询定义的缩进规则的核心逻辑。
 * 子类只需要提供特定于语言的 TSLanguage 对象和格式化查询。
 *
 * An advanced code formatter that leverages the Tree-sitter parsing library.
 * This base class provides the core logic for traversing a syntax tree and applying indentation rules
 * defined by a Tree-sitter query. Subclasses only need to provide the language-specific
 * TSLanguage object and the formatting query.
 */
abstract class TreeSitterFormatter(
    private val languageType: String,
    private val formatQuery: String,
    private val indentSize: Int = 4,
    private val editor: IDEEditor? = null // 接收IDEEditor实例（用于获取Context）
) : CodeFormatter {

    // 修复：调用getLanguage时传递editor参数
    private val language: TSLanguage? by lazy { 
        editor?.let { LanguageAnalysisBridge.getLanguage(languageType, it) } 
    }

    override fun format(source: String): String {
        val lang = language ?: return source // 如果语言解析器不可用，则回退
        val parser = TSParser.create().apply { setLanguage(lang) }
        val tree: TSTree = parser.parseString(null, source)

        try {
            return formatWithTree(source, tree)
        } catch (e: Exception) {
            e.printStackTrace()
            return source
        } finally {
            tree.close()
            parser.close()
        }
    }

    private fun formatWithTree(source: String, tree: TSTree): String {
        val indentString = " ".repeat(indentSize)
        val sourceLines = source.lines()
        val indentAdjustments = IntArray(sourceLines.size)

        val query = TSQuery.create(language!!, formatQuery)
        TSQueryCursor.create().use { cursor ->
            cursor.exec(query, tree.rootNode)
            var match = cursor.nextMatch()
            while (match != null) {
                for (capture in match.captures) {
                    val node = capture.node
                    val startLine = node.getStartPoint().row
                    val endLine = node.getEndPoint().row
                    val captureName = query.getCaptureNameForId(capture.index)

                    when (captureName) {
                        "indent" -> {
                            for (i in (startLine + 1)..endLine) {
                                if (i < indentAdjustments.size) indentAdjustments[i]++
                            }
                            // 块的结束括号不应增加缩进
                            if (node.type.contains("block") || node.type.contains("body") || node.type.endsWith("}")) {
                                if (endLine < indentAdjustments.size) indentAdjustments[endLine]--
                            }
                        }
                        "indent.start" -> { // 对于像 `{` 这样的符号，其后的所有行都增加缩进
                            for (i in (startLine + 1) until indentAdjustments.size) {
                                indentAdjustments[i]++
                            }
                        }
                        "indent.end" -> { // 对于像 `}` 这样的符号，从当前行开始的所有行都减少缩进
                            for (i in startLine until indentAdjustments.size) {
                                indentAdjustments[i]--
                            }
                        }
                        "indent.branch" -> { // 对于像 `else if` 或 `case` 这样的分支
                            if (startLine < indentAdjustments.size) indentAdjustments[startLine]--
                            for (i in (startLine + 1)..endLine) {
                                if (i < indentAdjustments.size) indentAdjustments[i]++
                            }
                        }
                        "ignore" -> {
                             for (i in startLine..endLine) {
                                if (i < indentAdjustments.size) indentAdjustments[i] = Int.MIN_VALUE // 标记为忽略
                            }
                        }
                    }
                }
                match = cursor.nextMatch()
            }
        }
        query.close()

        val formatted = StringBuilder()
        var currentIndentLevel = 0
        sourceLines.forEachIndexed { index, line ->
             if (indentAdjustments.getOrElse(index) { 0 } == Int.MIN_VALUE) {
                formatted.append(line).append('\n') 
                return@forEachIndexed
            }
            
            currentIndentLevel += indentAdjustments.getOrElse(index) { 0 }
            currentIndentLevel = currentIndentLevel.coerceAtLeast(0)

            val trimmedLine = line.trim()
            if (trimmedLine.isNotEmpty()) {
                formatted.append(indentString.repeat(currentIndentLevel))
                formatted.append(trimmedLine)
            }
            formatted.append('\n')
        }

        return formatted.toString().trimEnd('\n')
    }

    // 重载构造函数：兼容无editor场景（若有默认Context获取方式）
    constructor(
        languageType: String,
        formatQuery: String,
        indentSize: Int = 4
    ) : this(languageType, formatQuery, indentSize, null)
}
