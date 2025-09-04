package me.rerere.rikkahub.ui.hooks

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.getSelectedTTSProvider
import me.rerere.rikkahub.utils.stripMarkdown
import me.rerere.tts.model.AudioFormat
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSManager
import me.rerere.tts.provider.TTSProviderSetting
import org.koin.compose.koinInject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "TTS"

/**
 * Composable function to remember and manage custom TTS state.
 * Uses user-configured TTS providers instead of system TTS.
 */
@Composable
fun rememberCustomTtsState(): CustomTtsState {
    val context = LocalContext.current
    val settingsStore = koinInject<SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()

    // Remember the CustomTtsState instance across recompositions
    val ttsState = remember {
        CustomTtsStateImpl(
            context = context.applicationContext,
            settingsStore = settingsStore
        )
    }

    // Update the provider when settings change
    DisposableEffect(settings.selectedTTSProviderId, settings.ttsProviders) {
        ttsState.updateProvider(settings.getSelectedTTSProvider())
        onDispose { }
    }

    // Cleanup resources when the state is disposed
    DisposableEffect(ttsState) {
        onDispose {
            ttsState.cleanup()
        }
    }

    return ttsState
}

/**
 * Interface defining the public API of our custom TTS state holder.
 */
interface CustomTtsState {
    /** Flow indicating if the TTS provider is available and ready. */
    val isAvailable: StateFlow<Boolean>

    /** Flow indicating if the TTS is currently speaking. */
    val isSpeaking: StateFlow<Boolean>

    /** Flow holding any error message. */
    val error: StateFlow<String?>

    /** Flow indicating current chunk being processed (index) */
    val currentChunk: StateFlow<Int>

    /** Flow indicating total chunks in queue */
    val totalChunks: StateFlow<Int>

    /**
     * Speaks the given text using the selected TTS provider.
     * Long texts will be automatically chunked and queued.
     */
    fun speak(text: String, flushCalled: Boolean = true)

    /** Stops the current speech and clears the queue. */
    fun stop()

    /** Pauses the current playback. */
    fun pause()

    /** Resumes the paused playback. */
    fun resume()

    /** Skips to the next chunk in the queue. */
    fun skipNext()

    /** Cleanup resources. */
    fun cleanup()
}

/**
 * Internal implementation of CustomTtsState.
 */
