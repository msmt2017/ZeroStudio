package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.CodeFormatResult
import org.eclipse.lsp4j.TextEdit

class FormattingAdapter {
    fun toAndroidIdeFormatResult(lspResult: Any): CodeFormatResult {
        // TODO: 实现 LSP4J TextEdit -> AndroidIDE CodeFormatResult 的适配
        return CodeFormatResult(false, mutableListOf())
    }
} 