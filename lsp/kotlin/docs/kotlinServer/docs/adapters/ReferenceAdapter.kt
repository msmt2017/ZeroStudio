package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.ReferenceResult
import org.eclipse.lsp4j.Location

class ReferenceAdapter {
    fun toAndroidIdeReferenceResult(lspResult: Any): ReferenceResult {
        // TODO: 实现 LSP4J Location -> AndroidIDE ReferenceResult 的适配
        return ReferenceResult(emptyList())
    }
} 