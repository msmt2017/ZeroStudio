package com.itsaky.androidide.lsp.java

import androidx.annotation.RestrictTo
import com.itsaky.androidide.eventbus.events.editor.DocumentChangeEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentCloseEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentOpenEvent
import com.itsaky.androidide.eventbus.events.editor.DocumentSelectedEvent
import com.itsaky.androidide.javac.services.fs.CacheFSInfoSingleton
import com.itsaky.androidide.javac.services.fs.CachingJarFileSystemProvider.clearCache
import com.itsaky.androidide.javac.services.fs.CachingJarFileSystemProvider.clearCachesForPaths
import com.itsaky.androidide.lsp.api.ILanguageClient
import com.itsaky.androidide.lsp.api.ILanguageServer
import com.itsaky.androidide.lsp.api.IServerSettings
import com.itsaky.androidide.lsp.internal.model.CachedCompletion
import com.itsaky.androidide.lsp.java.actions.JavaCodeActionsMenu
import com.itsaky.androidide.lsp.java.compiler.JavaCompilerService
import com.itsaky.androidide.lsp.java.compiler.SourceFileManager
import com.itsaky.androidide.lsp.java.models.JavaServerSettings
import com.itsaky.androidide.lsp.java.providers.CodeFormatProvider
import com.itsaky.androidide.lsp.java.providers.CompletionProvider
import com.itsaky.androidide.lsp.java.providers.DefinitionProvider
import com.itsaky.androidide.lsp.java.providers.JavaDiagnosticProvider
import com.itsaky.androidide.lsp.java.providers.JavaSelectionProvider
import com.itsaky.androidide.lsp.java.providers.ReferenceProvider
import com.itsaky.androidide.lsp.java.providers.SignatureProvider
import com.itsaky.androidide.lsp.java.providers.snippet.JavaSnippetRepository.init
import com.itsaky.androidide.lsp.java.utils.AnalyzeTimer
import com.itsaky.androidide.lsp.java.utils.CancelChecker.Companion.isCancelled
import com.itsaky.androidide.lsp.models.CodeFormatResult
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.DefinitionParams
import com.itsaky.androidide.lsp.models.DefinitionResult
import com.itsaky.androidide.lsp.models.DiagnosticResult
import com.itsaky.androidide.lsp.models.ExpandSelectionParams
import com.itsaky.androidide.lsp.models.FailureType
import com.itsaky.androidide.lsp.models.FormatCodeParams
import com.itsaky.androidide.lsp.models.LSPFailure
import com.itsaky.androidide.lsp.models.ReferenceParams
import com.itsaky.androidide.lsp.models.ReferenceResult
import com.itsaky.androidide.lsp.models.SignatureHelp
import com.itsaky.androidide.lsp.models.SignatureHelpParams
import com.itsaky.androidide.lsp.util.LSPEditorActions
import com.itsaky.androidide.models.Range
import com.itsaky.androidide.projects.FileManager.getActiveDocumentCount
import com.itsaky.androidide.projects.IProjectManager.Companion.getInstance
import com.itsaky.androidide.projects.api.ModuleProject
import com.itsaky.androidide.projects.api.Project
import com.itsaky.androidide.utils.DocumentUtils
import com.itsaky.androidide.utils.ILogger
import com.itsaky.androidide.utils.VMUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking // Added for synchronous execution of suspend functions
// Removed 'limitedParallelism' import as it was causing an unresolved reference,
// likely due to an older kotlinx.coroutines version in the build environment.
// import kotlinx.coroutines.limitedParallelism
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.nio.file.Path
import java.util.Objects
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout // Added for timeout functionality

class JavaLanguageServer : ILanguageServer {

    private val completionProvider: CompletionProvider = CompletionProvider()
    private val diagnosticProvider: JavaDiagnosticProvider?
    override var client: ILanguageClient? = null
        private set

    private var _settings: IServerSettings? = null
    private var selectedFile: Path? = null
    private val timer = AnalyzeTimer { analyzeSelected() }
    private var cachedCompletion: CachedCompletion

