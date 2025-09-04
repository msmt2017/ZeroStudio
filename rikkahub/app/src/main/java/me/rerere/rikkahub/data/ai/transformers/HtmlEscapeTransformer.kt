package me.rerere.rikkahub.data.ai.transformers

import android.content.Context
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.OutputMessageTransformer
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import org.apache.commons.text.StringEscapeUtils

object HtmlEscapeTransformer : OutputMessageTransformer {
    override suspend fun visualTransform(
        context: Context,
        messages: List<UIMessage>,
        model: Model
    ): List<UIMessage> {
        return messages.map { message ->
            message.copy(
                parts = message.parts.map { part ->
                    if (message.role == MessageRole.ASSISTANT && part is UIMessagePart.Text) {
                        UIMessagePart.Text(StringEscapeUtils.unescapeHtml4(part.text))
                    } else {
                        part
                    }
                }
            )
        }
    }
}
