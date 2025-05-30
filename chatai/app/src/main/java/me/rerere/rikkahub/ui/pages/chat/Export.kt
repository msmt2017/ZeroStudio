package me.rerere.rikkahub.ui.pages.chat

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.composables.icons.lucide.FileText
import com.composables.icons.lucide.Lucide
import com.dokar.sonner.ToastType
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.ui.toSortedMessageParts
import me.rerere.ai.util.encodeBase64
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.utils.toLocalString
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime

@Composable
fun ChatExportSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    conversation: Conversation,
    selectedMessages: List<UIMessage>
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current

    if (visible) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = stringResource(id = R.string.chat_page_export_format))

                val successMessage =
                    stringResource(id = R.string.chat_page_export_success, "Markdown")
                OutlinedCard(
                    onClick = {
                        exportToMarkdown(context, conversation, selectedMessages)
                        toaster.show(
                            successMessage,
                            type = ToastType.Success
                        )
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(id = R.string.chat_page_export_markdown))
                        },
                        supportingContent = {
                            Text(stringResource(id = R.string.chat_page_export_markdown_desc))
                        },
                        leadingContent = {
                            Icon(Lucide.FileText, contentDescription = null)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatImageContent(conversation: Conversation, messages: List<UIMessage>) {
    Column(
        modifier = Modifier
            .background(Color.White)
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Text(
            text = conversation.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Exported on ${LocalDateTime.now().toLocalString()}",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        messages.forEachIndexed { index, message ->
            val roleName = when (message.role) {
                MessageRole.USER -> "User"
                MessageRole.ASSISTANT -> "Assistant"
                MessageRole.SYSTEM -> "System"
                MessageRole.TOOL -> "Tool"
            }
            Text(
                text = "$roleName:",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = message.toText(),
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            if (index < messages.size - 1) {
                Text(
                    text = "---",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(vertical = 4.dp),
                    color = Color.LightGray
                )
            }
        }
    }
}

private fun exportToMarkdown(
    context: Context,
    conversation: Conversation,
    messages: List<UIMessage>
) {
    val filename = "chat-export.md"

    val sb = buildAnnotatedString {
        append("# ${conversation.title}\n\n")
        append("*Exported on ${LocalDateTime.now().toLocalString()}*\n\n")

        messages.forEach { message ->
            val role = if (message.role == MessageRole.USER) "**User**" else "**Assistant**"
            append("$role:\n\n")
            message.parts.toSortedMessageParts().forEach { part ->
                when (part) {
                    is UIMessagePart.Text -> {
                        append(part.text)
                        appendLine()
                    }

                    is UIMessagePart.Image -> {
                        append("![Image](${part.encodeBase64()})")
                        appendLine()
                    }

                    is UIMessagePart.Reasoning -> {
                        part.reasoning.lines()
                            .filter { it.isNotBlank() }
                            .map { "> $it" }
                            .forEach {
                                append(it)
                            }
                        appendLine()
                        appendLine()
                    }

                    else -> {}
                }
            }
            appendLine()
            append("---")
            appendLine()
        }
    }

    try {
        val file = File(context.getExternalFilesDir(null), filename)
        if (!file.exists()) {
            file.createNewFile()
        } else {
            file.delete()
            file.createNewFile()
        }
        FileOutputStream(file).use {
            it.write(sb.toString().toByteArray())
        }

        // Share the file
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        shareFile(context, uri, "text/markdown")

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun shareFile(context: Context, uri: Uri, mimeType: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        android.content.Intent.createChooser(
            intent,
            context.getString(R.string.chat_page_export_share_via)
        )
    )
}