package android.zero.studio.lsp.kotlin.adapters

import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.CompletionItemKind
import com.itsaky.androidide.lsp.models.InsertTextFormat
import com.itsaky.androidide.lsp.models.MatchLevel
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionItem as Lsp4jCompletionItem

class CompletionAdapter {
    fun toLsp4jCompletionParams(params: CompletionParams?): org.eclipse.lsp4j.CompletionParams {
        return org.eclipse.lsp4j.CompletionParams().apply {
            textDocument = org.eclipse.lsp4j.TextDocumentIdentifier(params?.file?.toUri().toString())
            position = org.eclipse.lsp4j.Position(params?.position?.line ?: 0, params?.position?.character ?: 0)
        }
    }

    fun toAndroidIdeCompletionResult(lspResult: Any): CompletionResult {
        val items = when (lspResult) {
            is CompletionList -> lspResult.items
            is List<*> -> lspResult.filterIsInstance<Lsp4jCompletionItem>()
            else -> emptyList()
        }
        val ideItems = items.map { lspItem ->
            com.itsaky.androidide.lsp.models.CompletionItem(
                ideLabel = lspItem.label ?: "",
                detail = lspItem.detail ?: "",
                insertText = lspItem.insertText,
                insertTextFormat = lspItem.insertTextFormat?.let { InsertTextFormat.valueOf(it.name) },
                sortText = lspItem.sortText,
                command = null, // 可适配 lspItem.command
                completionKind = lspItem.kind?.name?.let { CompletionItemKind.valueOf(it) } ?: CompletionItemKind.NONE,
                matchLevel = MatchLevel.NO_MATCH, // 可根据 label/prefix 计算
                additionalTextEdits = null, // 可适配 lspItem.additionalTextEdits
                data = null // 可适配 lspItem.data
            )
        }
        return CompletionResult(ideItems)
    }
} 