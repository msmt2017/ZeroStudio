package org.javacs.kt

import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either 
import org.eclipse.lsp4j.jsonrpc.services.JsonDelegate
import org.eclipse.lsp4j.services.LanguageClient as Lsp4jLanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer as Lsp4jLanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import org.eclipse.lsp4j.services.NotebookDocumentService

import java.io.Closeable
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture

import kotlinx.coroutines.future.await

import org.javacs.kt.command.ALL_COMMANDS
import org.javacs.kt.database.DatabaseService
import org.javacs.kt.progress.LanguageClientProgress
import org.javacs.kt.progress.Progress
import org.javacs.kt.semantictokens.semanticTokensLegend
import org.javacs.kt.util.AsyncExecutor
import org.javacs.kt.util.TemporaryDirectory
import org.javacs.kt.util.parseURI
import org.javacs.kt.externalsources.*

import com.itsaky.androidide.lsp.api.ILanguageClient as IAndroidIdeLanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer as IAndroidIdeLanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings as IAndroidIdeServerSettings
import com.itsaky.androidide.lsp.models.CompletionParams as AndroidIdeCompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult as AndroidIdeCompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams as AndroidIdeDefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult as AndroidIdeDefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticResult as AndroidIdeDiagnosticResult
import com.itsaky.androidide.lsp.models.ExpandSelectionParams as AndroidIdeExpandSelectionParams
import com.itsaky.androidide.lsp.models.FormatCodeParams as AndroidIdeFormatCodeParams
import com.itsaky.androidide.lsp.models.CodeFormatResult as AndroidIdeCodeFormatResult
import com.itsaky.androidide.lsp.models.LSPFailure as AndroidIdeLSPFailure
import com.itsaky.androidide.lsp.models.ReferenceParams as AndroidIdeReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult as AndroidIdeReferenceResult
import com.itsaky.androidide.lsp.models.SignatureHelp as AndroidIdeSignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams as AndroidIdeSignatureHelpParams
import com.itsaky.androidide.models.Range as AndroidIdeRange
import com.itsaky.androidide.projects.IWorkspace as IAndroidIdeWorkspace

