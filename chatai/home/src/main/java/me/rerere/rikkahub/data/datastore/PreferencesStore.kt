package me.rerere.rikkahub.data.datastore

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.datastore.core.IOException
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.pebbletemplates.pebble.PebbleEngine
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.Serializable
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.AppScope
import me.rerere.rikkahub.data.mcp.McpServerConfig
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.theme.PresetThemeType
import me.rerere.rikkahub.ui.theme.PresetThemes
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.utils.toMutableStateFlow
import me.rerere.search.SearchCommonOptions
import me.rerere.search.SearchServiceOptions
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "PreferencesStore"

private val Context.settingsStore by preferencesDataStore(
  name = "settings",
  produceMigrations = { context ->
    listOf(
      SharedPreferencesMigration(context, "settings"),
    )
  }
)

class SettingsStore(
  context: Context,
  scope: AppScope,
) : KoinComponent {
  companion object {
    // UI设置
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val THEME_ID = stringPreferencesKey("theme_id")
    val THEME_TYPE = stringPreferencesKey("theme_type")
    val DISPLAY_SETTING = stringPreferencesKey("display_setting")

    // 模型选择
    val ENABLE_WEB_SEARCH = booleanPreferencesKey("enable_web_search")
    val SELECT_MODEL = stringPreferencesKey("chat_model")
    val TITLE_MODEL = stringPreferencesKey("title_model")
    val TRANSLATE_MODEL = stringPreferencesKey("translate_model")
    val SUGGESTION_MODEL = stringPreferencesKey("suggestion_model")
    val TITLE_PROMPT = stringPreferencesKey("title_prompt")
    val TRANSLATION_PROMPT = stringPreferencesKey("translation_prompt")
    val SUGGESTION_PROMPT = stringPreferencesKey("suggestion_prompt")

    // 提供商
    val PROVIDERS = stringPreferencesKey("providers")

    // 助手
    val SELECT_ASSISTANT = stringPreferencesKey("select_assistant")
    val ASSISTANTS = stringPreferencesKey("assistants")

    // 搜索
    val SEARCH_SERVICE = stringPreferencesKey("search_service")
    val SEARCH_COMMON = stringPreferencesKey("search_common")

    // MCP
    val MCP_SERVERS = stringPreferencesKey("mcp_servers")

    // WebDAV
    val WEBDAV_CONFIG = stringPreferencesKey("webdav_config")
  }

  private val dataStore = context.settingsStore

  val settingsFlowRaw = dataStore.data
    .catch { exception ->
      if (exception is IOException) {
        emit(emptyPreferences())
      } else {
        throw exception
      }
    }.map { preferences ->
      Settings(
        enableWebSearch = preferences[ENABLE_WEB_SEARCH] == true,
        chatModelId = preferences[SELECT_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
        titleModelId = preferences[TITLE_MODEL]?.let { Uuid.parse(it) } ?: Uuid.random(),
        translateModeId = preferences[TRANSLATE_MODEL]?.let { Uuid.parse(it) }
          ?: Uuid.random(),
        suggestionModelId = preferences[SUGGESTION_MODEL]?.let { Uuid.parse(it) }
          ?: Uuid.random(),
        titlePrompt = preferences[TITLE_PROMPT] ?: DEFAULT_TITLE_PROMPT,
        translatePrompt = preferences[TRANSLATION_PROMPT] ?: DEFAULT_TRANSLATION_PROMPT,
        suggestionPrompt = preferences[SUGGESTION_PROMPT] ?: DEFAULT_SUGGESTION_PROMPT,
        assistantId = preferences[SELECT_ASSISTANT]?.let { Uuid.parse(it) }
          ?: DEFAULT_ASSISTANT_ID,
        providers = JsonInstant.decodeFromString(preferences[PROVIDERS] ?: "[]"),
        assistants = JsonInstant.decodeFromString(preferences[ASSISTANTS] ?: "[]"),
        dynamicColor = preferences[DYNAMIC_COLOR] != false,
        themeId = preferences[THEME_ID] ?: PresetThemes[0].id,
        themeType = preferences[THEME_TYPE]?.let {
          JsonInstant.decodeFromString(it)
        } ?: PresetThemeType.STANDARD,
        displaySetting = JsonInstant.decodeFromString(preferences[DISPLAY_SETTING] ?: "{}"),
        searchServiceOptions = preferences[SEARCH_SERVICE]?.let {
          JsonInstant.decodeFromString(it)
        } ?: SearchServiceOptions.DEFAULT,
        searchCommonOptions = preferences[SEARCH_COMMON]?.let {
          JsonInstant.decodeFromString(it)
        } ?: SearchCommonOptions(),
        mcpServers = preferences[MCP_SERVERS]?.let {
          JsonInstant.decodeFromString(it)
        } ?: emptyList(),
        webDavConfig = preferences[WEBDAV_CONFIG]?.let {
          JsonInstant.decodeFromString(it)
        } ?: WebDavConfig(),
      )
    }
    .map {
      var providers = it.providers.ifEmpty { DEFAULT_PROVIDERS }.toMutableList()
      DEFAULT_PROVIDERS.forEach { defaultProvider ->
        if (providers.none { it.id == defaultProvider.id }) {
          providers.add(defaultProvider.copyProvider())
        }
      }
      providers = providers.map { provider ->
        val defaultProvider = DEFAULT_PROVIDERS.find { it.id == provider.id }
        if (defaultProvider != null) {
          provider.copyProvider(
            builtIn = defaultProvider.builtIn,
            description = defaultProvider.description,
          )
        } else provider
      }.toMutableList()
      val assistants = it.assistants.ifEmpty { DEFAULT_ASSISTANTS }.toMutableList()
      DEFAULT_ASSISTANTS.forEach { defaultAssistant ->
        if (assistants.none { it.id == defaultAssistant.id }) {
          assistants.add(defaultAssistant.copy())
        }
      }
      it.copy(
        providers = providers,
        assistants = assistants
      )
    }
    .map { settings ->
      // 去重
      settings.copy(
        providers = settings.providers.distinctBy { it.id }.map { provider ->
          when (provider) {
            is ProviderSetting.OpenAI -> provider.copy(
              models = provider.models.distinctBy { model -> model.id }
            )

            is ProviderSetting.Google -> provider.copy(
              models = provider.models.distinctBy { model -> model.id }
            )

            is ProviderSetting.Claude -> provider.copy(
              models = provider.models.distinctBy { model -> model.id }
            )
          }
        },
        assistants = settings.assistants.distinctBy { it.id },
      )
    }
    .onEach {
      get<PebbleEngine>().templateCache.invalidateAll()
    }

  val settingsFlow = settingsFlowRaw
    .distinctUntilChanged()
    .toMutableStateFlow(scope, Settings())

  suspend fun update(settings: Settings) {
    settingsFlow.value = settings
    dataStore.edit { preferences ->
      preferences[DYNAMIC_COLOR] = settings.dynamicColor
      preferences[THEME_ID] = settings.themeId
      preferences[THEME_TYPE] = JsonInstant.encodeToString(settings.themeType)
      preferences[DISPLAY_SETTING] = JsonInstant.encodeToString(settings.displaySetting)

      preferences[ENABLE_WEB_SEARCH] = settings.enableWebSearch
      preferences[SELECT_MODEL] = settings.chatModelId.toString()
      preferences[TITLE_MODEL] = settings.titleModelId.toString()
      preferences[TRANSLATE_MODEL] = settings.translateModeId.toString()
      preferences[SUGGESTION_MODEL] = settings.suggestionModelId.toString()
      preferences[TITLE_PROMPT] = settings.titlePrompt
      preferences[TRANSLATION_PROMPT] = settings.translatePrompt
      preferences[SUGGESTION_PROMPT] = settings.suggestionPrompt

      preferences[PROVIDERS] = JsonInstant.encodeToString(settings.providers)

      preferences[ASSISTANTS] = JsonInstant.encodeToString(settings.assistants)
      preferences[SELECT_ASSISTANT] = settings.assistantId.toString()

      preferences[SEARCH_SERVICE] = JsonInstant.encodeToString(settings.searchServiceOptions)
      preferences[SEARCH_COMMON] = JsonInstant.encodeToString(settings.searchCommonOptions)

      preferences[MCP_SERVERS] = JsonInstant.encodeToString(settings.mcpServers)
      preferences[WEBDAV_CONFIG] = JsonInstant.encodeToString(settings.webDavConfig)
    }
  }

  suspend fun update(fn: (Settings) -> Settings) {
    update(fn(settingsFlow.value))
  }

  suspend fun updateAssistant(assistantId: Uuid) {
    dataStore.edit { preferences ->
      preferences[SELECT_ASSISTANT] = assistantId.toString()
    }
  }
}

@Serializable
data class Settings(
  val dynamicColor: Boolean = true,
  val themeId: String = PresetThemes[0].id,
  val themeType: PresetThemeType = PresetThemeType.STANDARD,
  val displaySetting: DisplaySetting = DisplaySetting(),
  val enableWebSearch: Boolean = false,
  val chatModelId: Uuid = Uuid.random(),
  val titleModelId: Uuid = Uuid.random(),
  val titlePrompt: String = DEFAULT_TITLE_PROMPT,
  val translateModeId: Uuid = Uuid.random(),
  val translatePrompt: String = DEFAULT_TRANSLATION_PROMPT,
  val suggestionModelId: Uuid = Uuid.random(),
  val suggestionPrompt: String = DEFAULT_SUGGESTION_PROMPT,
  val assistantId: Uuid = DEFAULT_ASSISTANT_ID,
  val providers: List<ProviderSetting> = DEFAULT_PROVIDERS,
  val assistants: List<Assistant> = DEFAULT_ASSISTANTS,
  val searchServiceOptions: SearchServiceOptions = SearchServiceOptions.DEFAULT,
  val searchCommonOptions: SearchCommonOptions = SearchCommonOptions(),
  val mcpServers: List<McpServerConfig> = emptyList(),
  val webDavConfig: WebDavConfig = WebDavConfig()
)

@Serializable
data class DisplaySetting(
  val showModelIcon: Boolean = false,
  val showTokenUsage: Boolean = true,
  val autoCloseThinking: Boolean = true,
  val showUpdates: Boolean = true,
  val showMessageJumper: Boolean = true,
)

@Serializable
data class WebDavConfig(
  val url: String = "",
  val username: String = "",
  val password: String = "",
  val path: String = "rikkahub_backups",
  val items: List<BackupItem> = listOf(
    BackupItem.DATABASE,
    BackupItem.FILES
  ),
) {
  @Serializable
  enum class BackupItem {
    DATABASE,
    FILES,
  }
}

fun Settings.isNotConfigured() = providers.all { it.models.isEmpty() }

fun Settings.findModelById(uuid: Uuid): Model? {
  return this.providers.findModelById(uuid)
}

fun List<ProviderSetting>.findModelById(uuid: Uuid): Model? {
  this.forEach { setting ->
    setting.models.forEach { model ->
      if (model.id == uuid) {
        return model
      }
    }
  }
  return null
}

fun Settings.getCurrentChatModel(): Model? {
  return findModelById(this.getCurrentAssistant().chatModelId ?: this.chatModelId)
}

fun Settings.getCurrentAssistant(): Assistant {
  return this.assistants.find { it.id == assistantId } ?: this.assistants.first()
}

fun Model.findProvider(providers: List<ProviderSetting>): ProviderSetting? {
  providers.forEach { setting ->
    setting.models.forEach { model ->
      if (model.id == this.id) {
        return setting
      }
    }
  }
  return null
}

private val DEFAULT_PROVIDERS = listOf(
  ProviderSetting.OpenAI(
    id = Uuid.parse("1eeea727-9ee5-4cae-93e6-6fb01a4d051e"),
    name = "OpenAI",
    baseUrl = "https://api.openai.com/v1",
    apiKey = "sk-",
    builtIn = true
  ),
  ProviderSetting.Google(
    id = Uuid.parse("6ab18148-c138-4394-a46f-1cd8c8ceaa6d"),
    name = "Gemini",
    apiKey = "",
    enabled = true,
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("f099ad5b-ef03-446d-8e78-7e36787f780b"),
    name = "DeepSeek",
    baseUrl = "https://api.deepseek.com/v1",
    apiKey = "sk-",
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("56a94d29-c88b-41c5-8e09-38a7612d6cf8"),
    name = "硅基流动",
    baseUrl = "https://api.siliconflow.cn/v1",
    apiKey = "sk-",
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("d5734028-d39b-4d41-9841-fd648d65440e"),
    name = "OpenRouter",
    baseUrl = "https://openrouter.ai/api/v1",
    apiKey = "",
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("f76cae46-069a-4334-ab8e-224e4979e58c"),
    name = "阿里云百炼",
    baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    apiKey = "",
    enabled = false,
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("3dfd6f9b-f9d9-417f-80c1-ff8d77184191"),
    name = "火山引擎",
    baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
    apiKey = "",
    enabled = false,
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("3bc40dc1-b11a-46fa-863b-6306971223be"),
    name = "智谱AI开放平台",
    baseUrl = "https://open.bigmodel.cn/api/paas/v4",
    apiKey = "",
    enabled = false,
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("89e67540-32fe-4c62-9970-2e9aed9bd59d"),
    name = "JuheNext",
    baseUrl = "https://api.juheai.top/v1",
    apiKey = "",
    enabled = false,
    builtIn = true,
    description = {
      Text(
        text = buildAnnotatedString {
          append("一站式API中转平台, 官网：")
          withLink(LinkAnnotation.Url("https://api.juheai.top")) {
            withStyle(SpanStyle(MaterialTheme.colorScheme.primary)) {
              append("https://api.juheai.top")
            }
          }
        }
      )
    }
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("ef5d149b-8e34-404b-818c-6ec242e5c3c5"),
    name = "腾讯Hunyuan",
    baseUrl = "https://api.hunyuan.cloud.tencent.com/v1",
    apiKey = "",
    enabled = false,
    builtIn = true
  ),
  ProviderSetting.OpenAI(
    id = Uuid.parse("ff3cde7e-0f65-43d7-8fb2-6475c99f5990"),
    name = "xAI",
    baseUrl = "https://api.x.ai/v1",
    apiKey = "",
    enabled = false,
    builtIn = true
  )
)

internal val DEFAULT_ASSISTANT_ID = Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
internal val DEFAULT_ASSISTANTS = listOf(
  Assistant(
    id = DEFAULT_ASSISTANT_ID,
    name = "",
    temperature = 0.6f,
    systemPrompt = ""
  ),
  Assistant(
    id = Uuid.parse("3d47790c-c415-4b90-9388-751128adb0a0"),
    name = "示例助手",
    temperature = 0.6f,
    systemPrompt = "你是{model_name}, 一个人工智能助手，乐意为用户提供准确，有益的帮助。现在时间是{cur_datetime}，用户设备语言为\"{locale}\"，时区为{timezone}，用户正在使用{device_info}，版本{system_version}。如果用户没有明确说明，请使用用户设备语言和用户对话。"
  ),
)
internal val DEFAULT_ASSISTANTS_IDS = DEFAULT_ASSISTANTS.map { it.id }

internal val DEFAULT_TITLE_PROMPT = """
    I will give you some dialogue content in the `<content>` block.
    You need to summarize the conversation between user and assistant into a short title.
    1. The title language should be consistent with the user's primary language
    2. Do not use punctuation or other special symbols
    3. Reply directly with the title
    4. Summarize using {locale} language
    5. The title should not exceed 10 characters
    
    <content>
    {content}
    </content>
""".trimIndent()

internal val DEFAULT_SUGGESTION_PROMPT = """
    I will provide you with some dialogue content in the `<content>` block, including conversations between the user and the assistant.
    You need to act as the user to reply to the assistant, generating 3~5 appropriate and contextually relevant responses to the assistant.
    
    Rules:
    1. Reply directly with suggestions, do not add any formatting, and separate suggestions with newlines, no need to add markdown list formats.
    2. Use {locale} language.
    3. Ensure each suggestion is valid.
    4. Each suggestion should not exceed 10 characters.
    5. Imitate the user's previous conversational style.
    
    <content>
    {content}
    </content>
""".trimIndent()

internal val DEFAULT_TRANSLATION_PROMPT = """
    You are a translation expert, skilled in translating various languages, and maintaining accuracy, faithfulness, and elegance in translation.
    Next, I will send you text. Please translate it into {target_lang}, and return the translation result directly, without adding any explanations or other content.
                    
    Please translate the <source_text> section:
    
    <source_text>
    {source_text}
    </source_text>
""".trimIndent()