package android.zero.studio.chatai.data.dto

import android.zero.studio.chatai.data.model.DynamicTheme
import android.zero.studio.chatai.data.model.ThemeMode

data class ThemeSetting(
    val dynamicTheme: DynamicTheme = DynamicTheme.OFF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)