private class CustomTtsStateImpl(
    private val context: Context,
    private val settingsStore: SettingsStore
) : CustomTtsState, KoinComponent {

    private val ttsManager by inject<TTSManager>()

    // 创建自己的 AudioPlayer 实例，避免与 TTSManager 的单例冲突
    private val audioPlayer = me.rerere.tts.player.AudioPlayer(context)
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentJob: Job? = null

    private var currentProvider: TTSProviderSetting? = null

    // Queue system for text chunks
    private val chunkQueue = ConcurrentLinkedQueue<String>()
    private var isProcessingQueue = false
    private var isPaused = false

    // Pre-synthesis system
    private val preSynthesisCache = ConcurrentHashMap<Int, TTSResponse>()
    private var synthesizerJob: Job? = null
    private var nextChunkToSynthesize = 0

    // Chunking configuration
    private val maxChunkLength = 40 // Maximum characters per chunk (only as reference)
    private val chunkDelayMs = 5L // Delay between chunks
    private val preSynthesisCount = 4 // Number of chunks to pre-synthesize ahead

    private val _isAvailable = MutableStateFlow(false)
    override val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    private val _currentChunk = MutableStateFlow(0)
    override val currentChunk: StateFlow<Int> = _currentChunk.asStateFlow()

    private val _totalChunks = MutableStateFlow(0)
    override val totalChunks: StateFlow<Int> = _totalChunks.asStateFlow()

    private fun showToast(message: String) {
        scope.launch(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    fun updateProvider(provider: TTSProviderSetting?) {
        currentProvider = provider
        _isAvailable.update { provider != null }
        _error.update { null }

        if (provider == null) {
            stop()
        }
    }

    private fun chunkText(text: String): List<String> {
        if (text.isBlank()) {
            return emptyList()
        }

        // 1. 按段落分割
        val paragraphs = text.split("\n\n")

        // 正则表达式会在标点符号后分割，并保留标点
        val punctuationRegex = "(?<=[。！？，、：;.!?:,\n])".toRegex()

        // 2. 对每个段落进行处理，然后将结果合并
        return paragraphs.flatMap { paragraph ->
            if (paragraph.isBlank()) {
                emptyList()
            } else {
                paragraph.stripMarkdown()
                    .split(punctuationRegex)
                    .asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .fold<String, MutableList<StringBuilder>>(mutableListOf()) { acc, chunk ->
                        if (acc.isEmpty() || acc.last().length + chunk.length > maxChunkLength) {
                            acc.add(StringBuilder(chunk))
                        } else {
                            acc.last().append(chunk)
                        }
                        acc
                    }
                    .map { it.toString() }
            }
        }
    }

    override fun speak(text: String, flushCalled: Boolean) {
        val provider = currentProvider
        if (provider == null) {
            val errorMsg = "No TTS provider selected"
            _error.update { errorMsg }
            showToast(errorMsg)
            return
        }

        // Chunk the text
        val chunks = chunkText(text)

        if (flushCalled) {
            // Flush mode: stop current playback and clear queue, then start fresh
            if (isProcessingQueue) {
                stop()
            }

            chunkQueue.clear()
            chunkQueue.addAll(chunks)

            // Clear pre-synthesis cache and reset state
            preSynthesisCache.clear()
            nextChunkToSynthesize = 0

            _totalChunks.update { chunks.size }
            _currentChunk.update { 0 }
            _error.update { null }

            Log.d("CustomTtsState", "Text flushed and chunked into ${chunks.size} parts")

            // Start background synthesis worker
            startSynthesizer(chunks)

            startQueueProcessing()
        } else {
            // Queue mode: add to existing queue
            chunkQueue.addAll(chunks)
            _totalChunks.update { _totalChunks.value + chunks.size }
            _error.update { null }

            Log.d("CustomTtsState", "Added ${chunks.size} chunks to queue. Total: ${_totalChunks.value}")

            // If not already processing, start queue processing
            if (!isProcessingQueue) {
                val allChunks = chunkQueue.toList()
                startSynthesizer(allChunks)
                startQueueProcessing()
            }
        }
    }

    private fun startSynthesizer(chunks: List<String>) {
        synthesizerJob?.cancel()
        synthesizerJob = scope.launch {
            val provider = currentProvider ?: return@launch

            try {
                for (i in 0 until minOf(preSynthesisCount, chunks.size)) {
                    if (!isActive) break

                    val chunk = chunks[i]
                    Log.d("CustomTtsState", "Pre-synthesizing chunk $i: $chunk")

                    try {
                        val request = TTSRequest(text = chunk)
                        val response = withContext(Dispatchers.IO) {
                            ttsManager.generateSpeech(provider, request)
                        }
                        preSynthesisCache[i] = response
                        nextChunkToSynthesize = i + 1
                        Log.d("CustomTtsState", "Pre-synthesis completed for chunk $i")
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Log.e("CustomTtsState", "Pre-synthesis error for chunk $i", e)
                        val errorMsg = "TTS synthesis error: ${e.message}"
                        _error.update { errorMsg }
                        showToast(errorMsg)
                        break
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CustomTtsState", "Synthesizer error", e)
                val errorMsg = "TTS synthesis error: ${e.message}"
                _error.update { errorMsg }
                showToast(errorMsg)
            }
        }
    }

    private fun triggerMoreSynthesis(allChunks: List<String>, currentChunkIndex: Int) {
        if (synthesizerJob?.isActive != true) {
            synthesizerJob = scope.launch {
                val provider = currentProvider ?: return@launch

                try {
                    // Synthesize upcoming chunks that are not yet cached
                    val endIndex = minOf(currentChunkIndex + preSynthesisCount, allChunks.size)
                    for (i in maxOf(currentChunkIndex, nextChunkToSynthesize) until endIndex) {
                        if (!isActive || preSynthesisCache.containsKey(i)) continue

                        val chunk = allChunks[i]
                        Log.d("CustomTtsState", "Pre-synthesizing chunk $i: $chunk")

                        try {
                            val request = TTSRequest(text = chunk)
                            val response = withContext(Dispatchers.IO) {
                                ttsManager.generateSpeech(provider, request)
                            }
                            preSynthesisCache[i] = response
                            nextChunkToSynthesize = i + 1
                            Log.d("CustomTtsState", "Pre-synthesis completed for chunk $i")
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.e("CustomTtsState", "Pre-synthesis error for chunk $i", e)
                            val errorMsg = "TTS synthesis error: ${e.message}"
                            _error.update { errorMsg }
                            showToast(errorMsg)
                            break
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("CustomTtsState", "Trigger synthesis error", e)
                    val errorMsg = "TTS synthesis error: ${e.message}"
                    _error.update { errorMsg }
                    showToast(errorMsg)
                }
            }
        }
    }

    private fun startQueueProcessing() {
        if (isProcessingQueue) return

        isProcessingQueue = true
        isPaused = false
        _isSpeaking.update { true }

        currentJob = scope.launch {
            try {
                processQueue()
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    Log.e("CustomTtsState", "Queue processing error", e)
                    val errorMsg = "Queue processing error: ${e.message}"
                    _error.update { errorMsg }
                    showToast(errorMsg)
                }
            } finally {
                isProcessingQueue = false
                _isSpeaking.update { false }
            }
        }
    }

    private suspend fun processQueue() {
        val provider = currentProvider ?: return
        var chunkIndex = 0
        val allChunks = chunkQueue.toList()

        while (chunkQueue.isNotEmpty() && !(currentJob?.isCancelled ?: false)) {
            if (isPaused) {
                delay(100)
                continue
            }

            val chunk = chunkQueue.poll() ?: break
            _currentChunk.update { chunkIndex + 1 }

            Log.d("CustomTtsState", "Processing chunk ${chunkIndex + 1}/${_totalChunks.value}: $chunk")

            // Try to get pre-synthesized audio, fallback to real-time synthesis
            val response = try {
                preSynthesisCache.remove(chunkIndex) ?: run {
                    Log.d("CustomTtsState", "No pre-synthesized audio for chunk $chunkIndex, synthesizing now...")
                    val request = TTSRequest(text = chunk)
                    withContext(Dispatchers.IO) {
                        ttsManager.generateSpeech(provider, request)
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CustomTtsState", "TTS synthesis error for chunk ${chunkIndex + 1}", e)
                val errorMsg = "TTS synthesis error for chunk ${chunkIndex + 1}: ${e.message}"
                _error.update { errorMsg }
                showToast(errorMsg)
                chunkIndex++
                continue
            }

            // Trigger pre-synthesis for upcoming chunks
            triggerMoreSynthesis(allChunks, chunkIndex + 1)

            // Play the audio using our own AudioPlayer instance
            try {
                Log.d("CustomTtsState", "Starting playback for chunk ${chunkIndex + 1}")
                when (response.format) {
                    AudioFormat.PCM -> {
                        val sampleRate = response.sampleRate ?: 24000
                        audioPlayer.playPcmSound(response.audioData, sampleRate)
                    }

                    else -> {
                        audioPlayer.playSound(response.audioData, response.format)
                    }
                }
                Log.d("CustomTtsState", "Playback completed for chunk ${chunkIndex + 1}")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.e("CustomTtsState", "Audio playback error for chunk ${chunkIndex + 1}", e)
                val errorMsg = "Audio playback error: ${e.message}"
                _error.update { errorMsg }
                showToast(errorMsg)
            }

            // Small delay between chunks
            if (chunkQueue.isNotEmpty() && !(currentJob?.isCancelled ?: false)) {
                delay(chunkDelayMs)
            }

            chunkIndex++
        }

        Log.d("CustomTtsState", "Queue processing completed")
    }

    override fun stop() {
        currentJob?.cancel()
        synthesizerJob?.cancel()
        // 停止当前音频播放
        audioPlayer.stop()
        chunkQueue.clear()
        preSynthesisCache.clear()
        isProcessingQueue = false
        isPaused = false
        nextChunkToSynthesize = 0
        _isSpeaking.update { false }
        _currentChunk.update { 0 }
        _totalChunks.update { 0 }
        Log.d("CustomTtsState", "TTS stopped, queue and cache cleared")
    }

    override fun pause() {
        isPaused = true
        Log.d("CustomTtsState", "TTS paused")
    }

    override fun resume() {
        isPaused = false
        Log.d("CustomTtsState", "TTS resumed")
    }

    override fun skipNext() {
        if (chunkQueue.isNotEmpty()) {
            chunkQueue.poll()
            Log.d("CustomTtsState", "Skipped to next chunk. Remaining: ${chunkQueue.size}")
        }
    }

    override fun cleanup() {
        stop()
        currentJob = null
        // 释放我们自己的 AudioPlayer 资源
        audioPlayer.dispose()
    }
}
