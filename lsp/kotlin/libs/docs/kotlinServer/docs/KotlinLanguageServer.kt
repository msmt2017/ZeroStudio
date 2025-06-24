package android.zero.studio.lsp.kotlin

import com.itsaky.androidide.lsp.api.*
import com.itsaky.androidide.lsp.models.*
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.api.Project
import java.nio.file.Path
import android.zero.studio.lsp.kotlin.providers.KotlinCompletionProvider
import android.zero.studio.lsp.kotlin.providers.KotlinDiagnosticProvider
import android.zero.studio.lsp.kotlin.providers.KotlinDefinitionProvider
import android.zero.studio.lsp.kotlin.providers.KotlinReferenceProvider
import android.zero.studio.lsp.kotlin.providers.KotlinFormatter
import org.javacs.kt.KotlinLanguageServer as JavacsKotlinLanguageServer
import org.javacs.kt.KotlinTextDocumentService
import android.zero.studio.lsp.kotlin.adapters.*
import android.zero.studio.lsp.kotlin.providers.*

// Provider 引用后续补充
// import android.zero.studio.lsp.kotlin.providers.*

/**
 * Kotlin 语言服务主类，实现 ILanguageServer 接口。
 * 负责调度各 Provider，实现 LSP 相关功能。
 */
class KotlinLanguageServer : ILanguageServer {
    override val serverId: String = SERVER_ID
    override var client: ILanguageClient? = null

    private val javacsServer = JavacsKotlinLanguageServer()
    private val textDocumentService: KotlinTextDocumentService = javacsServer.getTextDocumentService()

    // 适配器实例
    private val completionAdapter = CompletionAdapter()
    private val diagnosticAdapter = DiagnosticAdapter()
    private val definitionAdapter = DefinitionAdapter()
    private val referenceAdapter = ReferenceAdapter()
    private val formattingAdapter = FormattingAdapter()
    private val hoverAdapter = HoverAdapter()

    // Provider 注入依赖
    private val completionProvider = KotlinCompletionProvider(textDocumentService, completionAdapter)
    private val diagnosticProvider = KotlinDiagnosticProvider(textDocumentService, diagnosticAdapter)
    private val definitionProvider = KotlinDefinitionProvider(textDocumentService, definitionAdapter)
    private val referenceProvider = KotlinReferenceProvider(textDocumentService, referenceAdapter)
    private val formatter = KotlinFormatter(textDocumentService, formattingAdapter)
    private val hoverProvider = KotlinHoverProvider(textDocumentService, hoverAdapter)

    companion object {
        const val SERVER_ID = "ide.lsp.kotlin"
    }

    override fun shutdown() {
        // TODO: 释放资源
    }

    override fun connectClient(client: ILanguageClient?) {
        this.client = client
    }

    override fun applySettings(settings: IServerSettings?) {
        // TODO: 应用设置到各 Provider
    }

    override fun setupWithProject(project: Project) {
        // TODO: 项目初始化，通知各 Provider
    }

    override fun complete(params: CompletionParams?): CompletionResult {
        return completionProvider.complete(params)
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        return referenceProvider.findReferences(params)
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        return definitionProvider.findDefinition(params)
    }

    override suspend fun expandSelection(params: ExpandSelectionParams): Range {
        // TODO: 实现智能选区
        return params.selection
    }

    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
        // TODO: 实现签名帮助
        return SignatureHelp(emptyList(), -1, -1)
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        return diagnosticProvider.analyze(file)
    }

    override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        return formatter.formatCode(params)
    }

    override fun handleFailure(failure: LSPFailure?): Boolean {
        // TODO: 错误处理
        return false
    }
} 