package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.DiagnosticResult
import org.eclipse.lsp4j.Diagnostic as Lsp4jDiagnostic

class DiagnosticAdapter {
    fun toAndroidIdeDiagnosticResult(lspDiagnostics: List<Lsp4jDiagnostic>): DiagnosticResult {
        // TODO: 将 LSP4J Diagnostic 转为 IDE 的 DiagnosticResult
        return DiagnosticResult.NO_UPDATE
    }
} 