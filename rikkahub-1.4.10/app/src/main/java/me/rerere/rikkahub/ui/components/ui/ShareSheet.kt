package me.rerere.rikkahub.ui.components.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Share2
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.utils.JsonInstant
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Composable
fun ShareSheet(
    state: ShareSheetState,
) {
    val context = LocalContext.current
    if (state.isShow) {
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("共享你的LLM模型", style = MaterialTheme.typography.titleLarge)

                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                            intent.type = "text/plain"
                            intent.putExtra(
                                Intent.EXTRA_TEXT,
                                state.currentProvider?.encode() ?: ""
                            )
                            try {
                                context.startActivity(Intent.createChooser(intent, null))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    ) {
                        Icon(Lucide.Share2, null)
                    }
                }

                QRCode(
                    value = state.currentProvider?.encode() ?: "",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
private fun ProviderSetting.encode(): String {
    return buildString {
        append("ai-provider:")
        append("v1:")

        val value = JsonInstant.encodeToString(buildJsonObject {
            // "type": "openai-compat", "google", "anthropic"
            put(
                "type", JsonPrimitive(
                    when (this@encode) {
                        is ProviderSetting.OpenAI -> "openai-compat"
                        is ProviderSetting.Google -> "google"
                        is ProviderSetting.Claude -> "claude"
                    }
                )
            )

            // display name
            put("name", JsonPrimitive(name))

            // provider settings
            when (this@encode) {
                is ProviderSetting.OpenAI -> {
                    put("apiKey", JsonPrimitive(apiKey))
                    put("baseUrl", JsonPrimitive(baseUrl))
                }

                is ProviderSetting.Google -> {
                    put("apiKey", JsonPrimitive(apiKey))
                }

                is ProviderSetting.Claude -> {
                    put("apiKey", JsonPrimitive(apiKey))
                    put("baseUrl", JsonPrimitive(baseUrl))
                }
            }
        })
        append(Base64.encode(value.encodeToByteArray()))
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeProviderSetting(value: String): ProviderSetting {
    require(value.startsWith("ai-provider:v1:")) { "Invalid provider setting string" }

    // 去掉前缀
    val base64Str = value.removePrefix("ai-provider:v1:")

    // Base64解码
    val jsonBytes = Base64.decode(base64Str)
    val jsonStr = jsonBytes.decodeToString()
    val jsonObj = JsonInstant.parseToJsonElement(jsonStr).jsonObject

    val type = jsonObj["type"]?.jsonPrimitive?.content ?: error("Missing type")
    val name = jsonObj["name"]?.jsonPrimitive?.content ?: error("Missing name")

    return when (type) {
        "openai-compat" -> ProviderSetting.OpenAI(
            name = name,
            apiKey = jsonObj["apiKey"]?.jsonPrimitive?.content ?: error("Missing apiKey"),
            baseUrl = jsonObj["baseUrl"]?.jsonPrimitive?.content ?: error("Missing baseUrl"),
            models = emptyList()
        )

        "google" -> ProviderSetting.Google(
            name = name,
            apiKey = jsonObj["apiKey"]?.jsonPrimitive?.content ?: error("Missing apiKey"),
            models = emptyList()
        )

        else -> error("Unknown provider type: $type")
    }
}

class ShareSheetState {
    private var show by mutableStateOf(false)
    val isShow get() = show

    private var provider by mutableStateOf<ProviderSetting?>(null)
    val currentProvider get() = provider

    fun show(provider: ProviderSetting) {
        this.show = true
        this.provider = provider
    }

    fun dismiss() {
        this.show = false
    }
}

@Composable
fun rememberShareSheetState(): ShareSheetState {
    return ShareSheetState()
}
