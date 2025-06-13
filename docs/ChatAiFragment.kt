

/*
*本fragment是根据RouteActivity.kt改编
*原版ChatAiFragment
*by android_zero  别称：零丶
*/


package me.rerere.rikkahub

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import androidx.compose.ui.platform.ComposeView // 导入 ComposeView
import androidx.fragment.app.Fragment // 导入 Fragment androidx-fragment_ktx
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

class ChatAiFragment : Fragment() {

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    // Koin 注入在 Fragment 中同样有效
    private val highlighter by inject<Highlighter>()
    private val okHttpClient by inject<OkHttpClient>()
    private val settingsStore by inject<SettingsStore>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Firebase Analytics 初始化可以在 Fragment 中进行，因为它需要 Context
        firebaseAnalytics = Firebase.analytics
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 使用 ComposeView 作为 Fragment 的根视图
        return ComposeView(requireContext()).apply {
            // 设置 Compose 内容
            setContent {
                val navController = rememberNavController()
                RikkahubTheme {
                    // ImageLoaderFactory 最好在 Application 类中设置一次，避免在每个 Fragment/Composable 中重复设置
                    // 但如果仅此 Fragment 使用或调试需要，此处设置也无妨
                    setSingletonImageLoaderFactory { context ->
                        ImageLoader.Builder(context)
                            .crossfade(true)
                            .components {
                                // 这里的 okHttpClient 通过 Koin 注入到 Fragment，可以正常使用
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

    // AppRoutes Composable 保持不变
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

// composableHelper 函数也保持不变
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
