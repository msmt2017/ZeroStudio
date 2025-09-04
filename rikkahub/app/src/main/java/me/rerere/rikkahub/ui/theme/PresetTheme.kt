package me.rerere.rikkahub.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
import me.rerere.rikkahub.ui.theme.presets.BlackThemePreset
import me.rerere.rikkahub.ui.theme.presets.OceanThemePreset
import me.rerere.rikkahub.ui.theme.presets.SakuraThemePreset
import me.rerere.rikkahub.ui.theme.presets.SpringThemePreset

data class PresetTheme(
    val id: String,
    val name: @Composable () -> Unit,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
    val mediumContrastLight: ColorScheme,
    val mediumContrastDark: ColorScheme,
    val highContrastLight: ColorScheme,
    val highContrastDark: ColorScheme,
) {
    fun getColorScheme(type: PresetThemeType, dark: Boolean): ColorScheme {
        return when (type) {
            PresetThemeType.STANDARD -> if (dark) standardDark else standardLight
            PresetThemeType.MEDIUM_CONTRAST -> if (dark) mediumContrastDark else mediumContrastLight
            PresetThemeType.HIGH_CONTRAST -> if (dark) highContrastDark else highContrastLight
        }
    }
}

val PresetThemes by lazy {
    listOf(
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        BlackThemePreset
    )
}

fun findPresetTheme(id: String): PresetTheme {
    return PresetThemes.find { it.id == id } ?: SakuraThemePreset
}

@Serializable
enum class PresetThemeType {
    STANDARD,
    MEDIUM_CONTRAST,
    HIGH_CONTRAST
}
