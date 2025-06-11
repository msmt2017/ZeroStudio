package me.rerere.rikkahub

import android.os.Bundle
import android.zero.mcp.McpClient
import android.zero.mcp.McpResponse
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
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
import me.rerere.highlight.Highlighter
import me.rerere.highlight.LocalHighlighter
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.ui.context.*
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.itsaky.androidide.projects.IProjectManager // Assuming this import is correct and dependency is added
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.util.UUID // Explicitly import java.util.UUID

class ChatAiFragment : Fragment() {
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    private lateinit var mcpClient: McpClient
    private var projectRootPath by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mcpClient = McpClient("http://127.0.0.1:11583", okHttpClient)
        projectRootPath = try {
            // This line WILL FAIL if com.itsaky.androidide.projects.IProjectManager is not in your dependencies
            com.itsaky.androidide.projects.IProjectManager.getInstance()
                .rootProject?.directory?.absolutePath ?: ""
        } catch (e: Exception) {
            println("Error getting project root: ${e.message}")
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
            RikkahubTheme {
                setSingletonImageLoaderFactory { context ->
                    ImageLoader.Builder(context)
                        .crossfade(true)
                        .components {
                            add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                            add(SvgDecoder.Factory(scaleToDensity = true))
                        }
                        .build()
                }
                AppRoutes(navController)
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
                LocalFirebaseAnalytics provides remember { Firebase.analytics },
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
                        ChatPage(Uuid.parse(back.arguments!!.getString("id")!!))
                    }
                    composableHelper("history") {
                        HistoryPage()
                    }
                }
            }
        }
    }

    @Composable
    fun ChatPage(id: Uuid) {
        var userInput by rememberSaveable { mutableStateOf("") }
        val focusManager = LocalFocusManager.current
        val messages = remember { mutableStateListOf<McpResponse>() }
        val fileHistory = remember { mutableStateListOf<String>() }
        val cmdHistory = remember { mutableStateListOf<String>() }
        val allCommands = listOf("@File:", "@task:", "@gradle:", "@shell:")
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
            topBar = { CenterAlignedTopAppBar(title={ Text("ChatAI - MCP") }) },
            bottomBar = {
                Column {
                    Box {
                        AutoCompleteTextField(
                            value = userInput,
                            onValueChange = { text ->
                                userInput = text
                                suggestionType = when {
                                    text.startsWith("@File:") -> SuggestionType.FileHistory
                                    text.startsWith("@task:") -> SuggestionType.CommandHistory
                                    text.startsWith("@gradle:") -> SuggestionType.CommandHistory
                                    text.startsWith("@shell:") -> SuggestionType.CommandHistory
                                    text.startsWith("@") -> SuggestionType.Command
                                    else -> SuggestionType.None
                                }
                                showSuggestions = suggestionType != SuggestionType.None && text.isNotBlank()

                                suggestions = when(suggestionType) {
                                    SuggestionType.Command -> allCommands.filter { it.startsWith(text,true) }
                                    SuggestionType.FileHistory -> fileHistory.filter {
                                        it.contains(text.removePrefix("@File:").trim(), true)
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
                                        userInput = "@File:upload path=$selectedSuggestion"
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
            }
        ) { pad ->
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
            input.startsWith("@File:") -> {
                val parts = input.removePrefix("@File:").split(" ", limit = 2)
                val action = parts[0]
                parts.drop(1).forEach { kv ->
                    val keyValue = kv.split("=", limit = 2)
                    if (keyValue.size == 2) {
                        args[keyValue[0]] = keyValue[1]
                    }
                }
                args["projectRoot"] = projectRoot
                client.sendCommand(
                    type = "file.$action",
                    args = args,
                    contextId = null
                ).onEach { resp ->
                    if (resp.event == "file.upload.content" && args["path"] != null) {
                        val uploadedPath = args["path"]!!
                        if (uploadedPath.isNotBlank() && !fileHistory.contains(uploadedPath)) {
                            fileHistory += uploadedPath
                        }
                    }
                }.launchIn(client.coroutineScope)
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
                    contextId = null
                ).onEach { resp ->
                    messages += resp
                }.launchIn(client.coroutineScope)
            }
            input.startsWith("@gradle:") -> {
                val cmd = input.removePrefix("@gradle:").trim()
                client.sendCommand(
                    type = "gradle.execute",
                    args = mapOf("projectRoot" to projectRoot, "command" to cmd),
                    contextId = null
                ).onEach { resp ->
                    messages += resp
                }.launchIn(client.coroutineScope)
            }
            input.startsWith("@shell:") -> {
                val cmd = input.removePrefix("@shell:").trim()
                client.sendCommand(
                    type = "shell.execute",
                    args = mapOf("command" to cmd),
                    contextId = null
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
        Text(msg.result,style=MaterialTheme.typography.bodyMedium)
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
