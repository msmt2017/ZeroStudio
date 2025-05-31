package me.rerere.rikkahub.ui.pages.history;

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@Composable
fun HistoryPage(vm: HistoryVM = koinViewModel()) {
    val navController = LocalNavController.current

    var searchText by remember { mutableStateOf("") }
    val allConversations by vm.conversations.collectAsStateWithLifecycle()
    val searchConversations by produceState(emptyList(), searchText) {
        runCatching {
            vm.searchConversations(searchText).collect {
                value = it
            }
        }.onFailure {
            it.printStackTrace()
        }
    }
    val showConversations = if (searchText.isEmpty()) {
        allConversations
    } else {
        searchConversations
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("聊天历史")
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    TextButton(
                        onClick = {
                            vm.deleteAllConversations()
                        }
                    ) {
                        Text("重置聊天")
                    }
                }
            )
        },
        bottomBar = {
            SearchInput(
                value = searchText,
                onValueChange = { searchText = it }
            )
        }
    ) { contentPadding ->
        LazyColumn(
            contentPadding = contentPadding + PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(showConversations, key = { it.id }) {
                ConversationItem(
                    conversation = it,
                    onClick = {
                        navigateToChatPage(navController, it.id)
                    },
                    onDelete = { vm.deleteConversation(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem()
                )
            }
        }
    }
}

@Composable
private fun SearchInput(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("输入关键词搜索聊天")
                },
                shape = RoundedCornerShape(50),
                singleLine = true,
                trailingIcon = {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier
                    ) {
                        Icon(Lucide.X, "Clear")
                    }
                }
            )
        }
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {},
) {
    Surface(
        onClick = onClick,
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(25),
        modifier = modifier
    ) {
        ListItem(
            headlineContent = {
                Text(conversation.title.ifBlank { "新对话" }.trim())
            },
            supportingContent = {
                Text(conversation.createAt.toLocalDateTime())
            },
            trailingContent = {
                IconButton(
                    onClick = onDelete
                ) {
                    Icon(Lucide.X, "Delete")
                }
            }
        )
    }
}