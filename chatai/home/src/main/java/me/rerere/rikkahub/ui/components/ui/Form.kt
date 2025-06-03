package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.weight(1f)
        ) {
            ProvideTextStyle(
                MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                label()
            }
            content()
            ProvideTextStyle(
                MaterialTheme.typography.labelSmall.copy(
                    color = LocalContentColor.current.copy(alpha = 0.6f)
                )
            ) {
                Column {
                    description?.invoke()
                }
            }
        }
        tail()
    }
}

@Preview(showBackground = true)
@Composable
private fun FormItemPreview() {
    FormItem(
        label = { Text("Label") },
        content = {
            OutlinedTextField(
                value = "",
                onValueChange = {}
            )
        },
        description = {
            Text("Description")
        }
    )
}