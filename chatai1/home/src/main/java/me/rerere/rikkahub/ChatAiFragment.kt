package me.rerere.rikkahub




import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.LocalAnimatedVisibilityScope
import me.rerere.rikkahub.ui.context.LocalFirebaseAnalytics
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalSharedTransitionScope
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.pages.assistant.AssistantPage
import me.rerere.rikkahub.ui.pages.assistant.detail.AssistantDetailPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

import android.zero.mcp.McpClient
import android.zero.mcp.McpResponse

import com.itsaky.androidide.projects.Module
import com.itsaky.androidide.projects.ProjectManagerImpl
import com.itsaky.androidide.activities.editor.EditorHandlerActivity
import com.itsaky.androidide.editor.ui.IDEEditor








// import android.os.Bundle

// import androidx.compose.animation.AnimatedContentScope
// import androidx.compose.animation.AnimatedContentTransitionScope
// import androidx.compose.animation.EnterTransition
// import androidx.compose.animation.ExitTransition
// import androidx.compose.animation.SharedTransitionLayout
// import androidx.compose.animation.core.tween
// import androidx.compose.animation.fadeIn
// import androidx.compose.animation.fadeOut
// import androidx.compose.animation.scaleIn
// import androidx.compose.animation.scaleOut
// import androidx.compose.foundation.background
// import androidx.compose.foundation.layout.*
// import androidx.compose.foundation.lazy.LazyColumn
// import androidx.compose.foundation.lazy.items
// import androidx.compose.foundation.text.KeyboardActions
// import androidx.compose.foundation.text.KeyboardOptions
// import androidx.compose.material3.*
// import androidx.compose.runtime.*
// import androidx.compose.runtime.saveable.rememberSaveable
// import androidx.compose.ui.Alignment
// import androidx.compose.ui.Modifier
// import androidx.compose.ui.graphics.TransformOrigin
// import androidx.compose.ui.platform.ComposeView
// import androidx.compose.ui.platform.LocalFocusManager
// import androidx.compose.ui.text.input.ImeAction
// import androidx.compose.ui.unit.dp
// import androidx.fragment.app.Fragment
// import androidx.lifecycle.compose.collectAsStateWithLifecycle
// import androidx.navigation.NamedNavArgument
// import androidx.navigation.NavBackStackEntry
// import androidx.navigation.NavGraphBuilder
// import androidx.navigation.NavType
// import androidx.navigation.compose.NavHost
// import androidx.compose.material.icons.Icons


// import java.io.File
// import android.util.Log
// import java.util.UUID
// import kotlinx.coroutines.launch
// import androidx.lifecycle.lifecycleScope
// import io.github.rosemoe.sora.text.Cursor
// import androidx.navigation.compose.rememberNavController
// import coil.ImageLoader
// import coil.decode.SvgDecoder


// import me.rerere.rikkahub.util.LocalNavController
// import me.rerere.rikkahub.util.LocalSharedTransitionScope
// import me.rerere.rikkahub.util.LocalSettings
// import me.rerere.rikkahub.util.LocalHighlighter
// import me.rerere.rikkahub.util.LocalFirebaseAnalytics
// import me.rerere.rikkahub.util.LocalToaster
// import me.rerere.rikkahub.util.Toaster
// import me.rerere.rikkahub.util.composableHelper
// import me.rerere.rikkahub.util.LocalDarkMode
// import com.google.firebase.analytics.FirebaseAnalytics


