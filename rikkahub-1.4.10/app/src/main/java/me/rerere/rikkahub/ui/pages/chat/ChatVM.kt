package me.rerere.rikkahub.ui.pages.chat

import android.app.Application
import android.util.Log
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.MessageRole
import me.rerere.ai.core.Tool
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.TextGenerationParams
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.finishReasoning
import me.rerere.ai.ui.isEmptyInputMessage
import me.rerere.ai.ui.truncate
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.transformers.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.transformers.DocumentAsPromptTransformer
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.ai.transformers.HtmlEscapeTransformer
import me.rerere.rikkahub.data.ai.transformers.PlaceholderTransformer
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.ThinkTagTransformer
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.mcp.McpManager
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.ui.hooks.writeStringPreference
import me.rerere.rikkahub.utils.JsonInstantPretty
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.UpdateChecker
import me.rerere.rikkahub.utils.applyPlaceholders
import kotlinx.coroutines.Dispatchers
import me.rerere.rikkahub.data.ai.LocalTools
import me.rerere.rikkahub.utils.deleteChatFiles
import me.rerere.search.SearchService
import me.rerere.search.SearchServiceOptions
import java.time.Instant
import java.util.Locale
import kotlin.uuid.Uuid

private const val TAG = "ChatVM"

private val inputTransformers by lazy {
    listOf(
        PlaceholderTransformer,
        DocumentAsPromptTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer,
        HtmlEscapeTransformer,
    )
}

