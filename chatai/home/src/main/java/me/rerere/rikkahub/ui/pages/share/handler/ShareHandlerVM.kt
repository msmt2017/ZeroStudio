package me.rerere.rikkahub.ui.pages.share.handler

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.utils.base64Decode
import kotlin.uuid.Uuid

class ShareHandlerVM(
  savedStateHandle: SavedStateHandle,
  private val settingsStore: SettingsStore
) : ViewModel() {
  val shareText = checkNotNull(savedStateHandle.get<String>("text")?.base64Decode())
  val settings = settingsStore.settingsFlow
    .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

  suspend fun updateAssistant(assistantId: Uuid) {
    settingsStore.updateAssistant(assistantId)
  }
}