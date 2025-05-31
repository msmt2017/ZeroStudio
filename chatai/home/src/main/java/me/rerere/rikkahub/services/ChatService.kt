package me.rerere.rikkahub.services

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.transformers.MessageTimeTransformer
import me.rerere.ai.ui.transformers.PlaceholderTransformer
import me.rerere.ai.ui.transformers.ThinkTagTransformer
import me.rerere.rikkahub.CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.ai.Base64ImageToLocalFileTransformer
import me.rerere.rikkahub.data.ai.GenerationChunk
import me.rerere.rikkahub.data.ai.GenerationHandler
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.MemoryRepository
import me.rerere.rikkahub.utils.JsonInstant
import org.koin.android.ext.android.inject

private const val TAG = "ChatService"
private const val NOTIFICATION_ID = 1001

private val inputTransformers by lazy {
    listOf(
        PlaceholderTransformer,
    )
}

private val outputTransformers by lazy {
    listOf(
        ThinkTagTransformer,
        Base64ImageToLocalFileTransformer
    )
}

class ChatService : Service() {
    private val handler: GenerationHandler by inject()
    private val conversationRepo: ConversationRepository by inject()
    private val memoryRepository: MemoryRepository by inject()
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null
    
    private val _currentConversation = MutableStateFlow<Conversation?>(null)
    val currentConversation: StateFlow<Conversation?> = _currentConversation.asStateFlow()
    
    private val binder = ChatServiceBinder()
    
    inner class ChatServiceBinder : Binder() {
        fun getService(): ChatService = this@ChatService
    }
    
    override fun onBind(intent: Intent): IBinder {
        return binder
    }
    
    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                ACTION_START_GENERATION -> {
                    val settingsJson = it.getStringExtra(EXTRA_SETTINGS) ?: return@let
                    val modelJson = it.getStringExtra(EXTRA_MODEL) ?: return@let
                    val assistantJson = it.getStringExtra(EXTRA_ASSISTANT)
                    val conversationJson = it.getStringExtra(EXTRA_CONVERSATION) ?: return@let

                    Log.i(TAG, "onStartCommand: startGeneration")
                    startGeneration(
                        settings = JsonInstant.decodeFromString(settingsJson),
                        model = JsonInstant.decodeFromString(modelJson),
                        assistant = assistantJson?.let { JsonInstant.decodeFromString<Assistant?>(it) },
                        conversation = JsonInstant.decodeFromString(conversationJson)
                    )
                }
                ACTION_STOP_GENERATION -> {
                    stopGeneration()
                }
            }
        }
        return START_NOT_STICKY
    }
    
    private fun startGeneration(
        settings: Settings,
        model: Model,
        assistant: Assistant?,
        conversation: Conversation
    ) {
        stopGeneration()
        
        _currentConversation.value = conversation
        
        currentJob = serviceScope.launch {
            runCatching {
                Log.i(TAG, "startGeneration: start stream text")
                handler.generateText(
                    settings = settings,
                    model = model,
                    messages = conversation.messages,
                    assistant = assistant,
                    memories = {
                        if (assistant != null) memoryRepository.getMemoriesOfAssistant(assistant.id.toString()) else emptyList()
                    },
                    inputTransformers = buildList {
                        addAll(inputTransformers)
                        if (assistant?.enableMessageTime == true) add(MessageTimeTransformer)
                    },
                    outputTransformers = outputTransformers,
                    maxSteps = 5,
                ).collect { chunk ->
                    val currentConv = _currentConversation.value ?: return@collect
                    _currentConversation.value = when (chunk) {
                        is GenerationChunk.Messages -> {
                            currentConv.copy(
                                messages = chunk.messages
                            )
                        }
                        is GenerationChunk.TokenUsage -> {
                            currentConv.copy(
                                tokenUsage = chunk.usage
                            )
                        }
                    }
                    Log.i(TAG, "startGeneration: $chunk")
                }
            }.onFailure {
                it.printStackTrace()
            }.onSuccess {
                Log.i(TAG, "startGeneration: generate success")
                _currentConversation.value?.let { conversationRepo.upsertConversation(it) }
            }
        }
    }
    
    private fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHAT_COMPLETED_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Generating")
            .setSmallIcon(R.drawable.small_icon)
            .setContentText("Generating...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
    
    companion object {
        private const val ACTION_START_GENERATION = "me.rerere.rikkahub.action.START_GENERATION"
        private const val ACTION_STOP_GENERATION = "me.rerere.rikkahub.action.STOP_GENERATION"
        
        private const val EXTRA_SETTINGS = "me.rerere.rikkahub.extra.SETTINGS"
        private const val EXTRA_MODEL = "me.rerere.rikkahub.extra.MODEL"
        private const val EXTRA_ASSISTANT = "me.rerere.rikkahub.extra.ASSISTANT"
        private const val EXTRA_CONVERSATION = "me.rerere.rikkahub.extra.CONVERSATION"
        
        fun startGeneration(
            context: Context,
            settings: Settings,
            model: Model,
            assistant: Assistant?,
            conversation: Conversation
        ) {
            val intent = Intent(context, ChatService::class.java).apply {
                action = ACTION_START_GENERATION
                putExtra(EXTRA_SETTINGS, JsonInstant.encodeToString(settings))
                putExtra(EXTRA_MODEL, JsonInstant.encodeToString(model))
                putExtra(EXTRA_ASSISTANT, assistant?.let { JsonInstant.encodeToString(it) })
                putExtra(EXTRA_CONVERSATION, JsonInstant.encodeToString(conversation))
            }
            context.startService(intent)
        }
        
        fun stopGeneration(context: Context) {
            val intent = Intent(context, ChatService::class.java).apply {
                action = ACTION_STOP_GENERATION
            }
            context.startService(intent)
        }
    }
}