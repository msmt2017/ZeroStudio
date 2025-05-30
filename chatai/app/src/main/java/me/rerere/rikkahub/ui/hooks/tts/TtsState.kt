package me.rerere.rikkahub.ui.hooks.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import java.util.UUID

/**
 * Composable function to remember and manage TTS state.
 *
 * @param defaultLanguage The default language to try and set on initialization.
 * @param defaultSpeechRate The default speech rate (1.0 is normal).
 * @param defaultPitch The default pitch (1.0 is normal).
 * @param onInitError Callback invoked if TTS initialization fails.
 * @return An instance of [TtsState] to control TTS.
 */
@Composable
fun rememberTtsState(
    defaultLanguage: Locale = Locale.getDefault(),
    defaultSpeechRate: Float = 1.0f,
    defaultPitch: Float = 1.0f,
    onInitError: ((String?) -> Unit)? = null
): TtsState {
    val context = LocalContext.current

    // Remember the TtsState instance across recompositions
    val ttsState = remember {
        TtsStateImpl(
            context = context.applicationContext, // Use application context
            defaultLanguage = defaultLanguage,
            defaultSpeechRate = defaultSpeechRate,
            defaultPitch = defaultPitch,
            onInitErrorCallback = onInitError
        )
    }

    // Use DisposableEffect to ensure TTS is shut down when the composable leaves composition
    DisposableEffect(ttsState) {
        onDispose {
            Log.d("rememberTtsState", "Disposing TTS State")
            ttsState.shutdown()
        }
    }

    return ttsState
}

/**
 * Interface defining the public API of our TTS state holder.
 * Focuses on language-based TTS control.
 */
interface TtsState {
    /** Flow indicating if the TTS engine is initialized and ready. */
    val isInitialized: StateFlow<Boolean>

    /** Flow indicating if the TTS engine is currently speaking. */
    val isSpeaking: StateFlow<Boolean>

    /** Flow providing the set of available languages supported by the engine. */
    val availableLanguages: StateFlow<Set<Locale>>

    /** Flow indicating the currently set language for synthesis. Null if none is set or initialization failed. */
    val currentLanguage: StateFlow<Locale?>

    /** Flow holding any initialization error message. Null if initialization was successful or not yet complete. */
    val initError: StateFlow<String?>

    /**
     * Speaks the given text using the current TTS settings.
     *
     * @param text The text to speak.
     * @param queueMode Determines how this speech request is handled relative to others in the queue.
     *                  Use [TextToSpeech.QUEUE_FLUSH] (default) to interrupt current speech and speak immediately,
     *                  or [TextToSpeech.QUEUE_ADD] to add it to the end of the queue.
     * @param params Optional bundle for synthesis parameters (e.g., volume, pan).
     * @param utteranceId A unique identifier for this speech request, used for progress tracking. Defaults to a random UUID.
     */
    fun speak(
        text: String,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH,
        params: Bundle? = null,
        utteranceId: String = UUID.randomUUID().toString()
    )

    /**
     * Attempts to set the TTS language.
     * The engine will use its default voice for the specified locale.
     *
     * @param locale The desired language locale.
     * @return `true` if the language was set successfully, `false` otherwise (e.g., language not supported, TTS not initialized).
     */
    fun setLanguage(locale: Locale): Boolean

    /** Stops the current speech utterance immediately. */
    fun stop()

    /** Releases the resources used by the TTS engine. Should be called when TTS is no longer needed. */
    fun shutdown()
}

/**
 * Internal implementation of TtsState.
 */
