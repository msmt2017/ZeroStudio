package me.rerere.rikkahub.data.ai

import android.content.Context
import androidx.core.net.toFile
import androidx.core.net.toUri
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.InputMessageTransformer
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart

object DocumentAsPromptTransformer : InputMessageTransformer {
    override suspend fun transform(
        context: Context,
        messages: List<UIMessage>,
        model: Model
    ): List<UIMessage> {
        return messages.map { message ->
            message.copy(
                parts = message.parts.toMutableList().apply {
                    val documents = filterIsInstance<UIMessagePart.Document>()
                    if(documents.isNotEmpty()) {
                        documents.forEach { document ->
                            val file = document.url.toUri().toFile()
                            val text = file.readText()
                            val prompt = """
                                ## file: ${document.fileName}
                                ```
                                $text
                                ```
                            """.trimMargin()
                            add(UIMessagePart.Text(prompt))
                        }
                    }
                }
            )
        }
    }
}