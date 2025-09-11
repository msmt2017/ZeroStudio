package org.javacs.kt

import com.itsaky.androidide.lsp.api.ILanguageClient as IAndroidIdeLanguageClient
import com.itsaky.androidide.lsp.models.DiagnosticResult
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.LanguageClient
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture

/**
 * An adapter that implements the lsp4j LanguageClient interface and forwards
 * notifications to the AndroidIDE ILanguageClient interface.
 */
class LanguageClientAdapter(private val androidIdeClient: IAndroidIdeLanguageClient?) : LanguageClient {
    private val log = LoggerFactory.getLogger(LanguageClientAdapter::class.java)

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams) {
        log.debug("Forwarding ${diagnostics.diagnostics.size} diagnostics for ${diagnostics.uri} to AndroidIDE client")
        val result = diagnostics.toIdeDiagnosticResult()
        androidIdeClient?.publishDiagnostics(result)
    }

    override fun telemetryEvent(obj: Any?) {
        log.trace("telemetryEvent: {}", obj)
    }

    override fun showMessage(messageParams: MessageParams) {
        log.info("showMessage: [${messageParams.type}] ${messageParams.message}")
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams): CompletableFuture<MessageActionItem> {
        log.info("showMessageRequest: ${requestParams.message}")
        return CompletableFuture.completedFuture(null)
    }

    override fun logMessage(message: MessageParams) {
        log.trace("logMessage: [${message.type}] ${message.message}")
    }
}