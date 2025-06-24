package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.HoverParams
import com.itsaky.androidide.lsp.models.HoverResult
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.HoverAdapter
import org.eclipse.lsp4j.HoverParams as Lsp4jHoverParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.Position

/**
 * Kotlin 悬停提示服务 Provider。
 * 负责悬停信息查询，对接底层 shared/server 的悬停能力。
 */
class KotlinHoverProvider(
    private val textDocumentService: KotlinTextDocumentService,
    private val adapter: HoverAdapter
) {
    /**
     * 查询悬停信息。
     * @param params 悬停参数
     * @return 悬停结果
     */
    suspend fun hover(params: HoverParams): HoverResult {
        return try {
            val lspParams = Lsp4jHoverParams().apply {
                textDocument = TextDocumentIdentifier(params.file.toUri().toString())
                position = Position(params.position.line, params.position.character)
            }
            val lspResult = textDocumentService.hover(lspParams).get()
            adapter.toAndroidIdeHoverResult(lspResult)
        } catch (e: Exception) {
            HoverResult.EMPTY
        }
    }
} 