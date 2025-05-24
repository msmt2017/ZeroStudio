package android.zero.studio.chatai.data.dto

import android.zero.studio.chatai.data.ModelConstants.getDefaultAPIUrl
import android.zero.studio.chatai.data.model.ApiType

data class Platform(
    val name: ApiType,
    val selected: Boolean = false,
    val enabled: Boolean = false,
    val apiUrl: String = getDefaultAPIUrl(name),
    val token: String? = null,
    val model: String? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val systemPrompt: String? = null
)
