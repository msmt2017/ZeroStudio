package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bot
import com.composables.icons.lucide.Lucide
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.hooks.rememberAssistantState

@Composable
fun AssistantPicker(
    settings: Settings,
    onUpdateSettings: (Settings) -> Unit,
    modifier: Modifier = Modifier,
    onClickSetting: () -> Unit,
) {
    val state = rememberAssistantState(settings, onUpdateSettings)
    val defaultAssistantName = stringResource(R.string.assistant_page_default_assistant)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onClickSetting,
        ) {
            Icon(Lucide.Bot, null)
        }
        Select(
            options = settings.assistants,
            selectedOption = state.currentAssistant,
            onOptionSelected = {
                state.setSelectAssistant(it)
            },
            optionToString = {
                it.name.ifEmpty { defaultAssistantName }
            },
            modifier = Modifier.weight(1f),
        )
    }
}