package me.rerere.rikkahub.ui.components.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.composables.icons.lucide.Keyboard
import com.composables.icons.lucide.Lightbulb
import com.composables.icons.lucide.LightbulbOff
import com.composables.icons.lucide.Lucide
import me.rerere.ai.core.ReasoningLevel
import me.rerere.rikkahub.R

@Composable
fun ReasoningButton(
  modifier: Modifier = Modifier,
  onlyIcon: Boolean = false,
  reasoningTokens: Int,
  onUpdateReasoningTokens: (Int) -> Unit,
) {
  var showPicker by remember { mutableStateOf(false) }

  if (showPicker) {
    ReasoningPicker(
      reasoningTokens = reasoningTokens,
      onDismissRequest = { showPicker = false },
      onUpdateReasoningTokens = onUpdateReasoningTokens
    )
  }

  if (onlyIcon) {
    IconButton(
      onClick = {
        showPicker = true
      },
      modifier = modifier
    ) {
      Icon(
        imageVector = if (reasoningTokens == 0) Lucide.LightbulbOff else Lucide.Lightbulb,
        contentDescription = null
      )
    }
  } else {
    TextButton(
      onClick = {
        showPicker = true
      },
      modifier = modifier
    ) {
      Icon(Lucide.Lightbulb, null)
      Text(
        text = stringResource(id = R.string.reasoning_set_budget, reasoningTokens),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 8.dp)
      )
    }
  }
}

@Composable
fun ReasoningPicker(
  reasoningTokens: Int,
  onDismissRequest: () -> Unit = {},
  onUpdateReasoningTokens: (Int) -> Unit,
) {
  val currentLevel = ReasoningLevel.fromBudgetTokens(reasoningTokens)
  ModalBottomSheet(
    onDismissRequest = {
      onDismissRequest()
    },
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
  ) {
    Column(
      modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ReasoningLevelCard(
        selected = currentLevel == ReasoningLevel.OFF,
        icon = {
          Icon(Lucide.LightbulbOff, null)
        },
        title = {
          Text(stringResource(id = R.string.reasoning_off))
        },
        description = {
          Text(stringResource(id = R.string.reasoning_off_desc))
        },
        onClick = {
          onUpdateReasoningTokens(0)
        }
      )
      ReasoningLevelCard(
        selected = currentLevel == ReasoningLevel.LOW,
        icon = {
          Icon(Lucide.Lightbulb, null)
        },
        title = {
          Text(stringResource(id = R.string.reasoning_light))
        },
        description = {
          Text(stringResource(id = R.string.reasoning_light_desc))
        },
        onClick = {
          onUpdateReasoningTokens(1024)
        }
      )
      ReasoningLevelCard(
        selected = currentLevel == ReasoningLevel.MEDIUM,
        icon = {
          Icon(Lucide.Lightbulb, null)
        },
        title = {
          Text(stringResource(id = R.string.reasoning_medium))
        },
        description = {
          Text(stringResource(id = R.string.reasoning_medium_desc))
        },
        onClick = {
          onUpdateReasoningTokens(16_000)
        }
      )
      ReasoningLevelCard(
        selected = currentLevel == ReasoningLevel.HIGH,
        icon = {
          Icon(Lucide.Lightbulb, null)
        },
        title = {
          Text(stringResource(id = R.string.reasoning_heavy))
        },
        description = {
          Text(stringResource(id = R.string.reasoning_heavy_desc))
        },
        onClick = {
          onUpdateReasoningTokens(32_000)
        }
      )
      ReasoningLevelCard(
        modifier = Modifier.imePadding(),
        icon = {
          Icon(Lucide.Keyboard, null)
        },
        title = {
          Text(stringResource(id = R.string.reasoning_custom))
        },
        description = {
          var input by remember(reasoningTokens) {
            mutableStateOf(reasoningTokens.toString())
          }
          OutlinedTextField(
            value = input,
            onValueChange = { newValue ->
              input = newValue
              val newTokens = newValue.toIntOrNull()
              if (newTokens != null && newTokens >= 0) {
                onUpdateReasoningTokens(newTokens)
              }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
        },
        onClick = {}
      )
    }
  }
}

@Composable
private fun ReasoningLevelCard(
  modifier: Modifier = Modifier,
  selected: Boolean = false,
  icon: @Composable () -> Unit = {},
  title: @Composable () -> Unit = {},
  description: @Composable () -> Unit = {},
  onClick: () -> Unit,
) {
  Card(
    onClick = onClick,
    colors = if (selected) CardDefaults.cardColors() else CardDefaults.outlinedCardColors()
  ) {
    Row(
      modifier = modifier
          .fillMaxWidth()
          .padding(16.dp),
      horizontalArrangement = Arrangement.spacedBy(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      icon()
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
          title()
        }
        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
          description()
        }
      }
    }
  }
}

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
  MaterialTheme {
    var reasoningTokens by remember { mutableIntStateOf(0) }
    ReasoningPicker(
      onDismissRequest = {},
      reasoningTokens = reasoningTokens,
      onUpdateReasoningTokens = {
        reasoningTokens = it
      }
    )
  }
}