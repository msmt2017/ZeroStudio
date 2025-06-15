// File: android/zero/mcp/handlers/TabFileHandler.kt
package android.zero.mcp.handlers

import android.content.Context
import android.zero.mcp.LogManager
import android.zero.mcp.protocol.JsonAdapters
import android.zero.mcp.protocol.McpCommand
import android.zero.mcp.protocol.McpResponse
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.atomic.AtomicReference

/**
 * [TabFileHandler] processes MCP commands related to the currently open file in the editor (TabFile).
 * It uses the [CodeEditorProvider] to interact with the [CodeEditor] instance and its [Content].
 *
 * @param scope The CoroutineScope for launching asynchronous operations.
 * @param context The Android application context.
 * @param codeEditorProviderRef An [AtomicReference] to the [CodeEditorProvider] instance,
 * which is set by the host application ([MainActivity]).
 * @author Android Zero
 */
class TabFileHandler(
    private val scope: CoroutineScope,
    private val context: Context,
    private val codeEditorProviderRef: AtomicReference<CodeEditorProvider?>
) : CommandHandler {

    private val TAG = "TabFileHandler"

    override suspend fun handleCommand(command: McpCommand, responseChannel: SendChannel<String>) {
        scope.launch {
            val id = command.id
            val contextId = command.contextId
            val editorProvider = codeEditorProviderRef.get()

            if (editorProvider == null) {
                sendError(id, contextId, "CodeEditorProvider is not set. Editor functionality unavailable.", responseChannel)
                return@launch
            }

            val operation = command.instruction.split(":").last().removePrefix("#")

            LogManager.addLog("TabFileHandler: Handling operation '$operation'", "DEBUG", TAG)

            when (operation) {
                "getFile" -> {
                    handleGetFile(id, contextId, editorProvider, responseChannel)
                }
                "getLine" -> {
                    val lineRange = command.args["lineRange"]?.jsonPrimitive?.content
                    if (lineRange == null) {
                        sendError(id, contextId, "Missing 'lineRange' argument for tabfile.getLine.", responseChannel)
                        return@launch
                    }
                    handleGetLine(id, contextId, lineRange, editorProvider, responseChannel)
                }
                "getCursor" -> {
                    handleGetCursor(id, contextId, editorProvider, responseChannel)
                }
                "searchTabFile" -> {
                    val searchTerm = command.args["searchTerm"]?.jsonPrimitive?.content
                    if (searchTerm == null) {
                        sendError(id, contextId, "Missing 'searchTerm' argument for tabfile.searchTabFile.", responseChannel)
                        return@launch
                    }
                    handleSearchTabFile(id, contextId, searchTerm, editorProvider, responseChannel)
                }
                "getFunction" -> {
                    val functionName = command.args["functionName"]?.jsonPrimitive?.content
                    val language = command.args["language"]?.jsonPrimitive?.content
                    if (functionName == null) {
                        sendError(id, contextId, "Missing 'functionName' argument for tabfile.getFunction.", responseChannel)
                        return@launch
                    }
                    handleGetFunction(id, contextId, functionName, language, editorProvider, responseChannel)
                }
                else -> {
                    sendError(id, contextId, "Unknown tabfile operation: $operation", responseChannel)
                }
            }
        }
    }

    /**
     * Handles retrieving the full content of the currently open file.
     */
    private suspend fun handleGetFile(id: String, contextId: String?, editorProvider: CodeEditorProvider, responseChannel: SendChannel<String>) {
        editorProvider.runOnEditorUi { editor, content ->
            val fileContent = content.toString()
            scope.launch {
                sendSuccess(id, contextId, fileContent, responseChannel)
            }
        }
    }

    /**
     * Handles retrieving specific line(s) of content from the currently open file.
     */
    private suspend fun handleGetLine(id: String, contextId: String?, lineRange: String, editorProvider: CodeEditorProvider, responseChannel: SendChannel<String>) {
        val (startLineNum, endLineNum) = parseLineRange(lineRange)

        if (startLineNum == -1) {
            sendError(id, contextId, "Invalid line range format: $lineRange. Expected 'line' or 'start-end'.", responseChannel)
            return
        }

        editorProvider.runOnEditorUi { editor, content ->
            scope.launch {
                try {
                    val actualEndLineNum = if (endLineNum == -1) startLineNum else endLineNum
                    if (startLineNum < 1 || actualEndLineNum > content.lineCount || startLineNum > actualEndLineNum) {
                        sendError(id, contextId, "Line number out of bounds or invalid range. Total lines: ${content.lineCount}", responseChannel)
                        return@launch
                    }

                    val resultLines = (startLineNum..actualEndLineNum).map { content.getLineString(it - 1) } // 0-indexed content
                    sendSuccess(id, contextId, resultLines.joinToString("\n"), responseChannel)
                } catch (e: Exception) {
                    sendError(id, contextId, "Error getting line content: ${e.message}", responseChannel)
                }
            }
        }
    }

    /**
     * Parses a line range string (e.g., "5" or "5-10").
     * Returns Pair<startLine, endLine>, where endLine is -1 if single line.
     */
    private fun parseLineRange(range: String): Pair<Int, Int> {
        return if (range.contains("-")) {
            val parts = range.split("-")
            if (parts.size == 2) {
                val start = parts[0].trim().toIntOrNull() ?: -1
                val end = parts[1].trim().toIntOrNull() ?: -1
                Pair(start, end)
            } else {
                Pair(-1, -1)
            }
        } else {
            val line = range.trim().toIntOrNull() ?: -1
            Pair(line, -1)
        }
    }

    /**
     * Handles retrieving the content of the line where the cursor is.
     */
    private suspend fun handleGetCursor(id: String, contextId: String?, editorProvider: CodeEditorProvider, responseChannel: SendChannel<String>) {
        editorProvider.runOnEditorUi { editor, content ->
            val cursorPosition = editorProvider.getCursorPosition()
            if (cursorPosition != null) {
                val line = cursorPosition.first
                val column = cursorPosition.second
                val lineContent = if (line >= 0 && line < content.lineCount) {
                    content.getLineString(line)
                } else {
                    ""
                }
                scope.launch {
                    sendSuccess(id, contextId, "Line ${line + 1}, Col $column: $lineContent", responseChannel)
                }
            } else {
                scope.launch {
                    sendError(id, contextId, "Cursor position not available.", responseChannel)
                }
            }
        }
    }

    /**
     * Handles searching for a term within the currently open file.
     */
    private suspend fun handleSearchTabFile(id: String, contextId: String?, searchTerm: String, editorProvider: CodeEditorProvider, responseChannel: SendChannel<String>) {
        editorProvider.runOnEditorUi { editor, content ->
            scope.launch {
                val results = mutableListOf<JsonObject>()
                val fullContent = content.toString()

                // Perform a simple case-sensitive search for now.
                // For more advanced search (regex, case-insensitive),
                // you would need to implement more sophisticated logic.
                var currentIndex = 0
                while (currentIndex != -1) {
                    currentIndex = fullContent.indexOf(searchTerm, currentIndex, ignoreCase = false)
                    if (currentIndex != -1) {
                        // Convert character index to line and column
                        val charPos = content.getCharPosition(currentIndex) // assuming Content has getCharPosition
                        val line = charPos?.line ?: 0
                        val column = charPos?.column ?: 0

                        results.add(buildJsonObject {
                            put("line", line + 1) // 1-indexed line number
                            put("column", column)
                            put("text", searchTerm)
                            put("startIndexInContent", currentIndex)
                            put("endIndexInContent", currentIndex + searchTerm.length)
                        })
                        currentIndex += searchTerm.length // Move past the found term
                    }
                }
                sendSuccess(id, contextId, JsonAdapters.defaultJson.encodeToString(buildJsonArray { results.forEach { add(it) } }), responseChannel)
            }
        }
    }

    /**
     * Handles retrieving the content of a specific function within the currently open file.
     * This is a heuristic search, not a semantic parser.
     */
    private suspend fun handleGetFunction(id: String, contextId: String?, functionName: String, language: String?, editorProvider: CodeEditorProvider, responseChannel: SendChannel<String>) {
        editorProvider.runOnEditorUi { editor, content ->
            scope.launch {
                val fullContent = content.toString()
                val functionCode = findFunctionCodeHeuristic(fullContent, functionName, language ?: "kotlin")

                if (functionCode != null) {
                    val firstLineIndex = fullContent.indexOf(functionCode)
                    val startLine = if (firstLineIndex != -1) content.getCharPosition(firstLineIndex)?.line?.plus(1) ?: 1 else 1
                    val endLine = if (firstLineIndex != -1) content.getCharPosition(firstLineIndex + functionCode.length)?.line?.plus(1) ?: content.lineCount else content.lineCount

                    val metadata = buildJsonObject {
                        put("startLine", startLine)
                        put("endLine", endLine)
                    }
                    val response = McpResponse(id, "tabfile.getFunction.success", JsonPrimitive(functionCode), true, contextId, metadata)
                    responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
                    LogManager.addLog("TabFileHandler: Found function '$functionName'.", "INFO", TAG)
                } else {
                    sendError(id, contextId, "Function '$functionName' not found or could not be parsed heuristically.", responseChannel)
                }
            }
        }
    }

    /**
     * Heuristically attempts to find the code block for a given function name.
     * This is a simplified regex-based approach and may not be accurate for all cases
     * (e.g., nested functions, functions with complex signatures, comments, etc.).
     *
     * @param fullContent The entire file content.
     * @param functionName The name of the function to find.
     * @param language The programming language hint ("java" or "kotlin").
     * @return The function's code block as a String, or null if not found.
     */
    private fun findFunctionCodeHeuristic(fullContent: String, functionName: String, language: String): String? {
        val regexPattern = when (language.toLowerCase()) {
            "kotlin" -> Regex("""\b(?:fun|override fun|suspend fun)\s+$functionName\s*\(([^)]*)\)\s*(?::\s*\S+)?\s*\{([^}]*)\}""", RegexOption.DOT_ALL)
            "java" -> Regex("""\b(?:public|private|protected|static|final|abstract|synchronized|native|transient|volatile)?\s*\S+\s+$functionName\s*\(([^)]*)\)\s*\{([^}]*)\}""", RegexOption.DOT_ALL)
            else -> Regex("""\b\S+\s+$functionName\s*\(([^)]*)\)\s*\{([^}]*)\}""", RegexOption.DOT_ALL) // Generic fallback
        }

        val matchResult = regexPattern.find(fullContent)
        if (matchResult != null) {
            // Reconstruct the matched function block. This is a simplification.
            // A more robust solution would involve parsing the AST.
            val functionDeclarationGroup = matchResult.groupValues[0] // Entire matched string
            return functionDeclarationGroup
        }
        return null
    }

    /**
     * Helper to send a successful MCP response.
     */
    private suspend fun sendSuccess(id: String, contextId: String?, data: String, responseChannel: SendChannel<String>, metadata: kotlinx.serialization.json.JsonObject? = null) {
        val response = McpResponse(id, "tabfile.success", JsonPrimitive(data), true, contextId, metadata)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("TabFileHandler: Success for $id.", "INFO", TAG)
    }

    /**
     * Helper to send an error MCP response.
     */
    private suspend fun sendError(id: String, contextId: String?, errorMessage: String, responseChannel: SendChannel<String>) {
        val response = McpResponse(id, "tabfile.error", JsonPrimitive(errorMessage), false, contextId)
        responseChannel.send(JsonAdapters.defaultJson.encodeToString(response))
        LogManager.addLog("TabFileHandler: Error for $id: $errorMessage", "ERROR", TAG)
    }
}
