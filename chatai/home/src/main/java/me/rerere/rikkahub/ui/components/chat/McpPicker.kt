package me.rerere.rikkahub.ui.components.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastSumBy
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquareOff
import com.composables.icons.lucide.MessageSquareX
import com.composables.icons.lucide.Terminal
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.mcp.McpManager
import me.rerere.rikkahub.data.mcp.McpServerConfig
import me.rerere.rikkahub.data.mcp.McpStatus
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import org.koin.compose.koinInject

@Composable
fun McpPickerButton(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    mcpManager: McpManager,
    onUpdateAssistant: (Assistant) -> Unit
) {
    var showMcpPicker by remember { mutableStateOf(false) }
    val status by mcpManager.syncingStatus.collectAsStateWithLifecycle()
    val loading = status.values.any { it == McpStatus.Connecting }
    BadgedBox(
        badge = {
            val enabledServers = servers.fastFilter {
                it.commonOptions.enable
            }
            if (enabledServers.isNotEmpty()) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(text = enabledServers.size.toString())
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                showMcpPicker = true
            },
            modifier = Modifier
        ) {
            Box {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Lucide.Terminal, null)
                }
            }
        }
    }
    if (showMcpPicker) {
        ModalBottomSheet(
            onDismissRequest = { showMcpPicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.mcp_picker_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
                AnimatedVisibility(loading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        LinearWavyProgressIndicator()
                        Text(
                            text = stringResource(id = R.string.mcp_picker_syncing),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                McpPicker(
                    assistant = assistant,
                    servers = servers,
                    onUpdateAssistant = {
                        onUpdateAssistant(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }
        }
    }
}

@Composable
fun McpPicker(
    assistant: Assistant,
    servers: List<McpServerConfig>,
    modifier: Modifier = Modifier,
    onUpdateAssistant: (Assistant) -> Unit
) {
    val mcpManager = koinInject<McpManager>()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(servers.fastFilter { it.commonOptions.enable }) { server ->
            val status by mcpManager.getStatus(server).collectAsStateWithLifecycle(McpStatus.Idle)
            Card {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (status) {
                        McpStatus.Idle -> Icon(Lucide.MessageSquareOff, null)
                        McpStatus.Connecting -> CircularProgressIndicator(
                            modifier = Modifier.size(
                                24.dp
                            )
                        )

                        McpStatus.Connected -> Icon(Lucide.Terminal, null)
                        is McpStatus.Error -> Icon(Lucide.CircleAlert, null)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = server.commonOptions.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = when (status) {
                                is McpStatus.Idle -> "Idle"
                                is McpStatus.Connecting -> "Connecting"
                                is McpStatus.Connected -> "Connected"
                                is McpStatus.Error -> "Error: ${(status as McpStatus.Error).message}"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalContentColor.current.copy(alpha = 0.8f)
                        )
                        if (status == McpStatus.Connected) {
                            val tools = server.commonOptions.tools
                            val enabledTools = tools.fastFilter { it.enable }
                            Tag(
                                type = TagType.INFO
                            ) {
                                Text("${enabledTools.size}/${tools.size} tools")
                            }
                        }
                    }
                    Switch(
                        checked = server.id in assistant.mcpServers,
                        onCheckedChange = {
                            if (it) {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.add(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } // remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            } else {
                                val newServers = assistant.mcpServers.toMutableSet()
                                newServers.remove(server.id)
                                newServers.removeIf { servers.none { s -> s.id == server.id } } //  remove invalid servers
                                onUpdateAssistant(
                                    assistant.copy(
                                        mcpServers = newServers.toSet()
                                    )
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}