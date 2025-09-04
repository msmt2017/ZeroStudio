package me.rerere.tts.provider.providers

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse
import me.rerere.tts.provider.TTSProvider
import me.rerere.tts.provider.TTSProviderSetting
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "SystemTTSProvider"

class SystemTTSProvider : TTSProvider<TTSProviderSetting.SystemTTS> {
    override suspend fun generateSpeech(
        context: Context,
        providerSetting: TTSProviderSetting.SystemTTS,
        request: TTSRequest
    ): TTSResponse = suspendCancellableCoroutine { continuation ->
        var tts: TextToSpeech? = null
        val listener = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val ttsInstance = tts!!

                // Set language
                val locale = Locale.getDefault()
                val langResult = ttsInstance.setLanguage(locale)

                if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                    langResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.w(TAG, "generateSpeech: Language $locale not supported")
                }

                // Set speech parameters
                ttsInstance.setSpeechRate(providerSetting.speechRate)
                ttsInstance.setPitch(providerSetting.pitch)

                // Create temporary file for audio output using temp directory like RikkaHubApp
                val tempDir = context.filesDir.resolve("temp")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                val audioFile = File(tempDir, "tts_${System.currentTimeMillis()}.wav")

                val utteranceId = UUID.randomUUID().toString()

                ttsInstance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.i(TAG, "onStart: TTS engine started!")
                    }

                    override fun onDone(utteranceId: String?) {
                        try {
                            if (audioFile.exists()) {
                                val audioData = audioFile.readBytes()
                                audioFile.delete()

                                val response = TTSResponse(
                                    audioData = audioData,
                                    format = me.rerere.tts.model.AudioFormat.WAV,
                                    metadata = mapOf(
                                        "provider" to "system",
                                        "speechRate" to providerSetting.speechRate.toString(),
                                        "pitch" to providerSetting.pitch.toString()
                                    )
                                )
                                if (continuation.isActive) continuation.resume(response)
                            } else {
                                if (continuation.isActive) continuation.resumeWithException(
                                    Exception("Failed to generate audio file")
                                )
                            }
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        } finally {
                            ttsInstance.shutdown()
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "onError: TTS synthesis failed!")
                        audioFile.delete()
                        if (continuation.isActive) continuation.resumeWithException(
                            Exception("TTS synthesis failed")
                        )
                        ttsInstance.shutdown()
                    }
                })

                val result = ttsInstance.synthesizeToFile(
                    request.text,
                    null,
                    audioFile,
                    utteranceId
                )

                if (result != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) continuation.resumeWithException(
                        Exception("Failed to start TTS synthesis")
                    )
                    ttsInstance.shutdown()
                }

            } else {
                if (continuation.isActive) continuation.resumeWithException(
                    Exception("Failed to initialize TextToSpeech engine")
                )
            }
        }
        tts = TextToSpeech(context, listener)

        continuation.invokeOnCancellation {
            tts?.shutdown()
        }
    }
}
