package me.rerere.rikkahub.ui.pages.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.GripHorizontal
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Pencil
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.Trash2
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.DEFAULT_ASSISTANTS_IDS
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantMemory
import me.rerere.rikkahub.ui.components.chat.UIAvatar
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.hooks.EditState
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

@Composable
fun AssistantPage(vm: AssistantVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val createState = useEditState<Assistant> {
        vm.addAssistant(it)
    }
    val navController = LocalNavController.current

    // 标签过滤状态
    var selectedTagIds by remember { mutableStateOf(emptySet<Uuid>()) }

    // 根据选中的标签过滤助手
    val filteredAssistants = remember(settings.assistants, selectedTagIds) {
        if (selectedTagIds.isEmpty()) {
            settings.assistants
        } else {
            settings.assistants.filter { assistant ->
                assistant.tags.any { tagId -> tagId in selectedTagIds }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(
                        onClick = {
                            createState.open(Assistant())
                        }
                    ) {
                        Icon(Lucide.Plus, stringResource(R.string.assistant_page_add))
                    }
                }
            )
        }
    ) {
        val lazyListState = rememberLazyListState()
        val isFiltering = selectedTagIds.isNotEmpty()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            // 只有在没有过滤时才允许重排序
            if (!isFiltering) {
                // 需要考虑标签过滤器可能占用的位置
                val hasTagFilter = settings.assistantTags.isNotEmpty()
                val offset = if (hasTagFilter) 1 else 0

                val fromIndex = from.index - offset
                val toIndex = to.index - offset

                if (fromIndex >= 0 && toIndex >= 0 && fromIndex < settings.assistants.size && toIndex < settings.assistants.size) {
                    val newAssistants = settings.assistants.toMutableList().apply {
                        add(toIndex, removeAt(fromIndex))
                    }
                    vm.updateSettings(settings.copy(assistants = newAssistants))
                }
            }
        }
        val haptic = LocalHapticFeedback.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = it + PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            state = lazyListState
        ) {
            // 标签过滤器
            if (settings.assistantTags.isNotEmpty()) {
                item("tag_filter") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(settings.assistantTags, key = { tag -> tag.id }) { tag ->
                                FilterChip(
                                    onClick = {
                                        selectedTagIds = if (tag.id in selectedTagIds) {
                                            selectedTagIds - tag.id
                                        } else {
                                            selectedTagIds + tag.id
                                        }
                                    },
                                    label = { Text(tag.name) },
                                    selected = tag.id in selectedTagIds,
                                    shape = RoundedCornerShape(50),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            items(filteredAssistants, key = { assistant -> assistant.id }) { assistant ->
                ReorderableItem(
                    state = reorderableState,
                    key = assistant.id
                ) { isDragging ->
                    val memories by vm.getMemories(assistant).collectAsStateWithLifecycle(
                        initialValue = emptyList(),
                    )
                    AssistantItem(
                        assistant = assistant,
                        settings = settings,
                        memories = memories,
                        onEdit = {
                            navController.navigate(Screen.AssistantDetail(id = assistant.id.toString()))
                        },
                        onDelete = {
                            vm.removeAssistant(assistant)
                        },
                        modifier = Modifier
                            .scale(if (isDragging) 0.95f else 1f)
                            .animateItem(),
                        dragHandle = {
                            // 只有在没有过滤时才显示拖拽手柄
                            if (!isFiltering) {
                                Icon(
                                    imageVector = Lucide.GripHorizontal,
                                    contentDescription = null,
                                    modifier = Modifier.longPressDraggableHandle(
                                        onDragStarted = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                                        },
                                        onDragStopped = {
                                            haptic.performHapticFeedback(HapticFeedbackType.GestureEnd)
                                        }
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    AssistantCreationSheet(createState)
}

@Composable
private fun AssistantCreationSheet(
    state: EditState<Assistant>,
) {
    state.EditStateContent { assistant, update ->
        ModalBottomSheet(
            onDismissRequest = {
                state.dismiss()
            },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            dragHandle = {},
            sheetGesturesEnabled = false
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .padding(horizontal = 16.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    FormItem(
                        label = {
                            Text(stringResource(R.string.assistant_page_name))
                        },
                    ) {
                        OutlinedTextField(
                            value = assistant.name,
                            onValueChange = {
                                update(
                                    assistant.copy(
                                        name = it
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            state.dismiss()
                        }
                    ) {
                        Text(stringResource(R.string.assistant_page_cancel))
                    }
                    TextButton(
                        onClick = {
                            state.confirm()
                        }
                    ) {
                        Text(stringResource(R.string.assistant_page_save))
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantItem(
    assistant: Assistant,
    settings: Settings,
    modifier: Modifier = Modifier,
    memories: List<AssistantMemory>,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandle: @Composable () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Basic Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UIAvatar(
                    name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    value = assistant.avatar,
                )

                Text(
                    text = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                    style = MaterialTheme.typography.titleLarge
                )

                if (assistant.enableMemory) {
                    Tag(
                        type = TagType.SUCCESS
                    ) {
                        Text(stringResource(R.string.assistant_page_memory_count, memories.size))
                    }
                }

                Spacer(Modifier.weight(1f))

                dragHandle()
            }

            // Tags
            if (assistant.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    assistant.tags.fastForEach { tagId ->
                        val tag = settings.assistantTags.find { it.id == tagId }
                            ?: return@fastForEach // 如果找不到标签，则跳过
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Text(
                                text = tag.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }

            // System Prompt
            Text(
                text = buildAnnotatedString {
                    if (assistant.systemPrompt.isNotBlank()) {
                        // 变量替换为蓝色
                        // 正则匹配 {xxx}
                        val regex = "\\{[^}]+\\}".toRegex()
                        var lastIndex = 0
                        val input = assistant.systemPrompt
                        regex.findAll(input).forEach { matchResult ->
                            val start = matchResult.range.first
                            val end = matchResult.range.last + 1
                            // 普通文本
                            if (lastIndex < start) {
                                append(input.substring(lastIndex, start))
                            }
                            // 蓝色变量
                            withStyle(SpanStyle(color = MaterialTheme.extendColors.blue6)) { // 你可以自定义颜色
                                append(input.substring(start, end))
                            }
                            lastIndex = end
                        }
                        // 末尾剩余文本
                        if (lastIndex < input.length) {
                            append(input.substring(lastIndex))
                        }
                    } else {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(stringResource(R.string.assistant_page_no_system_prompt))
                        }
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))

                // Right
                TextButton(
                    onClick = {
                        showDeleteDialog = true
                    },
                    enabled = assistant.id !in DEFAULT_ASSISTANTS_IDS
                ) {
                    Icon(
                        Lucide.Trash2,
                        stringResource(R.string.assistant_page_delete),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(18.dp)
                    )
                    Text(stringResource(R.string.assistant_page_delete))
                }

                Button(
                    onClick = {
                        onEdit()
                    }
                ) {
                    Icon(
                        Lucide.Pencil,
                        stringResource(R.string.edit),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(18.dp)
                    )
                    Text(stringResource(R.string.edit))
                }
            }
        }
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text(stringResource(R.string.assistant_page_delete))
            },
            text = {
                Text(stringResource(R.string.assistant_page_delete_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
