package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.FormatCodeParams
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.FormattingAdapter
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.FormattingOptions

/**
 * Kotlin 格式化服务 Provider。
 * 负责代码格式化，对接底层 shared/server 的格式化能力。
 */
class KotlinFormatter(
    private val textDocumentService: KotlinTextDocumentService,
    private val adapter: FormattingAdapter
) {
    /**
     * 格式化代码。
     * @param params 格式化参数
     * @return 格式化结果
     */
    fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        return try {
            val lspParams = DocumentFormattingParams().apply {
                textDocument = TextDocumentIdentifier(params?.file?.toUri().toString())
                options = FormattingOptions(params?.tabSize ?: 4, params?.insertSpaces ?: true)
            }
            val lspResult = textDocumentService.formatting(lspParams).get()
            adapter.toAndroidIdeFormatResult(lspResult)
        } catch (e: Exception) {
            CodeFormatResult(false, mutableListOf())
        }
    }
} 