/*
* 本fragment是根据RouteActivity.kt改编
* by android_zero  别称：零丶
*/

package me.rerere.rikkahub

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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
import me.rerere.rikkahub.ui.pages.backup.BackupPage
import me.rerere.rikkahub.ui.pages.chat.ChatPage
import me.rerere.rikkahub.ui.pages.debug.DebugPage
import me.rerere.rikkahub.ui.pages.history.HistoryPage
import me.rerere.rikkahub.ui.pages.menu.MenuPage
import me.rerere.rikkahub.ui.pages.setting.SettingAboutPage
import me.rerere.rikkahub.ui.pages.setting.SettingDisplayPage
import me.rerere.rikkahub.ui.pages.setting.SettingMcpPage
import me.rerere.rikkahub.ui.pages.setting.SettingModelPage
import me.rerere.rikkahub.ui.pages.setting.SettingPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderDetailPage
import me.rerere.rikkahub.ui.pages.setting.SettingProviderPage
import me.rerere.rikkahub.ui.pages.setting.SettingSearchPage
import me.rerere.rikkahub.ui.pages.share.handler.ShareHandlerPage
import me.rerere.rikkahub.ui.pages.translator.TranslatorPage
import me.rerere.rikkahub.ui.pages.webview.WebViewPage
import me.rerere.rikkahub.ui.theme.LocalDarkMode
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import me.rerere.rikkahub.utils.base64Encode
import okhttp3.OkHttpClient
import org.koin.android.ext.android.inject
import kotlin.uuid.Uuid

private const val TAG = "ChatAiFragment"
private const val ARG_SHARE_TEXT = "share_text"

class ChatAiFragment : Fragment() {
    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()
    
    // 处理Activity传递的分享意图
    private var initialShareText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAnalytics = Firebase.analytics
        
        // 从Arguments获取分享文本（如果有）
        initialShareText = arguments?.getString(ARG_SHARE_TEXT)
        
        // 处理Activity的Intent（适用于Fragment直接作为主界面的情况）
        if (initialShareText == null && activity != null) {
            handleActivityIntent(activity?.intent)
        }
    }
    
    // 处理Activity的Intent数据
    private fun handleActivityIntent(intent: Intent?) {
        if (intent != null && intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (text != null) {
                initialShareText = text
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val navController = rememberNavController()
                
                // 处理初始分享文本
                LaunchedEffect(Unit) {
                    initialShareText?.let { text ->
                        navController.navigate("share/handler?text=${text.base64Encode()}") {
                            popUpTo(navController.graph.startDestinationId) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }
                
                RikkahubTheme {
                    // 配置Coil图片加载器
                    setSingletonImageLoaderFactory { context ->
                        ImageLoader.Builder(context)
                            .crossfade(true)
                            .components {
                                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                                add(SvgDecoder.Factory(scaleToDensity = true))
                            }
                            .build()
                    }
                    
                    // 提供应用路由
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
                        slideInHorizontally(
                            initialOffsetX = { it }
                        )
                    },
                    exitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = {
                                -it / 2
                            }
                        ) + fadeOut()
                    },
                    popExitTransition = {
                        slideOutHorizontally(
                            targetOffsetX = {
                                it
                            }
                        ) + fadeOut()
                    },
                    popEnterTransition = {
                        slideInHorizontally(
                            initialOffsetX = {
                                -it / 2
                            }
                        )
                    },
                ) {
                    // 聊天页面路由
                    composableHelper(
                        route = "chat/{id}?text={text}",
                        args = listOf(
                            navArgument("id") {
                                type = NavType.StringType
                            },
                            navArgument("text") {
                                type = NavType.StringType
                                nullable = true
                            }
                        ),
                        enterTransition = { fadeIn() },
                        exitTransition = { fadeOut() }
                    ) { entry ->
                        ChatPage(
                            id = Uuid.parse(entry.arguments?.getString("id")!!),
                            text = entry.arguments?.getString("text")
                        )
                    }
                    
                    // 分享处理页面路由
                    composableHelper(
                        route = "share/handler?text={text}",
                        args = listOf(
                            navArgument("text") {
                                type = NavType.StringType
                            }
                        )
                    ) {
                        ShareHandlerPage()
                    }
                    
                    // 其他页面路由（与原Activity保持一致）
                    composableHelper("history") {
                        HistoryPage()
                    }
                    
                    composableHelper("assistant") {
                        AssistantPage()
                    }
                    
                    composableHelper(
                        route = "assistant/{id}",
                        args = listOf(
                            navArgument("id") {
                                type = NavType.StringType
                            }
                        ),
                    ) {
                        AssistantDetailPage()
                    }
                    
                    composableHelper("menu") {
                        MenuPage()
                    }
                    
                    composableHelper("translator") {
                        TranslatorPage()
                    }
                    
                    composableHelper("setting") {
                        SettingPage()
                    }
                    
                    composableHelper("backup") {
                        BackupPage()
                    }
                    
                    composableHelper(
                        route = "webview?url={url}&content={content}",
                        args = listOf(
                            navArgument("url") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("content") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        ),
                    ) {
                        val url = it.arguments?.getString("url") ?: ""
                        val content = it.arguments?.getString("content") ?: ""
                        WebViewPage(url, content)
                    }
                    
                    composableHelper("setting/display") {
                        SettingDisplayPage()
                    }
                    
                    composableHelper("setting/provider") {
                        SettingProviderPage()
                    }
                    
                    composableHelper("setting/provider/{providerId}") {
                        val id = Uuid.parse(checkNotNull(it.arguments?.getString("providerId")))
                        SettingProviderDetailPage(id = id)
                    }
                    
                    composableHelper("setting/models") {
                        SettingModelPage()
                    }
                    
                    composableHelper("setting/about") {
                        SettingAboutPage()
                    }
                    
                    composableHelper("setting/search") {
                        SettingSearchPage()
                    }
                    
                    composableHelper("setting/mcp") {
                        SettingMcpPage()
                    }
                    
                    composableHelper("debug") {
                        DebugPage()
                    }
                }
            }
        }
    }
}


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