private class TtsStateImpl(
    private val context: Context,
    private val defaultLanguage: Locale = Locale.getDefault(),
    private val defaultSpeechRate: Float = 1.0f,
    private val defaultPitch: Float = 1.0f,
    private val onInitErrorCallback: ((String?) -> Unit)? = null
) : TtsState, TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // --- Language State ---
    private val _availableLanguages = MutableStateFlow<Set<Locale>>(emptySet())
    override val availableLanguages: StateFlow<Set<Locale>> = _availableLanguages.asStateFlow()
    private val _currentLanguage = MutableStateFlow<Locale?>(null)
    override val currentLanguage: StateFlow<Locale?> = _currentLanguage.asStateFlow()

    // --- Error State ---
    private val _initError = MutableStateFlow<String?>(null)
    override val initError: StateFlow<String?> = _initError.asStateFlow()

    // Listener to track speech start and end
    private val utteranceProgressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            _isSpeaking.update { true }
            Log.d("TtsStateImpl", "Speech started (ID: $utteranceId)")
        }

        override fun onDone(utteranceId: String?) {
            _isSpeaking.update { false }
            Log.d("TtsStateImpl", "Speech done (ID: $utteranceId)")
        }

        @Deprecated("Deprecated in API level 21")
        override fun onError(utteranceId: String?) {
            _isSpeaking.update { false }
            Log.e("TtsStateImpl", "Speech error (ID: $utteranceId)")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            _isSpeaking.update { false }
            Log.e("TtsStateImpl", "Speech error (ID: $utteranceId, Code: $errorCode)")
        }

        override fun onStop(utteranceId: String?, interrupted: Boolean) {
            _isSpeaking.update { false }
            Log.d("TtsStateImpl", "Speech stopped (ID: $utteranceId, Interrupted: $interrupted)")
        }
    }

    init {
        Log.d("TtsStateImpl", "Initializing TTS...")
        // Start TTS initialization
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(utteranceProgressListener)
    }

    // --- TextToSpeech.OnInitListener ---
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            Log.i("TtsStateImpl", "TTS Initialization Successful")
            _isInitialized.update { true }
            _initError.update { null }

            // Set default pitch and rate first
            tts?.setSpeechRate(defaultSpeechRate)
            tts?.setPitch(defaultPitch)

            // Query available languages
            try {
                // Get Languages
                _availableLanguages.update {
                    tts?.availableLanguages?.filterNotNull()?.toSet() ?: emptySet()
                }
                Log.d(
                    "TtsStateImpl",
                    "Available Languages: ${availableLanguages.value.joinToString { it.toLanguageTag() }}"
                )

                // --- Set Initial Language ---
                val langResult = tts?.setLanguage(defaultLanguage)
                if (langResult == TextToSpeech.LANG_MISSING_DATA || langResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(
                        "TtsStateImpl",
                        "Default language ($defaultLanguage) not supported or missing data. Trying device default."
                    )
                    val deviceDefaultLocale = Locale.getDefault()
                    val deviceLangResult = tts?.setLanguage(deviceDefaultLocale)
                    if (deviceLangResult == TextToSpeech.LANG_MISSING_DATA || deviceLangResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e(
                            "TtsStateImpl",
                            "Device default language ($deviceDefaultLocale) also not available."
                        )
                        // No language could be set
                        _currentLanguage.update { null }
                    } else {
                        // Device default language set successfully
                        _currentLanguage.update { tts?.language ?: deviceDefaultLocale }
                    }
                } else {
                    // Default language set successfully
                    _currentLanguage.update { tts?.language ?: defaultLanguage }
                }

                // Log final initial state
                Log.d(
                    "TtsStateImpl",
                    "Initial Language set to: ${currentLanguage.value?.toLanguageTag() ?: "None"}"
                )

            } catch (e: Exception) {
                // Catch potential errors during language fetching/setting
                Log.e("TtsStateImpl", "Error during TTS post-initialization setup", e)
                _initError.update { "Error during post-init setup: ${e.message}" }
                onInitErrorCallback?.invoke(initError.value)
                // Ensure states reflect failure
                _currentLanguage.update { null }
                _availableLanguages.update { emptySet() }
            }

        } else {
            val errorMsg = "TTS Initialization Failed! Status: $status"
            Log.e("TtsStateImpl", errorMsg)
            _isInitialized.update { false }
            _initError.update { errorMsg }
            onInitErrorCallback?.invoke(errorMsg)
        }
    }

    // --- TtsState Control Methods ---
    override fun speak(text: String, queueMode: Int, params: Bundle?, utteranceId: String) {
        if (!isInitialized.value || tts == null) {
            Log.e("TtsStateImpl", "TTS not initialized, cannot speak.")
            return
        }
        // Check if a language is set before speaking
        if (currentLanguage.value == null) {
            Log.e("TtsStateImpl", "TTS initialized but no language set, cannot speak.")
            // Maybe trigger an error state or callback?
            return
        }

        // Ensure utterance ID is passed for progress listener
        val finalParams = params ?: Bundle()
        // The TTS instance holds the current language state.

        val result = tts?.speak(text, queueMode, finalParams, utteranceId)
        if (result == TextToSpeech.ERROR) {
            Log.e("TtsStateImpl", "Error starting speech (ID: $utteranceId)")
            // Potentially update an error state here if needed
        } else {
            Log.d("TtsStateImpl", "Speech queued (ID: $utteranceId)")
        }
    }

    override fun setLanguage(locale: Locale): Boolean {
        if (!isInitialized.value || tts == null) {
            Log.e("TtsStateImpl", "TTS not initialized, cannot set language.")
            return false
        }

        try {
            val result = tts?.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("TtsStateImpl", "Language $locale not supported or missing data.")
                // Keep current state consistent - don't change language if setting fails
                return false
            } else {
                Log.i(
                    "TtsStateImpl",
                    "Language successfully set to $locale. Engine will use default voice for this language."
                )
                // Update state based on actual result after setting language
                val actualLanguage = tts?.language ?: locale
                _currentLanguage.update { actualLanguage }
                Log.d(
                    "TtsStateImpl",
                    "Current language updated to: ${actualLanguage.toLanguageTag()}"
                )
                return true
            }
        } catch (e: Exception) {
            Log.e("TtsStateImpl", "Error setting language to $locale", e)
            return false
        }
    }

    // Removed setVoice method

    override fun stop() {
        if (isInitialized.value && tts != null) {
            val result = tts?.stop()
            if (result == TextToSpeech.SUCCESS) {
                Log.d("TtsStateImpl", "TTS stop requested successfully.")
                // isSpeaking state will be updated by the UtteranceProgressListener's onDone/onError
            } else {
                Log.e(
                    "TtsStateImpl",
                    "Error stopping TTS (Result: $result). Forcing isSpeaking state to false."
                )
                // Force state update if stop call fails, as listener might not be called.
                _isSpeaking.update { false }
            }
        } else {
            Log.w("TtsStateImpl", "Stop called but TTS not initialized or already shut down.")
            // Ensure speaking state is false if stop is called when not initialized
            if (!_isInitialized.value) {
                _isSpeaking.update { false }
            }
        }
    }

    override fun shutdown() {
        Log.d("TtsStateImpl", "Shutdown requested.")
        // Stop speech before shutting down
        if (_isSpeaking.value) {
            try {
                tts?.stop() // Attempt to stop ongoing speech
                _isSpeaking.update { false } // Assume stop works or is irrelevant now
            } catch (e: Exception) {
                Log.e("TtsStateImpl", "Error stopping TTS during shutdown", e)
            }
        }
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("TtsStateImpl", "Error shutting down TTS", e)
        }
        tts = null // Release the reference
        // Reset states
        _isInitialized.update { false }
        _isSpeaking.update { false } // Ensure speaking is false after shutdown
        _currentLanguage.update { null }
        _availableLanguages.update { emptySet() }
        _initError.update { null } // Clear error on shutdown
        Log.d("TtsStateImpl", "Shutdown complete.")
    }
}