class ChatAiFragment : Fragment() {
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private lateinit var mcpClient: McpClient
    private var projectRootPath by mutableStateOf("")
    private var modules by mutableStateOf(emptyList<Module>())
    private var selectedModule by mutableStateOf<Module?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mcpClient = McpClient("http://127.0.0.1:11583", okHttpClient)
        projectRootPath = try {
            val projectManager = ProjectManagerImpl.getInstance()
            projectRootPath = projectManager.rootProject?.directory?.absolutePath ?: ""
            modules = projectManager.getAndroidModules()
            selectedModule = modules.firstOrNull()
            projectRootPath
        } catch (e: Exception) {
            Log.e("ChatAiFragment", "Error initializing project: ${e.message}")
            ""
        }
    }

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?
    ) = ComposeView(requireContext()).apply {
        setContent {
            val navController = rememberNavController()
            setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(OkHttpNetworkFetcher.Factory(okHttpClient))
                            add(SvgDecoder.Factory())
                        }
                    .build()
                }
                RikkahubTheme {
                    AppRoutes(navController)
                }
            }
        }
    }

    @Composable
    fun AppRoutes(navController: androidx.navigation.NavHostController) { // Corrected type
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalFirebaseAnalytics provides remember { FirebaseAnalytics.getInstance(requireContext()) },
                LocalToaster provides toastState,
            ) {
                Toaster(state = toastState, darkTheme = LocalDarkMode.current, richColors = true)
                NavHost(
                    navController = navController,
                    startDestination = "chat/${UUID.randomUUID()}",
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    enterTransition = { scaleIn(initialScale = 0.35f) + fadeIn(animationSpec = tween(300)) },
                    exitTransition = { fadeOut(animationSpec = tween(300)) },
                    popExitTransition = {
                        scaleOut(targetScale = 0.5f, transformOrigin = TransformOrigin(0.5f, 0.5f))
                            + fadeOut(animationSpec = tween(300))
                    },
                    popEnterTransition = { EnterTransition.None }
                ) {
                    composableHelper("chat/{id}",
                        listOf(navArgument("id") { type = NavType.StringType })
                    ) { back ->
                        ChatPage(UUID.fromString(back.arguments!!.getString("id")!!))
                    }
                    composableHelper("history") {
                        HistoryPage()
                    }
                }
            }
        }
    }

    @Composable
