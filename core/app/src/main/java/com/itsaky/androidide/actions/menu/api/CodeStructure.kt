package com.itsaky.androidide.actions.menu.api

import com.itsaky.androidide.models.Range

/**
 * Represents a code snippet template.
 * @param name The user-friendly name displayed in the selection dialog (e.g., "Public Static Method").
 * @param template The code template itself. Uses %s for name substitution and $0 for final cursor position.
 * @param description A brief description of what the snippet does.
 * @param requiresNameInput True if the template contains a %s and needs the user to provide a name.
 */
data class CodeSnippet(
    val name: String,
    val template: String,
    val description: String,
    val requiresNameInput: Boolean = template.contains("%s")
)

/**
 * Represents a symbol (class, method, field, etc.) extracted from the source code.
 * @param name The name of the symbol.
 * @param detail Additional details, like method parameters or field type.
 * @param kind The kind of symbol (e.g., "Class", "Method").
 * @param range The full range of the symbol's declaration in the code.
 * @param selectionRange The range of just the symbol's name, for highlighting and renaming.
 * @param bodyRange The range of the symbol's body (e.g., inside the {} of a method), if applicable.
 */
data class CodeSymbol(
    val name: String,
    val detail: String,
    val kind: String,
    val range: Range,
    val selectionRange: Range,
    val bodyRange: Range? = null
)