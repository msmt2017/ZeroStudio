package android.zero.studio.chatai.data

import android.zero.studio.chatai.data.model.ApiType

object ModelConstants {
    // LinkedHashSet should be used to guarantee item order
val openaiModels = linkedSetOf(
    // 多模态模型
    "gpt-4o",           // 支持文本、图像、音频的全能模型，2024年5月发布
    "gpt-4o-mini",      // gpt-4o 的轻量版，2024年7月发布

    // 编程优化模型
    "gpt-4.1",          // 编程能力提升，支持最长达100万token的上下文，2025年4月发布
    "gpt-4.1-mini",     // gpt-4.1 的小型版本
    "gpt-4.1-nano",     // gpt-4.1 的超轻量版本

    // 推理模型
    "o3",               // 强化推理能力，2025年4月发布
    "o3-mini",          // o3 的小型版本，2025年1月发布
    "o3-mini-high",     // o3-mini 的高性能版本
    "o4-mini",          // o3 的升级版，支持图像推理，2025年4月发布
    "o4-mini-high",     // o4-mini 的高性能版本

    // 其他模型
    "gpt-4-turbo",      // GPT-4 的优化版本，2023年11月发布
    "gpt-4",            // 原始 GPT-4 模型，2023年3月发布
    "gpt-3.5-turbo",    // GPT-3.5 的增强版，2023年4月发布
    "gpt-3.5",          // 原始 GPT-3.5 模型，2022年3月发布
    "gpt-4.5",          // GPT-4 的升级版，2025年2月发布
    "davinci-002",      // GPT-3 系列中的高性能模型
    "babbage-002"       // GPT-3 系列中的中等性能模型
)

    // val anthropicModels = linkedSetOf("claude-3-5-sonnet-20240620", "claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
    val anthropicModels = linkedSetOf(
     "claude-3-7-sonnet-20250219",
     "claude-3-5-sonnet-20240620",
    "claude-3-opus-20240229", 
    "claude-3-sonnet-20240229", 
    "claude-3-haiku-20240307",
    "claude-2.1",
    "claude-instant-1.2"
)
    // val googleModels = linkedSetOf("gemini-1.5-pro-latest", "gemini-1.5-flash-latest", "gemini-1.0-pro")
val googleModels = linkedSetOf(
    "gemini-2.5-pro-preview-05-06",
    "gemini-2.5-flash-preview-04-17",
    "gemini-2.0-pro-experimental-20240515", // 实验版，可能不稳定
    "gemini-2.0-flash",
    "gemini-2.0-flash-lite",
    "gemini-1.5-pro-latest",
    "gemini-1.5-flash-latest",
    "gemini-1.5-flash-8b-latest", // 较小规模的智能任务模型
    "imagen-3",
    "veo-2",
    "gemini-2.0-flash-preview-image-generation",
    "gemini-2.0-flash-live",
    "gemini-embedding-experimental",
    "text-embedding-004",
    "embedding-003",
    "aqa"
    // 注意：gemini-1.0-pro 虽然已添加到列表中，但不推荐使用
)
    
    // val groqModels = linkedSetOf("llama-3.2-3b-preview", "llama-3.2-1b-preview", "llama-3.1-70b-versatile", "llama-3.1-8b-instant", "gemma2-9b-it")
    val groqModels = linkedSetOf(
    "llama-3.2-3b-preview",
    "llama-3.2-1b-preview",
    "llama-3.1-70b-versatile",
    "llama-3.1-8b-instant",
    "llama-3.1-8b-versatile",
    "llama-3.1-405b-reasoning",
    "llama3-8b-8192",
    "llama3-70b-8192",
    "llama-3.3-70b-versatile",
    "llama-guard-3-8b",
    "mixtral-8x7b-32768",
    "gemma2-9b-it",
    "gemma-7b-it",
    // "claude-3-5-sonnet",
    // "claude-3-opus",
    // "claude-3-haiku",
    "whisper-large-v3",
    "distil-whisper-large-v3-en",
    "whisper-large-v3-turbo",
    "llama-2-70b",
    
    "allam-2-7b",
    "deepseek-r1-distill-llama-70b",
    "meta-llama/llama-4-maverick-17b-128e-instruct",
    "meta-llama/llama-4-scout-17b-16e-instruct",
    "mistral-saba-24b",
    "playai-tts",
    "qwen-qwq-32b",
    "playai-tts-arabic",
    "compound-beta",
    "compound-beta-mini",
    
    "mixtral-8x7b"
)
val ollamaModels = linkedSetOf(
    "llama3.1",
    "llama3.2",
    "llama3.3",
    "llama4",
    "gemma2",
    "gemma3",
    "qwen1.5",
    "qwen2.5",
    "qwen3",
    "deepseek-r1",
    "phi4",
    "phi4-mini",
    "mistral",
    "mistral-small-3.1",
    "codellama",
    "qwen2.5-coder",
    "moondream",
    "neural-chat",
    "starling-lm",
    "llama2-uncensored",
    "granite3.3"
)

    // val ollamaModels = linkedSetOf<String>()

    const val OPENAI_API_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_API_URL = "https://api.anthropic.com/"
    const val GOOGLE_API_URL = "https://generativelanguage.googleapis.com"
    const val GROQ_API_URL = "https://api.groq.com/openai/v1/"

    fun getDefaultAPIUrl(apiType: ApiType) = when (apiType) {
        ApiType.OPENAI -> OPENAI_API_URL
        ApiType.ANTHROPIC -> ANTHROPIC_API_URL
        ApiType.GOOGLE -> GOOGLE_API_URL
        ApiType.GROQ -> GROQ_API_URL
        ApiType.OLLAMA -> ""
    }

    const val ANTHROPIC_MAXIMUM_TOKEN = 4096

    const val OPENAI_PROMPT =
        "You are a helpful, clever, and very friendly assistant. " +
            "You are familiar with various languages in the world. " +
            "You are to answer my questions precisely. "

    const val DEFAULT_PROMPT = "Your task is to answer my questions precisely."

    const val CHAT_TITLE_GENERATE_PROMPT =
        "Create a title that summarizes the chat. " +
            "The output must match the language that the user and the opponent is using, and should be less than 50 letters. " +
            "The output should only include the sentence in plain text without bullets or double asterisks. Do not use markdown syntax.\n" +
            "[Chat Content]\n"
}