class KotlinLanguageServer(
    val config: Configuration = Configuration()
) : IAndroidIdeLanguageServer, LanguageClientAware, Closeable {
    val databaseService = DatabaseService()
    val classPath = CompilerClassPath(config.compiler, config.scripts, config.codegen, databaseService)

    private val tempDirectory = TemporaryDirectory()
    private val uriContentProvider = URIContentProvider(ClassContentProvider(config.externalSources, classPath, tempDirectory, CompositeSourceArchiveProvider(JdkSourceArchiveProvider(classPath), ClassPathSourceArchiveProvider(classPath))))
    val sourcePath = SourcePath(classPath, uriContentProvider, config.indexing, databaseService)
    val sourceFiles = SourceFiles(sourcePath, uriContentProvider, config.scripts)

    private val textDocuments = KotlinTextDocumentService(sourceFiles, sourcePath, config, tempDirectory, uriContentProvider, classPath)
    private val workspaces = KotlinWorkspaceService(sourceFiles, sourcePath, classPath, textDocuments, config)

    val lsp4jServer: Lsp4jLanguageServer = Lsp4jServerAdapter()

    private var androidIdeClient: IAndroidIdeLanguageClient? = null
    
    // This property will be set by the Lsp4jServerAdapter during its initialization
    lateinit var lsp4jClient: Lsp4jLanguageClient
        private set
    override val serverId: String = SERVER_ID
    
    companion object {
        val VERSION: String? = System.getProperty("kotlinLanguageServer.version")
        const val SERVER_ID = "androidide.lsp.kotlin"
    }

    init {
        LOG.info("Kotlin Language Server: Version ${VERSION ?: "?"}")
    }
    
    override fun connect(client: Lsp4jLanguageClient) {
        this.lsp4jClient = client
        textDocuments.connect(client)
        workspaces.connect(client)
        LOG.info("Connected to lsp4j client")
    }

    override fun close() {
        textDocuments.close()
        classPath.close()
        tempDirectory.close()
    }

    override fun connectClient(client: IAndroidIdeLanguageClient?) {
        this.androidIdeClient = client
        connect(LanguageClientAdapter(client))
    }

    override val client: IAndroidIdeLanguageClient?
        get() = androidIdeClient

    override fun applySettings(settings: IAndroidIdeServerSettings?) {
        LOG.info("Applying new server settings...")
        if (settings != null) {
            config.completion.snippets.enabled = settings.completionsEnabled()
            config.diagnostics.enabled = settings.diagnosticsEnabled()
        }
    }

    override fun setupWorkspace(workspace: IAndroidIdeWorkspace) {
        LOG.info("Setting up workspace: ${workspace.getProjectDir().absolutePath}")
        val root = workspace.getProjectDir().toPath()
        sourceFiles.addWorkspaceRoot(root)
        val refreshed = classPath.addWorkspaceRoot(root)
        if (refreshed) {
            sourcePath.refresh()
        }
        textDocuments.lintAll()
    }

    override fun complete(params: AndroidIdeCompletionParams?): AndroidIdeCompletionResult {
        if (params == null) return AndroidIdeCompletionResult.EMPTY
        val lsp4jParams = params.toLsp4j()
        val result = textDocuments.completion(lsp4jParams).get()
        return result.toIdeCompletionResult()
    }

    override suspend fun findReferences(params: AndroidIdeReferenceParams): AndroidIdeReferenceResult {
        val lsp4jParams = params.toLsp4j()
        val locations = textDocuments.references(lsp4jParams).await()
        return AndroidIdeReferenceResult(locations?.map { it.toIdeLocation() } ?: emptyList())
    }

    override suspend fun findDefinition(params: AndroidIdeDefinitionParams): AndroidIdeDefinitionResult {
        val lsp4jParams = params.toLsp4j()
        val result = textDocuments.definition(lsp4jParams).await()
        val locations = result?.left ?: emptyList()
        return AndroidIdeDefinitionResult(locations.map { it.toIdeLocation() })
    }

    override suspend fun expandSelection(params: AndroidIdeExpandSelectionParams): AndroidIdeRange {
        LOG.warn("expandSelection is not implemented for KotlinLanguageServer")
        return params.selection
    }

    override suspend fun signatureHelp(params: AndroidIdeSignatureHelpParams): AndroidIdeSignatureHelp {
        val lsp4jParams = params.toLsp4j()
        val result = textDocuments.signatureHelp(lsp4jParams).await()
        return result?.toIdeSignatureHelp() ?: AndroidIdeSignatureHelp(emptyList(), -1, -1)
    }

    override suspend fun analyze(file: Path): AndroidIdeDiagnosticResult {
        LOG.info("Triggering manual analysis for ${file.toUri()}")
        textDocuments.lintNowPublic(file.toUri())
        return AndroidIdeDiagnosticResult.NO_UPDATE
    }

    override fun formatCode(params: AndroidIdeFormatCodeParams?): AndroidIdeCodeFormatResult {
        if (params == null) return AndroidIdeCodeFormatResult.NONE
        val result = textDocuments.formatting(params.toLsp4j()).get()
        return result.toIdeCodeFormatResult(params.content)
    }

    override fun handleFailure(failure: AndroidIdeLSPFailure?): Boolean {
        LOG.error("Handling LSP failure: ${failure?.type}", failure?.error)
        return false
    }
    
    override fun shutdown() {
        try {
            close()
        } catch (e: Exception) {
            LOG.error("Error during shutdown", e)
        }
    }


    private inner class Lsp4jServerAdapter : Lsp4jLanguageServer {
        private val async = AsyncExecutor()
        private var progressFactory: Progress.Factory = Progress.Factory.None
            set(factory) {
                field = factory
                this@KotlinLanguageServer.sourcePath.progressFactory = factory
            }

        override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> = async.compute {
            val serverCapabilities = ServerCapabilities()
            serverCapabilities.setTextDocumentSync(TextDocumentSyncKind.Incremental)
            serverCapabilities.workspace = WorkspaceServerCapabilities()
            serverCapabilities.workspace.workspaceFolders = WorkspaceFoldersOptions()
            serverCapabilities.workspace.workspaceFolders.supported = true
            serverCapabilities.workspace.workspaceFolders.changeNotifications = Either.forRight(true)
            serverCapabilities.inlayHintProvider = Either.forLeft(true)
            serverCapabilities.hoverProvider = Either.forLeft(true)
            serverCapabilities.renameProvider = Either.forLeft(true)
            serverCapabilities.completionProvider = CompletionOptions(false, listOf("."))
            serverCapabilities.signatureHelpProvider = SignatureHelpOptions(listOf("(", ","))
            serverCapabilities.definitionProvider = Either.forLeft(true)
            serverCapabilities.documentSymbolProvider = Either.forLeft(true)
            serverCapabilities.workspaceSymbolProvider = Either.forLeft(true)
            serverCapabilities.referencesProvider = Either.forLeft(true)
            serverCapabilities.semanticTokensProvider = SemanticTokensWithRegistrationOptions(semanticTokensLegend, true, true)
            serverCapabilities.codeActionProvider = Either.forLeft(true)
            serverCapabilities.documentFormattingProvider = Either.forLeft(true)
            serverCapabilities.documentRangeFormattingProvider = Either.forLeft(true)
            serverCapabilities.executeCommandProvider = ExecuteCommandOptions(ALL_COMMANDS)
            serverCapabilities.documentHighlightProvider = Either.forLeft(true)

            val storagePath = getStoragePath(params)
            databaseService.setup(storagePath)

            val clientCapabilities = params.capabilities
            config.completion.snippets.enabled = clientCapabilities?.textDocument?.completion?.completionItem?.snippetSupport ?: false

            if (clientCapabilities?.window?.workDoneProgress ?: false) {
                this.progressFactory = LanguageClientProgress.Factory(this@KotlinLanguageServer.lsp4jClient)
            }

            if (clientCapabilities?.textDocument?.rename?.prepareSupport ?: false) {
                serverCapabilities.renameProvider = Either.forRight(RenameOptions(false))
            }

            @Suppress("DEPRECATION")
            val folders = params.workspaceFolders?.takeIf { it.isNotEmpty() }
                ?: params.rootUri?.let(::WorkspaceFolder)?.let(::listOf)
                ?: params.rootPath?.let(Paths::get)?.toUri()?.toString()?.let(::WorkspaceFolder)?.let(::listOf)
                ?: listOf()

            val progress = params.workDoneToken?.let { LanguageClientProgress("Workspace folders", it, this@KotlinLanguageServer.lsp4jClient) }

            folders.forEachIndexed { i, folder ->
                LOG.info("Adding workspace folder {}", folder.name)
                val progressPrefix = "[${i + 1}/${folders.size}] ${folder.name ?: ""}"
                val progressPercent = (100 * i) / folders.size

                progress?.update("$progressPrefix: Updating source path", progressPercent)
                val root = Paths.get(parseURI(folder.uri))
                sourceFiles.addWorkspaceRoot(root)

                progress?.update("$progressPrefix: Updating class path", progressPercent)
                val refreshed = classPath.addWorkspaceRoot(root)
                if (refreshed) {
                    progress?.update("$progressPrefix: Refreshing source path", progressPercent)
                    sourcePath.refresh()
                }
            }
            progress?.close()

            textDocuments.lintAll()
            
            val serverInfo = ServerInfo("Kotlin Language Server", VERSION)

            InitializeResult(serverCapabilities, serverInfo)
        }

        override fun getTextDocumentService(): TextDocumentService = this@KotlinLanguageServer.textDocuments
        override fun getWorkspaceService(): WorkspaceService = this@KotlinLanguageServer.workspaces

        override fun shutdown(): CompletableFuture<Any> {
            this@KotlinLanguageServer.shutdown()
            return completedFuture(null)
        }

        override fun exit() {}
        
        override fun getNotebookDocumentService(): NotebookDocumentService? = null
    }
}