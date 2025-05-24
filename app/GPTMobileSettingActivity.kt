
package com.itsaky.androidide.preferences

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import android.zero.studio.chatai.presentation.ui.setting.PlatformSettingScreen
import android.zero.studio.chatai.data.model.ApiType
import android.zero.studio.chatai.presentation.ui.setting.SettingViewModel

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
    val settingViewModel: SettingViewModel = hiltViewModel()
    // PlatformSettingScreen(
        // apiType = ApiType.OPENAI, //跳转到ai平台详细设置，比如：api_key，top p等
        // settingViewModel = settingViewModel
    // ) {
        // // 返回事件逻辑
    // }
}