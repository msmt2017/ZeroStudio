package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.CompletionAdapter

/**
 * Kotlin 补全服务 Provider。
 * 负责处理补全请求，对接底层 shared/server 的补全能力。
 */
class KotlinCompletionProvider(
    private val textDocumentService: KotlinTextDocumentService,
    private val adapter: CompletionAdapter
) {
    /**
     * 处理补全请求。
     * @param params 补全参数
     * @return 补全结果
     */
    fun complete(params: CompletionParams?): CompletionResult {
        return try {
            val lspParams = adapter.toLsp4jCompletionParams(params)
            val lspResult = textDocumentService.completion(lspParams).get()
            adapter.toAndroidIdeCompletionResult(lspResult)
        } catch (e: Exception) {
            // 日志记录
            CompletionResult.EMPTY
        }
    }
} 