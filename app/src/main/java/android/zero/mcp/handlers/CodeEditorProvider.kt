// File: android/zero/mcp/handlers/CodeEditorProvider.kt
package android.zero.mcp.handlers

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * An interface for the host application to provide access to the [CodeEditor] instance
 * and related functionalities to the MCP service. This decouples the MCP service
 * from the Android UI component lifecycle.
 */
interface CodeEditorProvider {

    /**
     * Provides the currently active [CodeEditor] instance.
     * @return The [CodeEditor] instance, or `null` if no editor is active.
     */
    fun getCodeEditor(): CodeEditor?

    /**
     * Provides the [Content] of the currently active [CodeEditor].
     * @return The [Content] instance, or `null` if no editor is active.
     */
    fun getCodeEditorContent(): Content?

    /**
     * Provides the absolute path of the file currently open in the [CodeEditor].
     * @return The file path as a [String], or `null` if no file is open.
     */
    fun getOpenedFilePath(): String?

    /**
     * Requests the [CodeEditor] to perform a specific action on its UI thread.
     * This is crucial for UI-related operations that must run on the main thread.
     *
     * @param action A lambda function containing the UI operation.
     */
    fun runOnEditorUi(action: (editor: CodeEditor, content: Content) -> Unit)

    /**
     * Gets the current cursor position within the editor.
     * @return A [Pair] where first is line and second is column, or `null` if no editor is active.
     */
    fun getCursorPosition(): Pair<Int, Int>?

    /**
     * Gets the currently selected text range within the editor.
     * @return A [Triple] where first is start line, second is start column, and third is end column.
     * Returns `null` if no selection or no editor is active.
     */
    fun getSelectionRange(): Triple<Int, Int, Int>?
}
