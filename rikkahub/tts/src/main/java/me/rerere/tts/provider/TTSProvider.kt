package me.rerere.tts.provider

import android.content.Context
import me.rerere.tts.model.TTSRequest
import me.rerere.tts.model.TTSResponse

interface TTSProvider<T : TTSProviderSetting> {
    suspend fun generateSpeech(
        context: Context,
        providerSetting: T,
        request: TTSRequest
    ): TTSResponse
}
