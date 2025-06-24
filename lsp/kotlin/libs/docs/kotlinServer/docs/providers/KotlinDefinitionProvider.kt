package android.zero.studio.lsp.kotlin.providers

import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.DefinitionAdapter
import org.eclipse.lsp4j.DefinitionParams as Lsp4jDefinitionParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.Position

/**
 * Kotlin 跳转定义服务 Provider。
 * 负责符号定义查找，对接底层 shared/server 的定义能力。
 */
class KotlinDefinitionProvider(
    private val textDocumentService: KotlinTextDocumentService,
    private val adapter: DefinitionAdapter
) {
    /**
     * 查找符号定义。
     * @param params 定义查找参数
     * @return 定义查找结果
     */
    suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        return try {
            val lspParams = Lsp4jDefinitionParams().apply {
                textDocument = TextDocumentIdentifier(params.file.toUri().toString())
                position = Position(params.position.line, params.position.character)
            }
            val lspResult = textDocumentService.definition(lspParams).get()
            adapter.toAndroidIdeDefinitionResult(lspResult)
        } catch (e: Exception) {
            DefinitionResult(emptyList())
        }
    }
} 