fun ChatPage(id: UUID) {
        var userInput by rememberSaveable { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        val messages = remember { mutableStateListOf<McpResponse>() }
        val fileHistory = remember { mutableStateListOf<String>() }
        val cmdHistory = remember { mutableStateListOf<String>() }
        val allCommands = listOf("@file:", "@Folder:", "@task:", "@gradle:", "@shell:", "@search:", "@TabFile:")
        var suggestions by remember { mutableStateOf(listOf<String>()) }
        var showSuggestions by remember { mutableStateOf(false) }
        var suggestionType by remember { mutableStateOf(SuggestionType.None) }

        LaunchedEffect(Unit) {
            mcpClient.listenSse().collectLatest { resp ->
                messages += resp
                when {
                    resp.event == "file.upload.content" -> {
                        val pathFromUploadCommand = userInput.substringAfter("path=").trim()
                        if (pathFromUploadCommand.isNotBlank() && !fileHistory.contains(pathFromUploadCommand)) {
                            fileHistory += pathFromUploadCommand
                        }
                    }
                    resp.event.startsWith("response") -> {
                        if (userInput.isNotBlank() && userInput.startsWith("@")) {
                            cmdHistory += userInput
                        }
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose { mcpClient.close() }
        }

        Scaffold(
            topBar = { CenterAlignedTopAppBar(
                title = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        Column {
                            Text(selectedModule?.name ?: "ChatAI - MCP")
                            selectedModule?.projectDir?.absolutePath?.let { path ->
                                Text(path, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        IconButton(onClick = { expanded = true }) {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select module")
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            modules.forEach { module ->
                                DropdownMenuItem(
                                    onClick = { 
                                        selectedModule = module
                                        expanded = false
                                    }
                                ) {
                                    Column {
                                        Text(module.name)
                                        Text(module.path, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            ) },
            bottomBar = {
                Column {
                    Box {
                        AutoCompleteTextField(
                            value = userInput,
                            onValueChange = { text ->
                                userInput = text
                                suggestionType = when {
                                    text.startsWith("@file:") -> SuggestionType.FileHistory
                        text.startsWith("@task:") -> SuggestionType.CommandHistory
                        text.startsWith("@gradle:") -> SuggestionType.CommandHistory
                        text.startsWith("@shell:") -> SuggestionType.CommandHistory
                        text.startsWith("@search:") -> SuggestionType.CommandHistory
                        text.startsWith("@") -> SuggestionType.Command
                                    else -> SuggestionType.None
                                }
                                showSuggestions = suggestionType != SuggestionType.None && text.isNotBlank()

                                suggestions = when(suggestionType) {
                                    SuggestionType.Command -> allCommands.filter { it.startsWith(text,true) }
                                    SuggestionType.FileHistory -> fileHistory.filter {
                                        it.contains(text.removePrefix("@file:").trim(), true)
                                    }
                                    SuggestionType.CommandHistory -> cmdHistory.filter {
                                        it.contains(text.removePrefix("@task:")
                                            .removePrefix("@gradle:")
                                            .removePrefix("@shell:")
                                            .trim(), true)
                                    }
                                    else->emptyList()
                                }
                            },
                            suggestions = suggestions,
                            showSuggestions = showSuggestions,
                            onSelectSuggestion = { selectedSuggestion ->
                                when (suggestionType) {
                                    SuggestionType.Command -> userInput = selectedSuggestion
                                    SuggestionType.FileHistory -> {
                                        userInput = "@file:upload path=$selectedSuggestion"
                                        handleInput(userInput, messages, cmdHistory, fileHistory, mcpClient, projectRootPath)
                                        userInput = ""
                                    }
                                    SuggestionType.CommandHistory -> userInput = selectedSuggestion
                                    SuggestionType.None -> {}
                                }
                                showSuggestions = false
                                focusManager.clearFocus()
                            },
                            keyboardActions = KeyboardActions(onSend = {
                                handleInput(userInput, messages, cmdHistory, fileHistory, mcpClient, projectRootPath)
                                userInput = ""
                                focusManager.clearFocus()
                            }),
                            placeholder = { Text("输入消息或命令，@触发补全") } // Passed as Composable lambda
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement=Arrangement.End) {
                        Button(onClick={
                            handleInput(userInput, messages, cmdHistory, fileHistory, mcpClient, projectRootPath)
                            userInput=""
                            focusManager.clearFocus()
                        }) { Text("发送") }
                    }
                }
            )
        } { pad ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .background(MaterialTheme.colorScheme.background),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { msg -> MessageItem(msg) }
            }
        }
    }

    private fun handleInput(
        input: String,
        messages: MutableList<McpResponse>,
        cmdHistory: MutableList<String>,
        fileHistory: MutableList<String>,
        client: McpClient,
        projectRoot: String
    ) {
        if (input.isBlank()) return
        messages += McpResponse(UUID.randomUUID().toString(), "local", "发送：$input", null, null)

        val args = mutableMapOf<String, String>()
        when {
            input.startsWith("@file:") -> {
                val parts = input.removePrefix("@file:").split(" ", limit = 2)
                val action = parts[0]
                parts.drop(1).forEach { kv ->
                    val keyValue = kv.split("=", limit = 2)
                    if (keyValue.size == 2) {
                        args[keyValue[0]] = keyValue[1]
                    }
                }
                args["projectRoot"] = projectRoot
                // 验证文件路径
                val filePath = args["path"]
                if (filePath != null) {
                    val file = File(filePath)
                    if (!file.isFile) {
                        messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：指定路径不是文件", null, null)
                        return
                    }
                }
                client.sendCommand(
                    type = "file.$action",
                    args = args,
                    contextId = UUID.randomUUID().toString()
                ).onEach { resp ->
                    if (resp.event == "file.upload.content" && args["path"] != null) {
                        val uploadedPath = args["path"]!!
                        if (uploadedPath.isNotBlank() && !fileHistory.contains(uploadedPath)) {
                            fileHistory += uploadedPath
                        }
                    }
                }.launchIn(lifecycleScope)
            }
            input.startsWith("@task:") -> {
                val tasksArg = input.removePrefix("@task:").trim()
                val taskArgs = tasksArg.split(" ").mapNotNull {
                    val keyValue = it.split("=", limit = 2)
                    if (keyValue.size == 2) keyValue[0] to keyValue[1] else null
                }.toMap()
                val tasksList = if (taskArgs.isEmpty()) tasksArg else ""
                val finalTaskArgs = if (tasksList.isNotBlank()) mapOf("tasks" to tasksList) else taskArgs

                client.sendCommand(
                    type = "task.execute",
                    args = finalTaskArgs,
                    contextId = UUID.randomUUID().toString()
                ).onEach { resp ->
                    messages += resp
                }.launchIn(viewModel.viewModelScope)
            }
            input.startsWith("@gradle:") -> {
                val cmd = input.removePrefix("@gradle:").trim()
                client.sendCommand(
                    type = "gradle.execute",
                    args = mapOf("projectRoot" to projectRoot, "command" to cmd),
                    contextId = UUID.randomUUID().toString()
                ).onEach { resp ->
                    messages += resp
                }.launchIn(viewModel.viewModelScope)
            }
            input.startsWith("@shell:") -> {
                val cmd = input.removePrefix("@shell:").trim()
                client.sendCommand(
                    type = "shell.execute",
                    args = mapOf("command" to cmd),
                    contextId = UUID.randomUUID().toString()
                ).onEach { resp ->
                    messages += resp
                }.launchIn(lifecycleScope)
            }
            input.startsWith("@TabFile:") -> {
                val tabCommand = input.removePrefix("@TabFile:").trim()
                val projectRoot = projectRootPath
                if (projectRoot.isBlank()) {
                    messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：无法获取项目根路径", null, null)
                    return
                }
                
                // 获取当前编辑器
                val editorActivity = requireActivity() as? EditorHandlerActivity
                val currentEditor = editorActivity?.getCurrentEditor() as? IDEEditor
                if (currentEditor == null) {
                    messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：未找到当前编辑器", null, null)
                    return
                }
                
                val currentFile = currentEditor.getFile()
                if (currentFile == null || !currentFile.exists()) {
                    messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：当前编辑器中没有打开的文件", null, null)
                    return
                }
                
                val fileContent = currentEditor.text.toString()
                val fileName = currentFile.name
                val filePath = currentFile.absolutePath
                
                when {
                    tabCommand.startsWith("getFile") -> {
                        // 上传整个文件
                        messages += McpResponse(UUID.randomUUID().toString(), "local", "正在上传文件: $fileName\n路径: $filePath", null, null)
                        client.sendCommand(
                            type = "file.upload.content",
                            args = mapOf(
                                "path" to filePath,
                                "content" to fileContent,
                                "projectRoot" to projectRoot
                            ),
                            contextId = UUID.randomUUID().toString()
                        ).launchIn(lifecycleScope)
                    }
                    tabCommand.startsWith("getLine:") -> {
                        // 处理行范围，格式: getLine:5-10
                        val lineRange = tabCommand.removePrefix("getLine:").trim()
                        val lineParts = lineRange.split("-")
                        if (lineParts.size != 2) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：行范围格式不正确，应为 getLine:开始行-结束行", null, null)
                            return
                        }
                        
                        val startLine = lineParts[0].toIntOrNull()?.minus(1) // 转换为0-based索引
                        val endLine = lineParts[1].toIntOrNull()?.minus(1)
                        if (startLine == null || endLine == null || startLine < 0 || endLine < startLine) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：无效的行范围", null, null)
                            return
                        }
                        
                        val lines = fileContent.lines()
                        if (endLine >= lines.size) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：行范围超出文件总行数", null, null)
                            return
                        }
                        
                        val selectedLines = lines.subList(startLine, endLine + 1).joinToString("\n")
                        val lineInfo = "文件: $fileName (行 ${startLine+1}-${endLine+1})\n路径: $filePath"
                        messages += McpResponse(UUID.randomUUID().toString(), "local", lineInfo, null, null)
                        client.sendCommand(
                            type = "file.upload.content",
                            args = mapOf(
                                "path" to filePath,
                                "content" to selectedLines,
                                "projectRoot" to projectRoot,
                                "lines" to "${startLine+1}-${endLine+1}"
                            ),
                            contextId = UUID.randomUUID().toString()
                        ).launchIn(client.coroutineScope)
                    }
                    tabCommand.startsWith("getCursor") -> {
                        // 获取光标所在行

                        val cursor: Cursor = currentEditor.text.cursor
                        val cursorLine = cursor.line // 0-based
                        val cursorColumn = cursor.column
                        val lines = fileContent.lines()
                        if (cursorLine < 0 || cursorLine >= lines.size) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：光标位置无效", null, null)
                            return
                        }

                        val cursorLineContent = lines[cursorLine]
                        val lineInfo = "文件: $fileName (光标行 ${cursorLine+1})\n路径: $filePath"
                        messages += McpResponse(UUID.randomUUID().toString(), "local", lineInfo, null, null)
                        client.sendCommand(
                            type = "file.upload.content",
                            args = mapOf(
                                "path" to filePath,
                                "content" to cursorLineContent,
                                "projectRoot" to projectRoot,
                                "line" to "${cursorLine+1}"
                            ),
                            contextId = UUID.randomUUID().toString()
                        ).launchIn(client.coroutineScope)
                    }
                    tabCommand.startsWith("getFunction:") -> {
                        // 获取指定函数内容
                        val functionName = tabCommand.removePrefix("getFunction:").trim()
                        if (functionName.isBlank()) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：函数名不能为空", null, null)
                            return
                        }

                        // 正则表达式匹配函数定义 (支持Kotlin/Java基本函数格式)
                        // 改进的正则表达式，支持嵌套花括号和更复杂的函数定义
                        val functionPattern = Regex(
                            "(?s)fun\\s+${Regex.escape(functionName)}\\s*\\([^)]*\\)\\s*(\\{((?>[^{}]+)|\\{(?1)\\})*\\})")
                        )

                        val matchResult = functionPattern.find(fileContent)
                        if (matchResult == null) {
                            messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：未找到函数 '$functionName'", null, null)
                            return
                        }

                        val functionContent = matchResult.value
                        val functionInfo = "文件: $fileName (函数: $functionName)\n路径: $filePath"
                        messages += McpResponse(UUID.randomUUID().toString(), "local", functionInfo, null, null)
                        client.sendCommand(
                            type = "file.upload.content",
                            args = mapOf(
                                "path" to filePath,
                                "content" to functionContent,
                                "projectRoot" to projectRoot,
                                "function" to functionName
                            ),
                            contextId = UUID.randomUUID().toString()
                        ).launchIn(client.coroutineScope)
                    }
                    else -> {
                        messages += McpResponse(UUID.randomUUID().toString(), "local", "错误：未知的@TabFile子命令，支持的命令: getFile, getLine:开始行-结束行, getCursor, getFunction:函数名", null, null)
                    }
                }
            }
            input.startsWith("@search:") -> {
                val searchParams = input.removePrefix("@search:").trim().split(" ", limit = 2)
                val keyword = searchParams.getOrNull(0) ?: ""
                val path = searchParams.getOrNull(1)?.removePrefix("path=") ?: projectRoot
                client.sendCommand(
                    type = "file.searchContent",
                    args = mapOf(
                        "content" to keyword,
                        "projectRoot" to path
                    ),
                    contextId = UUID.randomUUID().toString()
                ).onEach { resp ->
                    messages += resp
                }.launchIn(client.coroutineScope)
            }
            else -> {
                client.sendCommand(
                    type = "chat.message",
                    args = mapOf("text" to input),
                    contextId = null
                ).onEach { resp ->
                    messages += resp
                }.launchIn(client.coroutineScope)
            }
        }
    }

    enum class SuggestionType { None, Command, FileHistory, CommandHistory }
}

@Composable
fun AutoCompleteTextField(
    value:String,
    onValueChange:(String)->Unit,
    suggestions:List<String>,
    showSuggestions:Boolean,
    onSelectSuggestion:(String)->Unit,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    placeholder: @Composable () -> Unit = {} // Accept a Composable lambda for placeholder
) {
    Column {
        TextField(
            value=value,
            onValueChange=onValueChange,
            placeholder=placeholder, // Use the passed Composable lambda
            singleLine=true,
            keyboardOptions=KeyboardOptions.Default.copy(imeAction=ImeAction.Send),
            keyboardActions = keyboardActions,
            modifier=Modifier.fillMaxWidth().padding(8.dp)
        )
        if(showSuggestions) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { /* Handled by onSelectSuggestion or explicit dismiss if needed */ },
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(Alignment.CenterHorizontally)
            ) {
                suggestions.forEach { s->
                    DropdownMenuItem(onClick={ onSelectSuggestion(s) }) { Text(s) }
                }
            }
        }
    }
}

@Composable
fun MessageItem(msg:McpResponse) {
    Column(Modifier.fillMaxWidth().padding(4.dp).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        Text(msg.event,style=MaterialTheme.typography.labelSmall)
        val resultText = msg.result
        when {
            msg.event in listOf("file.upload.content", "file.create", "directory.create") && resultText.contains("path:") -> {
                val path = resultText.substringAfter("path:").trim()
                val file = File(path)
                Column {
                    Text(file.name, style = MaterialTheme.typography.bodyMedium)
                    Text(path, style = MaterialTheme.typography.bodySmall)
                }
            }
            else -> {
                Text(resultText, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun NavGraphBuilder.composableHelper(
    route:String,args:List<NamedNavArgument> = emptyList(),
    enterTransition:(AnimatedContentTransitionScope<NavBackStackEntry>.()->EnterTransition?)?=null,
    exitTransition:(AnimatedContentTransitionScope<NavBackStackEntry>.()->ExitTransition?)?=null, // Corrected type
    popEnterTransition:(AnimatedContentTransitionScope<NavBackStackEntry>.()->EnterTransition?)?=null, // Corrected type
    popExitTransition:(AnimatedContentTransitionScope<NavBackStackEntry>.()->ExitTransition?)?=null, // Corrected type
    content:@Composable AnimatedContentScope.(NavBackStackEntry)->Unit // Corrected type
){
    this.composable(
        route = route,
        arguments = args,
        enterTransition = enterTransition,
        exitTransition = exitTransition,
        popEnterTransition = popEnterTransition,
        popExitTransition = popExitTransition,
    ) { entry ->
        CompositionLocalProvider(LocalAnimatedVisibilityScope provides this) {
            content(entry)
        }
    }
}
