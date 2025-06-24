package org.javacs.kt

import java.nio.file.Path

interface ILanguageServer {
    val serverId: String?
    fun shutdown()
    fun connectClient(client: ILanguageClient?)
    val client: ILanguageClient?
    fun applySettings(settings: IServerSettings?)
    fun setupWithProject(project: Any)
    fun complete(params: Any?): Any
    suspend fun findReferences(params: Any): Any
    suspend fun findDefinition(params: Any): Any
    suspend fun expandSelection(params: Any): Any
    suspend fun signatureHelp(params: Any): Any
    suspend fun analyze(file: Path): Any
    fun formatCode(params: Any?): Any {
        return Any()
    }
    fun handleFailure(failure: Any?): Boolean {
        return false
    }
} 