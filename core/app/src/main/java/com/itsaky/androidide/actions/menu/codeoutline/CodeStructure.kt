package com.itsaky.androidide.actions.menu.codeoutline

import com.itsaky.androidide.models.Range

/**
 * Represents a symbol (like a class, method, or field) found in the code for the outline view.
 *
 * @param name The primary name of the symbol (e.g., "FragmentStateAdapter").
 * @param kind The type of the symbol (e.g., CLASS, METHOD).
 * @param detail Secondary information, like method parameters or a field's type.
 * @param description Modifiers and return type, e.g., "public static final String".
 * @param range The full range of the symbol's declaration in the code.
 * @param selectionRange The range of just the symbol's name for precise selection and renaming.
 */
data class CdSymb(
    val name: String,
    val kind: SymbolKind,
    val detail: String = "",
    val description: String = "",
    val range: Range,
    val selectionRange: Range
)

enum class SymbolKind {
    PACKAGE,
    IMPORT,
    CLASS,
    INTERFACE,
    ENUM,
    METHOD,
    FUNCTION,
    FIELD,
    PROPERTY,
    UNKNOWN
}