    // 新增协程作用域，使用SupervisorJob独立管理任务
    private val analysisScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val compilerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var analysisJob: Job
    // The 'parallelism' variable is kept, but its direct usage with 'limitedParallelism'
    // has been removed due to compilation issues. Concurrency will now be managed by
    // the default behavior of Dispatchers.Default and Dispatchers.IO.
    private val parallelism by lazy {
        Runtime.getRuntime().availableProcessors().coerceAtMost(2) // 限制最大并发数为2
    }

    val settings: IServerSettings
        get() {
            return _settings ?: JavaServerSettings.getInstance()
                .also { _settings = it }
        }

    override val serverId: String = SERVER_ID

    companion object {
        const val SERVER_ID = "ide.lsp.java"
        private val LOG = ILogger.newInstance("JavaLanguageServer")
    }

    init {
        diagnosticProvider = JavaDiagnosticProvider()
        cachedCompletion = CachedCompletion.EMPTY

        applySettings(JavaServerSettings.getInstance())

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }

        init()
    }

    override fun shutdown() {
        // 优化资源释放，取消所有协程作用域
        analysisScope.cancel()
        compilerScope.cancel()

        JavaCompilerProvider.getInstance().destroy()
        SourceFileManager.clearCache()
        CacheFSInfoSingleton.clearCache()
        clearCache()
        EventBus.getDefault().unregister(this)
        timer.cancel()
    }

    override fun connectClient(client: ILanguageClient?) {
        this.client = client
    }

    override fun applySettings(settings: IServerSettings?) {
        this._settings = settings
    }

    override fun setupWithProject(project: Project) {
        LSPEditorActions.ensureActionsMenuRegistered(JavaCodeActionsMenu)

        // 优化编译器初始化流程
        JavaCompilerService.NO_MODULE_COMPILER.destroy()
        SourceFileManager.clearCache()
        clearCachesForPaths { path: String -> path.endsWith("/R.jar") }
        JavaCompilerProvider.getInstance().destroy()

        // 动态初始化模块编译器
        for (subModule in project.subProjects) {
            if (subModule !is ModuleProject || subModule.path == project.rootProject.path) {
                continue
            }
            SourceFileManager.forModule(subModule)
        }
        startOrRestartAnalyzeTimer()
    }

    // Reverted to non-suspend function to match the ILanguageServer interface
    override fun complete(params: CompletionParams?): CompletionResult {
        val compiler = getCompiler(params!!.file)
        if (!settings.completionsEnabled() || !completionProvider.canComplete(params.file)) {
            return CompletionResult.EMPTY
        }

        // Cancel existing analysis if needed
        if (diagnosticProvider!!.isAnalyzing()) {
            LOG.warn("Cancelling source code analysis due to completion request")
            diagnosticProvider.cancel()
        }

        // Use runBlocking to call the suspend function and get the result synchronously.
        // This is necessary because the `complete` method in the interface is non-suspend.
        // Be aware that runBlocking blocks the current thread until the async operation completes.
        return runBlocking {
            compilerScope.async {
                completionProvider.reset(
                    compiler, settings, cachedCompletion) { cachedCompletion: CachedCompletion ->
                    updateCachedCompletion(cachedCompletion)
                }
                completionProvider.complete(params)
            }.await()
        }
    }

    override suspend fun findReferences(params: ReferenceParams): ReferenceResult {
        val compiler = getCompiler(params.file)
        return if (!settings.referencesEnabled()) {
            ReferenceResult(emptyList())
        } else ReferenceProvider(compiler, params.cancelChecker).findReferences(params)
    }

    override suspend fun findDefinition(params: DefinitionParams): DefinitionResult {
        val compiler = getCompiler(params.file)
        return if (!settings.definitionsEnabled()) {
            DefinitionResult(emptyList())
        } else DefinitionProvider(compiler, settings, params.cancelChecker).findDefinition(params)
    }

    override suspend fun expandSelection(params: ExpandSelectionParams): Range {
        val compiler = getCompiler(params.file)
        return if (!settings.smartSelectionsEnabled()) {
            params.selection
        } else JavaSelectionProvider(compiler).expandSelection(params)
    }

    override suspend fun signatureHelp(params: SignatureHelpParams): SignatureHelp {
        val compiler = getCompiler(params.file)
        return if (!settings.signatureHelpEnabled()) {
            SignatureHelp(emptyList(), -1, -1)
        } else SignatureProvider(compiler, params.cancelChecker).signatureHelp(params)
    }

    override suspend fun analyze(file: Path): DiagnosticResult {
        if (!settings.diagnosticsEnabled() || !DocumentUtils.isJavaFile(file)) {
            return DiagnosticResult.NO_UPDATE
        }

        return if (!settings.codeAnalysisEnabled()) {
            DiagnosticResult.NO_UPDATE
        } else {
            // Replaced 'limitedParallelism' due to unresolved reference.
            // Using 'withTimeout' to achieve the desired timeout behavior.
            withTimeout(TimeUnit.SECONDS.toMillis(10)) { // 10 seconds timeout
                analysisScope.async(Dispatchers.Default) {
                    diagnosticProvider!!.analyze(file)
                }.await() // Await no longer takes timeout arguments
            }
        }
    }

    override fun formatCode(params: FormatCodeParams?): CodeFormatResult {
        return CodeFormatProvider(settings).format(params)
    }

    override fun handleFailure(failure: LSPFailure?): Boolean {
        return when (failure!!.type) {
            FailureType.COMPLETION -> {
                if (isCancelled(failure.error)) {
                    true
                } else {
                    JavaCompilerProvider.getInstance().destroy()
                    true
                }
            }
            else -> false
        }
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun getCompiler(file: Path?): JavaCompilerService {
        if (!DocumentUtils.isJavaFile(file)) {
            return JavaCompilerService.NO_MODULE_COMPILER
        }
        return try {
            val root = getInstance().rootProject ?: return JavaCompilerService.NO_MODULE_COMPILER
            val module = root.findModuleForFile(file!!) ?: return JavaCompilerService.NO_MODULE_COMPILER
            JavaCompilerProvider.get(module)
        } catch (e: Exception) {
            LOG.error("Failed to get compiler for file: $file", e)
            JavaCompilerService.NO_MODULE_COMPILER
        }
    }

    private fun updateCachedCompletion(cachedCompletion: CachedCompletion) {
        Objects.requireNonNull(cachedCompletion)
        this.cachedCompletion = cachedCompletion
    }

    private fun startOrRestartAnalyzeTimer() {
        if (VMUtils.isJvm()) {
            return
        }
        if (!timer.isStarted) {
            timer.start()
        } else {
            timer.restart()
        }
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onContentChange(event: DocumentChangeEvent) {
        if (!DocumentUtils.isJavaFile(event.changedFile)) {
            return
        }

        JavaCompilerService.NO_MODULE_COMPILER.onDocumentChange(event)
        val module = getInstance().findModuleForFile(event.changedFile)
        if (module != null) {
            val compiler = JavaCompilerProvider.get(module)
            compiler.onDocumentChange(event)
        }
        startOrRestartAnalyzeTimer()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onFileSelected(event: DocumentSelectedEvent) {
        selectedFile = event.selectedFile
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onFileOpened(event: DocumentOpenEvent) {
        selectedFile = event.openedFile
        startOrRestartAnalyzeTimer()
    }

    @Subscribe(threadMode = ThreadMode.ASYNC)
    @Suppress("unused")
    fun onFileClosed(event: DocumentCloseEvent) {
        diagnosticProvider?.clearTimestamp(event.closedFile)

        if (getActiveDocumentCount() == 0) {
            selectedFile = null
            timer.cancel()
        }
    }

    private fun analyzeSelected() {
        if (selectedFile == null || client == null) {
            return
        }

        // Using Dispatchers.Default directly as 'limitedParallelism' was unresolved.
        // The timeout is now handled within the 'analyze' function.
        analysisScope.launch(Dispatchers.Default) {
            try {
                val result = analyze(selectedFile!!)
                withContext(Dispatchers.Main) {
                    client?.publishDiagnostics(result)
                }
            } catch (e: TimeoutException) {
                LOG.warn("Analysis timed out for file: $selectedFile", e)
            } catch (e: CancellationException) {
                LOG.info("Analysis cancelled for file: $selectedFile", e)
            } catch (e: Exception) {
                LOG.error("Analysis error for file: $selectedFile", e)
            }
        }
    }
}
