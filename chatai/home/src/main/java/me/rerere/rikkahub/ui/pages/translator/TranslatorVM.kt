package me.rerere.rikkahub.ui.pages.translator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.handleMessageChunk
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.hooks.getCurrentAssistant
import java.util.Locale

private const val TAG = "TranslatorVM"

class TranslatorVM(
    private val settingsStore: SettingsStore,
) : ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings())

    // 翻译状态
    private val _translating = MutableStateFlow(false)
    val translating: StateFlow<Boolean> = _translating

    // 输入文本
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText

    // 翻译结果
    private val _translatedText = MutableStateFlow("")
    val translatedText: StateFlow<String> = _translatedText
    
    // 翻译目标语言
    private val _targetLanguage = MutableStateFlow(Locale.SIMPLIFIED_CHINESE)
    val targetLanguage: StateFlow<Locale> = _targetLanguage
    
    // 错误流
    val errorFlow = MutableSharedFlow<Throwable>()
    
    // 当前任务
    private var currentJob: Job? = null

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }
    
    fun updateInputText(text: String) {
        _inputText.value = text
    }
    
    fun updateTargetLanguage(language: Locale) {
        _targetLanguage.value = language
    }
    
    fun translate() {
        val inputText = _inputText.value
        if (inputText.isBlank()) return
        
        val model = settings.value.providers.findModelById(settings.value.translateModeId) ?: return
        val provider = model.findProvider(settings.value.providers) ?: return
        val assistant = settings.value.getCurrentAssistant()
        
        // 取消当前任务
        currentJob?.cancel()
        
        // 设置翻译中状态
        _translating.value = true
        _translatedText.value = ""
        
        currentJob = viewModelScope.launch {
            runCatching {
                val providerHandler = ProviderManager.getProviderByType(provider)
                val prompt = """
                    你是一个翻译专家，擅长翻译各国语言，并且保持翻译准确和信达雅。
                    接下来我会给你发送文本，请将其翻译为 ${targetLanguage.value}，直接返回翻译结果，不要添加任何解释和其他内容。
                    
                    请翻译<source_text>部分:
                    
                    <source_text>
                    $inputText
                    </source_text>
                """.trimIndent()

                var messages = listOf(
                    UIMessage.user(prompt)
                )

                providerHandler.streamText(
                    providerSetting = provider,
                    messages = listOf(UIMessage.user(prompt)),
                    params = TextGenerationParams(
                        model = model,
                        temperature = assistant.temperature,
                    ),
                ).collect { chunk ->
                    messages = messages.handleMessageChunk(chunk)
                    _translatedText.value = messages.lastOrNull()?.toText() ?: ""
                }
            }.onFailure {
                it.printStackTrace()
                errorFlow.emit(it)
            }
            
            _translating.value = false
        }
    }
    
    fun cancelTranslation() {
        currentJob?.cancel()
        _translating.value = false
    }
}