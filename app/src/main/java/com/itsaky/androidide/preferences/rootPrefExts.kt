

package com.itsaky.androidide.preferences

import com.itsaky.androidide.resources.R.string
import com.itsaky.androidide.resources.R.xml
import com.itsaky.androidide.resources.R.drawable

// Android 和基础依赖
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.TextView

import androidx.preference.Preference

// Kotlin 序列化
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue

// Hilt 依赖注入
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.hilt.navigation.compose.hiltViewModel

// Jetpack Compose
import androidx.compose.runtime.Composable
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import android.zero.studio.chatai.data.model.ApiType
import android.zero.studio.chatai.data.ModelConstants
import android.zero.studio.chatai.presentation.ui.setting.PlatformSettingScreen
import android.zero.studio.chatai.presentation.ui.setting.SettingViewModel
import com.itsaky.androidide.preferences.SimpleClickablePreference

internal fun IDEPreferences.addRootPreferences() {
  addPreference(ConfigurationPreferences())
   addPreference(chataiPreferences())
    addPreference(DeveloperOptionsPreferences())
 // addPreference(PrivacyPreferences())
  addPreference(AboutPreferences())
}

@Parcelize
class ConfigurationPreferences(
  override val key: String = "idepref_configure",
  override val title: Int = string.configure,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    addPreference(GeneralPreferences())
    addPreference(EditorPreferences())
    addPreference(BuildAndRunPreferences())
    addPreference(TermuxPreferences())
  }
}

// 


@Parcelize
class chataiPreferences(
  override val key: String = "idepref_category_chatai",
  override val title: Int = string.ai_set,
  
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {

    addPreference(chatai)
  }
}




@Parcelize
class DeveloperOptionsPreferences(
  override val key: String = "idepref_devOpts",
  override val title: Int = string.title_developer_options,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
    addPreference(DeveloperOptionsScreen())
  }
}


// @Parcelize
// class PrivacyPreferences(
  // override val key: String = "idepref_privacy",
  // override val title: Int = string.title_privacy,
  // override val children: List<IPreference> = mutableListOf()
// ) : IPreferenceGroup() {

  // init {
    // addPreference(StatPreferences())
  // }
// }


@Parcelize
class AboutPreferences(
  override val key: String = "idepref_category_about",
  override val title: Int = string.about,
  override val children: List<IPreference> = mutableListOf()
) : IPreferenceGroup() {

  init {
  addPreference(StatPreferences())
  //  addPreference(changelog)
    addPreference(about)
  }
}
