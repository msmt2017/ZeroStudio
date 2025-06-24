package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.ReferenceAdapter
import org.eclipse.lsp4j.ReferenceParams as Lsp4jReferenceParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.Position

/**
 * Kotlin 引用查找服务 Provider。
 * 负责符号引用查找，对接底层 shared/server 的引用能力。
 */
class KotlinReferenceProvider(
    private val textDocumentService: KotlinTextDocumentService,
    private val adapter: ReferenceAdapter
) {
    /**
     * 查找符号引用。
     * @param params 引用查找参数
     * @return 引用查找结果
     */
    suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        return try {
            val lspParams = Lsp4jReferenceParams().apply {
                textDocument = TextDocumentIdentifier(params.file.toUri().toString())
                position = Position(params.position.line, params.position.character)
                context = org.eclipse.lsp4j.ReferenceContext(true)
            }
            val lspResult = textDocumentService.references(lspParams).get()
            adapter.toAndroidIdeReferenceResult(lspResult)
        } catch (e: Exception) {
            ReferenceResult(emptyList())
        }
    }
} 