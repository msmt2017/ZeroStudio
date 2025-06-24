package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.HoverResult
import org.eclipse.lsp4j.Hover

class HoverAdapter {
    fun toAndroidIdeHoverResult(lspResult: Any): HoverResult {
        // TODO: 实现 LSP4J Hover -> AndroidIDE HoverResult 的适配
        return HoverResult.EMPTY
    }
} 