package org.javacs.kt

interface ILanguageClient {
    fun publishDiagnostics(result: Any)
    fun getDiagnosticAt(file: java.io.File, line: Int, column: Int): Any?
    fun performCodeAction(params: Any)
    fun performCodeAction(actionItem: Any) {
        if (actionItem == null) return
        performCodeAction(actionItem)
    }
    fun performCodeAction(file: java.io.File, actionItem: Any) {
        performCodeAction(actionItem)
    }
    fun showDocument(params: Any): Any
    fun showLocations(locations: List<Any>)
} 