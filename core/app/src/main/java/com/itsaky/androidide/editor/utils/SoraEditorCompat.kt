package com.itsaky.androidide.editor.utils

import com.itsaky.androidide.models.Position
import com.itsaky.androidide.models.Range
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.Cursor
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.max
import kotlin.math.min

/**
 *  A collection of extension functions to add missing or compatibility APIs
 *  to the sora-editor library classes without modifying their source code.
 */

// --- Content Extensions for missing APIs ---

/**
 * Creates a CharPosition from a byte offset.
 * Assumes UTF-16 encoding where 1 char = 2 bytes.
 */
fun Content.getCharPositionForByte(byteOffset: Int): CharPosition {
    // A simple conversion from byte offset to char index for UTF-16
    val charIndex = byteOffset / 2
    return this.indexer.getCharPosition(charIndex)
}

/**
 * Gets the character index for a given Position object.
 */
fun Content.getCharIndex(position: Position): Int {
    return this.indexer.getCharIndex(position.line, position.column)
}


// --- Cursor Extensions for Multi-Cursor Simulation ---

/**
 * Returns the number of cursors. For the base sora-editor, this is always 1.
 */
fun Cursor.getCursorCount(): Int {
    return 1 // sora-editor is single-cursor
}

/**
 * Simulates adding a new cursor by extending the current selection to the new position.
 */
fun Cursor.addCursor(line: Int, column: Int) {
    val start = this.left()
    this.setRight(line, column)
    this.setLeft(start.line, start.column)
}

/**
 * Clears the current selection, placing the cursor at the end of the previous selection.
 */
fun Cursor.clearSelection() {
    val end = this.right()
    this.set(end.line, end.column)
}

/**
 * Simulates adding a new selection region by merging it with the existing selection.
 */
fun Cursor.addSelection(startLine: Int, startColumn: Int, endLine: Int, endColumn: Int) {
    if (!this.isSelected) {
        this.setLeft(startLine, startColumn)
        this.setRight(endLine, endColumn)
    } else {
        val currentStart = this.left()
        val currentEnd = this.right()

        // Find the earliest start and latest end to create a single large selection block
        val newStartLine = min(currentStart.line, startLine)
        val newStartColumn = if (newStartLine == currentStart.line) min(currentStart.column, startColumn) else if (newStartLine == startLine) startColumn else currentStart.column

        val newEndLine = max(currentEnd.line, endLine)
        val newEndColumn = if (newEndLine == currentEnd.line) max(currentEnd.column, endColumn) else if (newEndLine == endLine) endColumn else currentEnd.column

        this.setLeft(newStartLine, newStartColumn)
        this.setRight(newEndLine, newEndColumn)
    }
}


// --- CodeEditor Extensions for Convenience ---

/**
 * Sets the editor selection using a [Position].
 */
fun CodeEditor.setSelection(position: Position) {
    setSelection(position.line, position.column)
}

/**
 * Sets the editor selection using a [Range].
 */
fun CodeEditor.setSelection(range: Range) {
    setSelectionRegion(range.start.line, range.start.column, range.end.line, range.end.column)
}