package me.rerere.rikkahub.ui.components.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Defines how a column's width should be determined
sealed class ColumnWidth {
    // Automatically determines width based on content, with optional constraints
    data class Adaptive(
        val min: Dp = 0.dp, // Minimum width
        val max: Dp = Dp.Infinity // Maximum width (Dp.Infinity means no upper limit)
    ) : ColumnWidth()

    // Fixed width for the column
    data class Fixed(val width: Dp) : ColumnWidth()
}

// Defines a single column in the DataTable
data class ColumnDefinition<T>(
    // Composable function to render the header cell for this column
    val header: @Composable () -> Unit,
    // Composable function to render a data cell for this column, given the row data
    val cell: @Composable (row: T) -> Unit,
    // How the width of this column is determined (defaults to Adaptive)
    val width: ColumnWidth = ColumnWidth.Adaptive()
)
