package me.rerere.rikkahub.data.ai

import android.content.Context
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.OutputMessageTransformer
import me.rerere.ai.ui.UIMessage
import me.rerere.rikkahub.utils.convertBase64ImagePartToLocalFile

object Base64ImageToLocalFileTransformer : OutputMessageTransformer {
    override suspend fun onGenerationFinish(
        context: Context,
        messages: List<UIMessage>,
        model: Model
    ): List<UIMessage> {
        return messages.map { message ->
            context.convertBase64ImagePartToLocalFile(message)
        }
    }
}