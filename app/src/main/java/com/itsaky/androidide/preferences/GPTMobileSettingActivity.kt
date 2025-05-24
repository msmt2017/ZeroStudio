package com.itsaky.androidide.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import android.zero.studio.chatai.presentation.common.Route
import android.zero.studio.chatai.presentation.ui.setting.SettingScreen
import android.zero.studio.chatai.presentation.ui.setting.PlatformSettingScreen
import android.zero.studio.chatai.presentation.ui.setting.SettingViewModel
import android.zero.studio.chatai.data.model.ApiType

@AndroidEntryPoint
class GPTMobileSettingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GPTMobileSettingScreen()
        }
    }
}

@Composable
fun GPTMobileSettingScreen() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Route.SETTING_ROUTE
    ) {
        settingNavigation(navController)
    }
}

private fun NavGraphBuilder.settingNavigation(navController: NavHostController) {
    navigation(
        startDestination = Route.SETTINGS,
        route = Route.SETTING_ROUTE
    ) {
        // 主设置页面
        composable(Route.SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            
            SettingScreen(
                settingViewModel = settingViewModel,
                onNavigationClick = { navController.navigateUp() },
                onNavigateToPlatformSetting = { apiType ->
                    when (apiType) {
                        ApiType.OPENAI -> navController.navigate(Route.OPENAI_SETTINGS)
                        ApiType.ANTHROPIC -> navController.navigate(Route.ANTHROPIC_SETTINGS)
                        ApiType.GOOGLE -> navController.navigate(Route.GOOGLE_SETTINGS)
                        ApiType.GROQ -> navController.navigate(Route.GROQ_SETTINGS)
                        ApiType.OLLAMA -> navController.navigate(Route.OLLAMA_SETTINGS)
                    }
                },
                onNavigateToAboutPage = { navController.navigate(Route.ABOUT_PAGE) }
            )
        }

        // OpenAI 设置页面
        composable(Route.OPENAI_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            PlatformSettingScreen(
                settingViewModel = settingViewModel,
                apiType = ApiType.OPENAI
            ) { navController.navigateUp() }
        }

        // Anthropic 设置页面
        composable(Route.ANTHROPIC_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            PlatformSettingScreen(
                settingViewModel = settingViewModel,
                apiType = ApiType.ANTHROPIC
            ) { navController.navigateUp() }
        }

        // Google 设置页面
        composable(Route.GOOGLE_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            PlatformSettingScreen(
                settingViewModel = settingViewModel,
                apiType = ApiType.GOOGLE
            ) { navController.navigateUp() }
        }

        // Groq 设置页面
        composable(Route.GROQ_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            PlatformSettingScreen(
                settingViewModel = settingViewModel,
                apiType = ApiType.GROQ
            ) { navController.navigateUp() }
        }

        // Ollama 设置页面
        composable(Route.OLLAMA_SETTINGS) {
            val parentEntry = remember(it) {
                navController.getBackStackEntry(Route.SETTING_ROUTE)
            }
            val settingViewModel: SettingViewModel = hiltViewModel(parentEntry)
            PlatformSettingScreen(
                settingViewModel = settingViewModel,
                apiType = ApiType.OLLAMA
            ) { navController.navigateUp() }
        }

        // About页面和License页面的导航保持注释状态，与原代码一致
        // composable(Route.ABOUT_PAGE) {
        //     AboutScreen(
        //         onNavigationClick = { navController.navigateUp() },
        //         onNavigationToLicense = { navController.navigate(Route.LICENSE) }
        //     )
        // }
        // composable(Route.LICENSE) {
        //     LicenseScreen(onNavigationClick = { navController.navigateUp() })
        // }
    }
}