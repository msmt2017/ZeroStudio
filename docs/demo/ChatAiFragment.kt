// 文件路径：app/src/main/java/me/rerere/rikkahub/ChatAiFragment.kt
package me.rerere.rikkahub

import android.os.Bundle
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
import me.rerere.rikkahub.mcp.McpClient
import me.rerere.rikkahub.mcp.McpResponse
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

class ChatAiFragment : Fragment() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()

    // MCP 客户端
    private lateinit var mcpClient: McpClient

    // 当前打开项目根目录路径
    private var projectRootPath by mutableStateOf("")

    // 最近一次上传的文件路径，用于记录历史
    private var lastSentPath by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics = Firebase.analytics

        // 初始化 MCP 客户端
        mcpClient = McpClient()

        // 通过 IProjectManager 获取当前项目根目录
        val projectManager = com.itsaky.androidide.projects.IProjectManager.getInstance()
        projectRootPath = projectManager.rootProject?.directory?.absolutePath ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 使用 ComposeView 作为 Fragment 根视图
        return ComposeView(requireContext()).apply {
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
    }

    @Composable
    fun AppRoutes(navController: NavHostController) {
        val toastState = rememberToasterState()
        val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalSharedTransitionScope provides this,
                LocalSettings provides settings,
                LocalHighlighter provides highlighter,
                LocalFirebaseAnalytics provides firebaseAnalytics,
                LocalToaster provides toastState,
            ) {
                Toaster(
                    state = toastState,
                    darkTheme = LocalDarkMode.current,
                    richColors = true,
                )
                NavHost(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    navController = navController,
                    startDestination = rememberSaveable { "chat/${Uuid.random()}" },
                    enterTransition = {
                        scaleIn(initialScale = 0.35f) + fadeIn(animationSpec = tween(300))
                    },
                    exitTransition = {
                        fadeOut(animationSpec = tween(300))
                    },
                    popExitTransition = {
                        scaleOut(
                            targetScale = 0.5f,
                            transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0.5f)
                        ) + fadeOut()
                    },
                    popEnterTransition = {
                        EnterTransition.None
                    },
                ) {
                    composableHelper(
                        route = "chat/{id}",
                        args = listOf(
                            navArgument("id") {
                                type = NavType.StringType
                            }
                        ),
                    ) { entry ->
                        ChatPage(
                            id = Uuid.parse(entry.arguments?.getString("id")!!)
                        )
                    }

                    // 其余页面保持不变
                    composableHelper("history") { HistoryPage() }
                    composableHelper("assistant") { AssistantPage() }
                    composableHelper(
                        route = "assistant/{id}",
                        args = listOf(navArgument("id") { type = NavType.StringType })
                    ) { AssistantDetailPage() }
                    composableHelper("menu") { MenuPage() }
                    composableHelper("translator") { TranslatorPage() }
                    composableHelper("setting") { SettingPage() }
                    composableHelper(
                        route = "webview?url={url}&content={content}",
                        args = listOf(
                            navArgument("url") { type = NavType.StringType; defaultValue = "" },
                            navArgument("content") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) { backStackEntry ->
                        val url = backStackEntry.arguments?.getString("url") ?: ""
                        val content = backStackEntry.arguments?.getString("content") ?: ""
                        WebViewPage(url, content)
                    }
                    composableHelper("setting/display") { SettingDisplayPage() }
                    composableHelper("setting/provider") { SettingProviderPage() }
                    composableHelper("setting/models") { SettingModelPage() }
                    composableHelper("setting/about") { SettingAboutPage() }
                    composableHelper("setting/search") { SettingSearchPage() }
                    composableHelper("setting/mcp") { SettingMcpPage() }
                    composableHelper("debug") { DebugPage() }
                }
            }
        }
    }

    @Composable
    fun ChatPage(id: Uuid) {
        var userInput by rememberSaveable { mutableStateOf("") }
        val messages = remember { mutableStateListOf<McpResponse>() }
        // 历史上传文件路径列表
        val history = remember { mutableStateListOf<String>() }
        val focusManager = LocalFocusManager.current

        // 启动 SSE 监听
        LaunchedEffect(Unit) {
            mcpClient.startListening { resp ->
                when {
                    resp.event == "file.upload.content" -> {
                        // 将收到的文件内容显示为一个消息
                        messages.add(McpResponse("file.upload", resp.data))
                        // 记录历史，将 lastSentPath 添加到 history
                        if (lastSentPath.isNotEmpty() && !history.contains(lastSentPath)) {
                            history.add(lastSentPath)
                        }
                    }
                    resp.event.startsWith("file.upload.error") -> {
                        messages.add(McpResponse("file.upload.error", resp.data))
                    }
                    else -> {
                        // 其余事件照常添加到消息列表
                        messages.add(resp)
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            onDispose {
                mcpClient.stopListening()
            }
        }

        // Scaffold 支持一个抽屉（drawerContent）来显示历史列表
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(title = { Text(text = "ChatAI - MCP") })
            },
            drawerContent = {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(200.dp)
                        .padding(8.dp)
                ) {
                    Text("上传历史", style = MaterialTheme.typography.titleMedium)
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                    LazyColumn {
                        items(history) { path ->
                            TextButton(onClick = {
                                // 重新上传历史文件
                                lastSentPath = path
                                mcpClient.sendCommand("file.upload", mapOf(
                                    "path" to path,
                                    "projectRoot" to projectRootPath
                                ))
                            }) {
                                // 仅展示文件名
                                Text(
                                    text = path.substringAfterLast(File.separator),
                                    maxLines = 1,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        value = userInput,
                        onValueChange = { userInput = it },
                        placeholder = {
                            Text(text = "输入消息或命令，例如 @File:searchName keyword=MainActivity")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            sendCommand(userInput, messages)
                            userInput = ""
                            focusManager.clearFocus()
                        })
                    )
                    Button(onClick = {
                        sendCommand(userInput, messages)
                        userInput = ""
                        focusManager.clearFocus()
                    }) {
                        Text(text = "发送")
                    }
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background),
                verticalArrangement = Arrangement.Top,
                contentPadding = PaddingValues(8.dp)
            ) {
                items(messages) { msg ->
                    MessageItem(msg)
                }
            }
        }
    }

    @Composable
    fun MessageItem(msg: McpResponse) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = msg.event,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = msg.data,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }

    /**
     * 解析用户输入并发送 MCP 命令，所有命令前缀均加上“:”
     *
     * 支持：
     *   @File:<action> key1=value1 key2=value2 …
     *   @task:<sub> …         (例如 @task:list 或 @task:execute tasks=…)
     *   @gradle:<args>        (例如 @gradle:build)
     *   @shell:<cmd>          (例如 @shell:ls /sdcard/ZeroStudioProject)
     */
    private fun sendCommand(input: String, messages: MutableList<McpResponse>) {
        val message = input.trim()
        if (message.isEmpty()) {
            messages.add(McpResponse("system.error", "输入不能为空"))
            return
        }

        when {
            // @File:searchName keyword=MainActivity
            message.startsWith("@File:") -> {
                val content = message.removePrefix("@File:").trim()
                if (content.isEmpty()) {
                    messages.add(McpResponse("file.error", "No File command provided"))
                    return
                }
                val parts = content.split(" ")
                val action = parts[0]
                val argsMap = mutableMapOf<String, String>()
                argsMap["projectRoot"] = projectRootPath
                parts.drop(1).forEach { kv ->
                    val split = kv.split("=", limit = 2)
                    if (split.size == 2) argsMap[split[0]] = split[1]
                }
                if (action == "upload") {
                    // 记录当前上传路径到 lastSentPath
                    lastSentPath = argsMap["path"] ?: ""
                }
                mcpClient.sendCommand("file.$action", argsMap)
            }

            // @task:list 或 @task:execute tasks=:app:assembleDebug
            message.startsWith("@task:") -> {
                val content = message.removePrefix("@task:").trim()
                if (content.isEmpty()) {
                    messages.add(McpResponse("task.error", "No task action provided"))
                    return
                }
                val parts = content.split(" ")
                val action = parts[0]
                val argsMap = mutableMapOf<String, String>()
                if (action == "list") {
                    mcpClient.sendCommand("task.list", argsMap)
                } else if (action == "execute") {
                    parts.drop(1).forEach { kv ->
                        val split = kv.split("=", limit = 2)
                        if (split.size == 2) argsMap[split[0]] = split[1]
                    }
                    mcpClient.sendCommand("task.execute", argsMap)
                } else {
                    messages.add(McpResponse("task.error", "Unknown task action '$action'"))
                }
            }

            // @gradle:build 或 @gradle:assembleDebug
            message.startsWith("@gradle:") -> {
                val command = message.removePrefix("@gradle:").trim()
                if (command.isEmpty()) {
                    messages.add(McpResponse("gradle.error", "No Gradle command provided"))
                    return
                }
                val argsMap = mapOf(
                    "projectRoot" to projectRootPath,
                    "command" to command
                )
                mcpClient.sendCommand("gradle.execute", argsMap)
            }

            // @shell:ls /sdcard/ZeroStudioProject
            message.startsWith("@shell:") -> {
                val cmd = message.removePrefix("@shell:").trim()
                if (cmd.isEmpty()) {
                    messages.add(McpResponse("shell.error", "No Shell command provided"))
                    return
                }
                val argsMap = mapOf("command" to cmd)
                mcpClient.sendCommand("shell.execute", argsMap)
            }

            else -> {
                // 普通聊天逻辑
                messages.add(McpResponse("chat", message))
            }
        }
    }
}

// composableHelper 保持不变
private fun NavGraphBuilder.composableHelper(
    route: String,
    args: List<NamedNavArgument> = emptyList(),
    enterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    exitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    popEnterTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition?)? = null,
    popExitTransition: (AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition?)? = null,
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) {
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