class ChatVM(
    id: String,
    private val context: Application,
    private val settingsStore: SettingsStore,
    private val conversationRepo: ConversationRepository,
    private val memoryRepository: MemoryRepository,
    private val generationHandler: GenerationHandler,
    private val templateTransformer: TemplateTransformer,
    private val providerManager: ProviderManager,
    private val localTools: LocalTools,
    val mcpManager: McpManager,
    val updateChecker: UpdateChecker,
) : ViewModel() {
    private val _conversationId: Uuid = Uuid.parse(id)
    private val _conversation = MutableStateFlow(Conversation.ofId(_conversationId))
    val conversation: StateFlow<Conversation>
        get() = _conversation

    // 异步任务
    val conversationJob = MutableStateFlow<Job?>(null)

    init {
        // Load the conversation from the repository (database)
        viewModelScope.launch {
            val conversation = conversationRepo.getConversationById(_conversationId)
            if (conversation != null) {
                this@ChatVM._conversation.value = conversation

                // 更新当前助手到 conversation 所属的 assistant
                // 这里不能用 updateSettings，因为 settings 可能还没加载
                settingsStore.updateAssistant(conversation.assistantId)
            } else {
                // 新建对话, 并添加预设消息
                val currentSettings = settingsStore.settingsFlowRaw.first()
                val assistant = currentSettings.getCurrentAssistant()
                this@ChatVM._conversation.value =
                    this@ChatVM._conversation.value.updateCurrentMessages(assistant.presetMessages)
            }
        }

        // 记住对话ID, 方便下次启动恢复
        context.writeStringPreference("lastConversationId", _conversationId.toString())
    }

    // 用户设置
    val settings: StateFlow<Settings> =
        settingsStore.settingsFlow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    // 网络搜索
    val enableWebSearch = settings.map {
        it.enableWebSearch
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // 聊天列表
    val conversations =
        settings.map { it.assistantId }.distinctUntilChanged().flatMapLatest { assistantId ->
            conversationRepo.getConversationsOfAssistant(assistantId).catch {
                Log.e(TAG, "conversationRepo.getAllConversations: ", it)
                errorFlow.emit(it)
                emit(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 当前模型
    val currentChatModel = settings.map { settings ->
        settings.getCurrentChatModel()
    }.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // 错误流
    val errorFlow = MutableSharedFlow<Throwable>()

    // 生成完成
    val generationDoneFlow = MutableSharedFlow<Uuid>()

    // 更新设置
    fun updateSettings(newSettings: Settings) {
        viewModelScope.launch {
            val oldSettings = settings.value
            // 检查用户头像是否有变化，如果有则删除旧头像
            checkUserAvatarDelete(oldSettings, newSettings)
            settingsStore.update(newSettings)
        }
    }

    // 检查用户头像删除
    private fun checkUserAvatarDelete(oldSettings: Settings, newSettings: Settings) {
        val oldAvatar = oldSettings.displaySetting.userAvatar
        val newAvatar = newSettings.displaySetting.userAvatar

        if (oldAvatar is Avatar.Image && oldAvatar != newAvatar) {
            context.deleteChatFiles(listOf(oldAvatar.url.toUri()))
        }
    }

    // 设置聊天模型
    fun setChatModel(assistant: Assistant, model: Model) {
        viewModelScope.launch {
            settingsStore.update { settings ->
                settings.copy(
                    assistants = settings.assistants.map {
                        if (it.id == assistant.id) {
                            it.copy(
                                chatModelId = model.id
                            )
                        } else {
                            it
                        }
                    })
            }
        }
    }

    // Update checker
    val updateState =
        updateChecker.checkUpdate().stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    // Search Tool
    private val searchTool = Tool(
        name = "search_web",
        description = "search web for information",
        parameters = InputSchema.Obj(
            buildJsonObject {
                put("query", buildJsonObject {
                    put("type", "string")
                    put("description", "search keyword")
                })
            }, required = listOf("query")
        ),
        execute = {
            val query = it.jsonObject["query"]!!.jsonPrimitive.content
            val options = settings.value.searchServices.getOrElse(
                index = settings.value.searchServiceSelected,
                defaultValue = { SearchServiceOptions.DEFAULT })
            val service = SearchService.getService(options)
            val result = service.search(
                query = query,
                commonOptions = settings.value.searchCommonOptions,
                serviceOptions = options,
            )
            val results =
                JsonInstantPretty.encodeToJsonElement(result.getOrThrow()).jsonObject.let { json ->
                    val map = json.toMutableMap()
                    map["items"] = JsonArray(map["items"]!!.jsonArray.mapIndexed { index, item ->
                        JsonObject(item.jsonObject.toMutableMap().apply {
                            put("id", JsonPrimitive(Uuid.random().toString().take(6)))
                            put("index", JsonPrimitive(index + 1))
                        })
                    })
                    JsonObject(map)
                }
            results
        }, systemPrompt = { model ->
            if(model.tools.isNotEmpty()) return@Tool  ""
            """
            ## search_web 工具使用说明

            ### 搜索与筛选
            - 使用 `search_web` 工具(tool call)搜索互联网信息时，需要针对用户问题进行多角度、多关键词搜索
            - 优先选择权威、可信的信息源，避免使用过时或不准确的内容，现在是 {cur_date}
            - 基于**多个相关网页**综合回答，避免单一信息源
            - 每个关键信息点都要有对应的引用支撑

            ### 引用格式格式
            - 搜索结果中会包含index(搜索结果序号)和id(搜索结果唯一标识符)，引用格式为：
              `具体的引用内容 [citation](index:id)`
            - **引用必须紧跟在相关内容之后**，在标点符号后面，不得延后到回复结尾
            - 正确格式：`具体的引用内容 [citation](index:id)`, `多个引用内容。 [citation](index:id) [citation](index:id)`
            - 错误示例：把所有引用都放在回复最后

            ### 引用位置要求
            - **即时引用**：每当使用搜索结果中的信息时，立刻在该句话后添加引用
            - **分散引用**：引用应分布在整个回答中，而不是集中在某处
            - **精确对应**：引用标记必须紧跟其引用的具体内容

            ### 引用示例
            ```
            ✅ 正确：
            - 据报道，该技术可以提高效率30%。[citation](1:0b16b0)
            - 另一项研究显示，成本降低了15%，专家认为这将改变行业格局。[citation](2:06d59c) [citation](3:b18295)

            ❌ 错误：
            据报道，该技术可以提高效率30%。另一项研究显示，成本降低了15%。
            专家认为这将改变行业格局。
            [citation](1:b18295)

            ### 注意
            - 如果没有调用搜索工具，请勿添加引用
            ```
      """.trimIndent()
        }
    )

    fun handleMessageSend(content: List<UIMessagePart>) {
        if (content.isEmptyInputMessage()) return

        this.conversationJob.value?.cancel()
        val job = viewModelScope.launch {
            // 添加消息到列表
            val newConversation = conversation.value.copy(
                messageNodes = conversation.value.messageNodes + UIMessage(
                    role = MessageRole.USER,
                    parts = content,
                ).toMessageNode(),
            )
            saveConversation(newConversation)

            // 开始补全
            handleMessageComplete()

            generationDoneFlow.emit(Uuid.random())
        }
        this.conversationJob.value = job
        job.invokeOnCompletion {
            this.conversationJob.value = null
        }
    }

    fun handleMessageEdit(parts: List<UIMessagePart>, messageId: Uuid) {
        if (parts.isEmptyInputMessage()) return
        val newConversation = conversation.value.copy(
            messageNodes = conversation.value.messageNodes.map { node ->
                if (!node.messages.any { it.id == messageId }) {
                    return@map node // 如果这个node没有这个消息，则不修改
                }
                node.copy(
                    messages = node.messages + UIMessage(
                        role = node.role,
                        parts = parts,
                    ), selectIndex = node.messages.size
                )
            },
        )
        viewModelScope.launch {
            saveConversation(newConversation)
        }
    }

    fun handleMessageTruncate() {
        viewModelScope.launch {
            val lastTruncateIndex = conversation.value.messageNodes.lastIndex + 1
            // 如果截断在最后一个索引，则取消截断，否则更新 truncateIndex 到最后一个截断位置
            val newConversation = conversation.value.copy(
                truncateIndex = if (conversation.value.truncateIndex == lastTruncateIndex) -1 else lastTruncateIndex,
                title = "",
                chatSuggestions = emptyList(), // 清空建议
            )
            saveConversation(newConversation)
        }
    }

    private fun checkInvalidMessages() {
        var messagesNodes = conversation.value.messageNodes

        // 移除无效tool call
        messagesNodes = messagesNodes.mapIndexed { index, node ->
            val next = if (index < messagesNodes.size - 1) messagesNodes[index + 1] else null
            if (node.currentMessage.hasPart<UIMessagePart.ToolCall>()) {
                if (next?.currentMessage?.hasPart<UIMessagePart.ToolResult>() != true) {
                    return@mapIndexed node.copy(
                        messages = node.messages.filter { it.id != node.currentMessage.id },
                        selectIndex = node.selectIndex - 1
                    )
                }
            }
            node
        }

        // 更新index
        messagesNodes = messagesNodes.map { node ->
            if (node.messages.isNotEmpty() && node.selectIndex !in node.messages.indices) {
                node.copy(
                    selectIndex = 0
                )
            } else {
                node
            }
        }

        // 移除无效消息
        messagesNodes = messagesNodes.filter { it.messages.isNotEmpty() }

        _conversation.value = _conversation.value.copy(
            messageNodes = messagesNodes
        )
    }

    private suspend fun handleMessageComplete(messageRange: ClosedRange<Int>? = null) {
        val model = currentChatModel.value ?: return
        runCatching {
            // reset suggestions
            updateConversation(conversation.value.copy(chatSuggestions = emptyList()))

            // memory tool
            if (!model.abilities.contains(ModelAbility.TOOL)) {
                if (enableWebSearch.value || mcpManager.getAllAvailableTools()
                        .isNotEmpty() || settings.value.getCurrentAssistant().enableMemory
                ) {
                    errorFlow.emit(IllegalStateException(context.getString(R.string.tools_warning)))
                }
            }

            // check invalid messages
            checkInvalidMessages()

            // start generating
            generationHandler.generateText(
                settings = settings.value,
                model = model,
                messages = conversation.value.currentMessages.let {
                    if (messageRange != null) {
                        it.subList(messageRange.start, messageRange.endInclusive + 1)
                    } else {
                        it
                    }
                },
                assistant = settings.value.getCurrentAssistant(),
                memories = { memoryRepository.getMemoriesOfAssistant(settings.value.assistantId.toString()) },
                inputTransformers = buildList {
                    addAll(inputTransformers)
                    add(templateTransformer)
                },
                outputTransformers = outputTransformers,
                tools = buildList {
                    if (enableWebSearch.value) {
                        add(searchTool)
                    }
                    addAll(localTools.getTools(settings.value.getCurrentAssistant().localTools))
                    mcpManager.getAllAvailableTools().forEach { tool ->
                        add(
                            Tool(
                                name = tool.name,
                                description = tool.description ?: "",
                                parameters = tool.inputSchema,
                                execute = {
                                    mcpManager.callTool(tool.name, it.jsonObject)
                                })
                        )
                    }
                },
                truncateIndex = conversation.value.truncateIndex,
            ).onCompletion {
                // 可能被取消了，或者意外结束，兜底更新
                updateConversation(
                    conversation = conversation.value.copy(
                        messageNodes = conversation.value.messageNodes.map { node ->
                            node.copy(messages = node.messages.map { it.finishReasoning() } // 结束思考
                            )
                        },
                        updateAt = Instant.now()
                    )
                )
            }.collect { chunk ->
                when (chunk) {
                    is GenerationChunk.Messages -> {
                        updateConversation(conversation.value.updateCurrentMessages(chunk.messages))
                    }
                }
            }
        }.onFailure {
            it.printStackTrace()
            errorFlow.emit(it)
        }.onSuccess {
            saveConversation(conversation.value)
            generateTitle(conversation.value)
            generateSuggestion(conversation.value)
        }
    }

    fun generateTitle(conversation: Conversation, force: Boolean = false) {
        val shouldGenerate = when {
            force -> true
            conversation.title.isBlank() -> true
            else -> false
        }
        if (!shouldGenerate) return

        val model = settings.value.findModelById(settings.value.titleModelId) ?: let {
            // 如果没有标题模型，则使用聊天模型
            settings.value.getCurrentChatModel()
        } ?: return
        val provider = model.findProvider(settings.value.providers) ?: return

        viewModelScope.launch {
            runCatching {
                val providerHandler = providerManager.getProviderByType(provider)
                val result = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.user(
                            prompt = settings.value.titlePrompt.applyPlaceholders(
                                "locale" to Locale.getDefault().displayName,
                                "content" to conversation.currentMessages.truncate(conversation.truncateIndex)
                                    .joinToString("\n\n") { it.summaryAsText() })
                        ),
                    ),
                    params = TextGenerationParams(
                        model = model, temperature = 0.3f, thinkingBudget = 0
                    ),
                )
                Log.i(TAG, "generateTitle: ${result.choices[0].message?.toText()}")
                // 生成完，conversation可能不是最新了，因此需要重新获取
                conversationRepo.getConversationById(conversation.id)?.let {
                    saveConversation(
                        conversation = it.copy(
                            title = result.choices[0].message?.toText()?.trim() ?: "",
                        )
                    )
                }
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    fun generateSuggestion(conversation: Conversation) {
        val model = settings.value.findModelById(settings.value.suggestionModelId) ?: return
        val provider = model.findProvider(settings.value.providers) ?: return
        viewModelScope.launch {
            runCatching {
                updateConversation(_conversation.value.copy(chatSuggestions = emptyList()))
                val providerHandler = providerManager.getProviderByType(provider)
                val result = providerHandler.generateText(
                    providerSetting = provider,
                    messages = listOf(
                        UIMessage.user(
                            settings.value.suggestionPrompt.applyPlaceholders(
                                "locale" to Locale.getDefault().displayName,
                                "content" to conversation.currentMessages.truncate(conversation.truncateIndex)
                                    .takeLast(8).joinToString("\n\n") { it.summaryAsText() }),
                        )
                    ),
                    params = TextGenerationParams(
                        model = model,
                        temperature = 1.0f,
                        thinkingBudget = 0,
                    ),
                )
                val suggestions = result.choices[0].message?.toText()?.split("\n")?.map { it.trim() }
                    ?.filter { it.isNotBlank() } ?: emptyList()
                Log.i(TAG, "generateSuggestion: ${result.choices[0]}")
                saveConversation(
                    _conversation.value.copy(
                        chatSuggestions = suggestions.take(10),
                    )
                )
            }.onFailure {
                it.printStackTrace()
            }
        }
    }

    suspend fun forkMessage(
        message: UIMessage
    ): Conversation {
        val node = conversation.value.getMessageNodeByMessage(message)
        val nodes = conversation.value.messageNodes.subList(
            0, conversation.value.messageNodes.indexOf(node) + 1
        )
        val newConversation = Conversation(
            id = Uuid.random(), assistantId = settings.value.assistantId, messageNodes = nodes
        )
        saveConversation(newConversation)
        return newConversation
    }

    fun deleteMessage(
        message: UIMessage
    ) {
        val conversation = conversation.value
        val node = conversation.getMessageNodeByMessage(message) ?: return // 找到这个消息所在的node
        val nodeIndex = conversation.messageNodes.indexOf(node)
        if (nodeIndex == -1) return
        val newConversation = if (node.messages.size == 1) {
            // 删除这个Node，因为这个node只有一个消息，那么这个node就是这个消息
            conversation.copy(
                messageNodes = conversation.messageNodes.filterIndexed { index, node -> index != nodeIndex })
        } else {
            // 更新node，删除这个消息
            val updatedNodes = conversation.messageNodes.mapNotNull { node ->
                val newMessages = node.messages.filter { it.id != message.id }
                if (newMessages.isEmpty()) {
                    // 如果删除消息后该node为空，则移除该node
                    null
                } else {
                    // 确保selectIndex在有效范围内
                    val newSelectIndex = if (node.selectIndex >= newMessages.size) {
                        newMessages.lastIndex
                    } else {
                        node.selectIndex
                    }
                    node.copy(
                        messages = newMessages,
                        selectIndex = newSelectIndex
                    )
                }
            }
            conversation.copy(messageNodes = updatedNodes)
        }
        updateConversation(newConversation)
        viewModelScope.launch {
            saveConversation(newConversation)
        }
    }

    fun regenerateAtMessage(
        message: UIMessage, regenerateAssistantMsg: Boolean = true
    ) {
        viewModelScope.launch {
            if (message.role == MessageRole.USER) {
                // 如果是用户消息，则截止到当前消息
                val node = conversation.value.getMessageNodeByMessage(message)
                val indexAt = conversation.value.messageNodes.indexOf(node)
                val newConversation = conversation.value.copy(
                    messageNodes = conversation.value.messageNodes.subList(0, indexAt + 1)
                )
                saveConversation(newConversation)
                conversationJob.value?.cancel()
                val job = viewModelScope.launch {
                    handleMessageComplete()
                    generationDoneFlow.emit(Uuid.random())
                }
                conversationJob.value = job
                job.invokeOnCompletion {
                    conversationJob.value = null
                }
            } else {
                if (!regenerateAssistantMsg) {
                    // 如果不需要重新生成助手消息，则直接返回
                    saveConversation(conversation.value)
                    return@launch
                }
                val node = conversation.value.getMessageNodeByMessage(message)
                val nodeIndex = conversation.value.messageNodes.indexOf(node)
                conversationJob.value?.cancel()
                val job = viewModelScope.launch {
                    handleMessageComplete(
                        messageRange = 0..<nodeIndex,
                    )
                    generationDoneFlow.emit(Uuid.random())
                }
                conversationJob.value = job
                job.invokeOnCompletion {
                    conversationJob.value = null
                }
            }
        }
    }

    fun updateConversation(conversation: Conversation) {
        if (conversation.id != this._conversationId) return
        checkFilesDelete(conversation, this._conversation.value)
        this._conversation.value = conversation
    }

    // 变更消息，检查文件删除
    private fun checkFilesDelete(newConversation: Conversation, oldConversation: Conversation) {
        val newFiles = newConversation.files
        val oldFiles = oldConversation.files
        val deletedFiles = oldFiles.filter { file ->
            newFiles.none { it == file }
        }
        if (deletedFiles.isNotEmpty()) {
            context.deleteChatFiles(deletedFiles)
            Log.w(TAG, "checkFilesDelete: $deletedFiles")
        }
    }

    suspend fun saveConversation(conversation: Conversation) {
        val conversation = conversation.copy(assistantId = settings.value.assistantId)
        this.updateConversation(conversation)
        try {
            if (conversationRepo.getConversationById(conversation.id) == null) {
                conversationRepo.insertConversation(conversation)
            } else {
                conversationRepo.updateConversation(conversation)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun saveConversationAsync() {
        viewModelScope.launch {
            saveConversation(conversation.value)
        }
    }

    fun updateTitle(title: String) {
        viewModelScope.launch {
            saveConversation(conversation.value.copy(title = title))
        }
    }

    fun deleteConversation(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.deleteConversation(conversation)
        }
    }

    fun updatePinnedStatus(conversation: Conversation) {
        viewModelScope.launch {
            conversationRepo.togglePinStatus(conversation.id)
        }
    }

    fun translateMessage(message: UIMessage, targetLanguage: Locale) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val settings = settingsStore.settingsFlow.first()

                val messageText = message.parts.filterIsInstance<UIMessagePart.Text>()
                    .joinToString("\n\n") { it.text }
                    .trim()

                if (messageText.isBlank()) return@launch

                // Set loading state for translation
                val loadingText = context.getString(R.string.translating)
                updateTranslationField(message.id, loadingText)

                generationHandler.translateText(
                    settings = settings,
                    sourceText = messageText,
                    targetLanguage = targetLanguage
                ) { translatedText ->
                    // Update translation field in real-time
                    updateTranslationField(message.id, translatedText)
                }.collect { /* Final translation already handled in onStreamUpdate */ }

                // Save the conversation after translation is complete
                saveConversationAsync()
            } catch (e: Exception) {
                // Clear translation field on error
                clearTranslationField(message.id)
                errorFlow.emit(e)
            }
        }
    }

    private fun updateTranslationField(messageId: Uuid, translationText: String) {
        val currentConversation = conversation.value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = translationText)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(currentConversation.copy(messageNodes = updatedNodes))
    }

    fun clearTranslationField(messageId: Uuid) {
        val currentConversation = conversation.value
        val updatedNodes = currentConversation.messageNodes.map { node ->
            if (node.messages.any { it.id == messageId }) {
                val updatedMessages = node.messages.map { msg ->
                    if (msg.id == messageId) {
                        msg.copy(translation = null)
                    } else {
                        msg
                    }
                }
                node.copy(messages = updatedMessages)
            } else {
                node
            }
        }

        updateConversation(currentConversation.copy(messageNodes = updatedNodes))
    }
}
