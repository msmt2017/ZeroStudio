package com.itsaky.androidide.actions.menu.codeoutline

import com.itsaky.androidide.actions.menu.codeoutline.CdSymb
import com.itsaky.androidide.actions.menu.codeoutline.SymbolKind
import com.itsaky.androidide.editor.language.LanguageAnalysisBridge
import com.itsaky.androidide.editor.ui.IDEEditor
import com.itsaky.androidide.editor.utils.getCharPositionForByte
import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.treesitter.*
import io.github.rosemoe.sora.text.Content

object CodeOutlineParser {

    private fun TSNode.getText(content: Content): String = content.subSequence(this.startByte / 2, this.endByte / 2).toString()

    private fun TSNode.toLspRange(content: Content): Range {
        val startPos = content.getCharPositionForByte(this.startByte)
        val endPos = content.getCharPositionForByte(this.endByte)
        return Range(Position(startPos.line, startPos.column), Position(endPos.line, endPos.column))
    }

    private val javaQuery by lazy {
        """
        (package_declaration (scoped_identifier) @package)
        (import_declaration (scoped_identifier) @import)
        (class_declaration
            modifiers: (_) @modifiers
            name: (identifier) @name
            body: (class_body) @body) @class
        (interface_declaration
            modifiers: (_) @modifiers
            name: (identifier) @name) @interface
        (enum_declaration
            modifiers: (_) @modifiers
            name: (identifier) @name) @enum
        (method_declaration
            modifiers: (_) @modifiers
            type: (_) @returnType
            name: (identifier) @name
            parameters: (formal_parameters) @params) @method
        (field_declaration
            modifiers: (_) @modifiers
            type: (_) @type
            declarator: (variable_declarator
                name: (identifier) @name)) @field
        """.trimIndent()
    }
    
    private val kotlinQuery by lazy {
         """
        (package_header (scoped_identifier) @package)
        (import_header (import_path) @import)
        (class_declaration
            modifiers: (_) @modifiers
            name: (simple_identifier) @name) @class
        (function_declaration
            modifiers: (_) @modifiers
            name: (simple_identifier) @name
            value_parameters: (function_value_parameters) @params
            return_type: (type_reference)? @returnType) @function
        (property_declaration
            modifiers: (_) @modifiers
            (variable_declaration
                name: (simple_identifier) @name
                (type_reference)? @type
            )
        ) @property
        """.trimIndent()
    }
    
    fun getCodeStructure(editor: IDEEditor): List<CdSymb> {
        val fileExtension = editor.file?.extension?.lowercase() ?: return emptyList()
        val lang = LanguageAnalysisBridge.getTsLanguage(editor) ?: return emptyList()
        val tree = LanguageAnalysisBridge.getSyntaxTree(editor) ?: return emptyList()
        val content = editor.text
        
        val queryString = when (fileExtension) {
            "java" -> javaQuery
            "kt", "kts" -> kotlinQuery
            else -> return emptyList()
        }
        
        val query = TSQuery.create(lang, queryString)
        val cursor = TSQueryCursor.create()
        cursor.exec(query, tree.rootNode)
        
        val symbols = mutableListOf<CdSymb>()
        var match = cursor.nextMatch()
        
        while (match != null) {
            val symbol = parseMatch(match, query, content)
            if (symbol != null) {
                symbols.add(symbol)
            }
            match = cursor.nextMatch()
        }
        
        cursor.close()
        query.close()
        
        return symbols.sortedBy { it.range.start.line }
    }

    private fun parseMatch(match: TSQueryMatch, query: TSQuery, content: Content): CdSymb? {
        val captures = match.captures.associate { query.getCaptureNameForId(it.index) to it.node }
        // Use the full match node as the root to ensure we get the entire declaration range
        val rootNode = match.captures.firstOrNull()?.node?.let {
            var n = it
            while (n.parent != null && (n.parent?.startByte == n.startByte && n.parent?.endByte == n.endByte)) {
                n = n.parent!!
            }
            n
        } ?: return null

        val nameNode = captures["name"] ?: captures["package"] ?: captures["import"] ?: return null
        val name = nameNode.getText(content)
        
        // The first capture name determines the kind of the symbol
        val kindStr = query.getCaptureNameForId(match.captures.first().index)
        
        val kind = when (kindStr) {
            "package" -> SymbolKind.PACKAGE
            "import" -> SymbolKind.IMPORT
            "class" -> SymbolKind.CLASS
            "interface" -> SymbolKind.INTERFACE
            "enum" -> SymbolKind.ENUM
            "method" -> SymbolKind.METHOD
            "function" -> SymbolKind.FUNCTION
            "field" -> SymbolKind.FIELD
            "property" -> SymbolKind.PROPERTY
            else -> SymbolKind.UNKNOWN
        }
        
        if (kind == SymbolKind.PACKAGE || kind == SymbolKind.IMPORT) {
             return CdSymb(
                name = rootNode.getText(content), // For package/import, show the full path
                kind = kind,
                range = rootNode.toLspRange(content),
                selectionRange = nameNode.toLspRange(content)
            )
        }
        
        val modifiers = captures["modifiers"]?.getText(content)?.replace(Regex("\\s+"), " ")?.trim() ?: ""
        val params = captures["params"]?.getText(content) ?: ""
        val type = captures["type"]?.getText(content) ?: captures["returnType"]?.getText(content)
        
        val description = buildDescription(modifiers, type)
        
        return CdSymb(
            name = name,
            kind = kind,
            detail = params,
            description = description,
            range = rootNode.toLspRange(content),
            selectionRange = nameNode.toLspRange(content)
        )
    }

    private fun buildDescription(modifiers: String, type: String?): String {
        val parts = mutableListOf<String>()
        if (modifiers.isNotEmpty()) parts.add(modifiers)
        if (!type.isNullOrEmpty() && type != "Unit" && type != "void") {
            parts.add(": $type") // Use colon for better readability
        }
        return parts.joinToString(" ")
    }
}