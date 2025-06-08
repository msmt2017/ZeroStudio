package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.css
import me.rerere.rikkahub.utils.toCssHex

@Composable
fun AIIcon(
    path: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = LocalContentColor.current
    val context = LocalContext.current
    val model = remember(path, contentColor, context) {
        ImageRequest.Builder(context)
            .data("file:///android_asset/icons/$path.svg")
            .css(
                """
                svg {
                  fill: ${contentColor.toCssHex()};
                }
            """.trimIndent()
            )
            .build()
    }
    AsyncImage(
        model = model,
        contentDescription = name,
        modifier = modifier
            .clip(CircleShape)
            .size(24.dp),
    )
}

@Composable
fun AutoAIIcon(
    name: String,
    modifier: Modifier = Modifier,
) {
    val path = remember(name) { computeAIIconByName(name) } ?: run {
        TextAvatar(name, modifier)
        return
    }
    AIIcon(
        path = path,
        name = name,
        modifier = modifier,
    )
}

// https://lobehub.com/zh/icons
private fun computeAIIconByName(name: String): String? {
    // 检查缓存
    ICON_CACHE[name]?.let { return it }

    val lowerName = name.lowercase()
    val result = when {
        PATTERN_OPENAI.containsMatchIn(lowerName) -> "openai"
        PATTERN_GEMINI.containsMatchIn(lowerName) -> "gemini-color"
        PATTERN_GOOGLE.containsMatchIn(lowerName) -> "google-color"
        PATTERN_ANTHROPIC.containsMatchIn(lowerName) -> "anthropic"
        PATTERN_CLAUDE.containsMatchIn(lowerName) -> "claude-color"
        PATTERN_DEEPSEEK.containsMatchIn(lowerName) -> "deepseek-color"
        PATTERN_GROK.containsMatchIn(lowerName) -> "grok"
        PATTERN_QWEN.containsMatchIn(lowerName) -> "qwen-color"
        PATTERN_DOUBAO.containsMatchIn(lowerName) -> "doubao-color"
        PATTERN_OPENROUTER.containsMatchIn(lowerName) -> "openrouter"
        PATTERN_ZHIPU.containsMatchIn(lowerName) -> "zhipu-color"
        PATTERN_MISTRAL.containsMatchIn(lowerName) -> "mistral-color"
        PATTERN_META.containsMatchIn(lowerName) -> "meta-color"
        PATTERN_HUNYUAN.containsMatchIn(lowerName) -> "hunyuan-color"
        PATTERN_GEMMA.containsMatchIn(lowerName) -> "gemma-color"
        PATTERN_PERPLEXITY.containsMatchIn(lowerName) -> "perplexity-color"
        PATTERN_ALIYUN.containsMatchIn(lowerName) -> "alibabacloud-color"
        PATTERN_BYTEDANCE.containsMatchIn(lowerName) -> "bytedance-color"
        PATTERN_SILLICON_CLOUD.containsMatchIn(lowerName) -> "siliconcloud-color"
        PATTERN_AIHUBMIX.containsMatchIn(lowerName) -> "aihubmix-color"
        PATTERN_OLLAMA.containsMatchIn(lowerName) -> "ollama"
        PATTERN_GITHUB.containsMatchIn(lowerName) -> "github"
        PATTERN_CLOUDFLARE.containsMatchIn(lowerName) -> "cloudflare-color"
        PATTERN_XAI.containsMatchIn(lowerName) -> "xai"
        else -> null
    }

    // 保存到缓存
    result?.let { ICON_CACHE[name] = it }

    return result
}

// 静态缓存和正则模式
private val ICON_CACHE = mutableMapOf<String, String>()
private val PATTERN_OPENAI = Regex("(gpt|openai|o\\d)")
private val PATTERN_GEMINI = Regex("(gemini)")
private val PATTERN_GOOGLE = Regex("google")
private val PATTERN_ANTHROPIC = Regex("anthropic")
private val PATTERN_CLAUDE = Regex("claude")
private val PATTERN_DEEPSEEK = Regex("deepseek")
private val PATTERN_GROK = Regex("grok")
private val PATTERN_QWEN = Regex("qwen|qwq|qvq")
private val PATTERN_DOUBAO = Regex("doubao")
private val PATTERN_OPENROUTER = Regex("openrouter")
private val PATTERN_ZHIPU = Regex("zhipu|智谱|glm")
private val PATTERN_MISTRAL = Regex("mistral")
private val PATTERN_META = Regex("meta|(?<!o)llama")
private val PATTERN_HUNYUAN = Regex("hunyuan|tencent")
private val PATTERN_GEMMA = Regex("gemma")
private val PATTERN_PERPLEXITY = Regex("perplexity")
private val PATTERN_BYTEDANCE = Regex("bytedance|火山")
private val PATTERN_ALIYUN = Regex("aliyun|阿里云|百炼")
private val PATTERN_SILLICON_CLOUD = Regex("硅基")
private val PATTERN_AIHUBMIX = Regex("aihubmix")
private val PATTERN_OLLAMA = Regex("ollama")
private val PATTERN_GITHUB = Regex("github")
private val PATTERN_CLOUDFLARE = Regex("cloudflare")
private val PATTERN_XAI = Regex("xai")