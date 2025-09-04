package me.rerere.rikkahub.ui.components.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.rikkahub.ui.theme.RikkahubTheme
import kotlin.math.max

private const val DEFAULT_SAMPLE_SIZE = 8 // Number of rows to measure for adaptive width
private val DEFAULT_CELL_PADDING = 8.dp

@Composable
fun <T> DataTable(
    columns: List<ColumnDefinition<T>>,
    data: List<T>,
    modifier: Modifier = Modifier,
    cellPadding: PaddingValues = PaddingValues(DEFAULT_CELL_PADDING),
    border: BorderStroke = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(0.5f)),
    adaptiveWidthSampleSize: Int = DEFAULT_SAMPLE_SIZE // Number of rows to sample for adaptive width calculation
) {
    var calculatedColumnWidths by remember(
        columns,
        data.size
    ) { // Recalculate if columns or data size change significantly
        mutableStateOf<List<Dp>?>(null)
    }

    val density = LocalDensity.current

    // Phase 1: Calculate Column Widths using SubcomposeLayout
    // This composable doesn't render anything visible itself, it just measures.
    SubcomposeColumnWidthCalculator(
        columns = columns,
        data = data,
        adaptiveWidthSampleSize = adaptiveWidthSampleSize,
        density = density,
        onWidthsCalculated = { widthsPx ->
            // Convert pixel widths back to Dp and store them
            calculatedColumnWidths = widthsPx.map { with(density) { it.toDp() } }
        }
    )

    // Phase 2: Render the Table using the calculated widths
    val horizontalScrollState = rememberScrollState()

    // Only render when widths are calculated
    if (calculatedColumnWidths != null) {
        Column(
            modifier = modifier
                .wrapContentSize()
                .clip(MaterialTheme.shapes.small)
                .border(border, MaterialTheme.shapes.small)
        ) {
            // Use HorizontalScroll for tables wider than the screen
            Column(
                modifier = Modifier.horizontalScroll(horizontalScrollState)
            ) {
                // --- Header Row ---
                TableHeaderRow(
                    columns = columns,
                    columnWidths = calculatedColumnWidths!!, // Not null here
                    cellPadding = cellPadding,
                    border = border
                )

                // --- Data Rows ---
                data.fastForEachIndexed { rowIndex, rowData ->
                    key(rowIndex) {
                        TableRow(
                            rowData = rowData,
                            columns = columns,
                            columnWidths = calculatedColumnWidths!!,
                            cellPadding = cellPadding,
                            border = border,
                        )
                    }
                }
            } // End Inner Column
        } // End Outer Column
    }
}


// --- Helper Composables ---

/**
 * Calculates column widths using SubcomposeLayout. Does not render anything itself.
 */
@Composable
private fun <T> SubcomposeColumnWidthCalculator(
    columns: List<ColumnDefinition<T>>,
    data: List<T>,
    adaptiveWidthSampleSize: Int,
    density: Density,
    onWidthsCalculated: (List<Int>) -> Unit
) {
    SubcomposeLayout { constraints ->
        val measuredWidths = IntArray(columns.size)
        val sampleData = data.take(adaptiveWidthSampleSize.coerceAtLeast(0)) // Take a sample

        columns.fastForEachIndexed { colIndex, column ->
            when (val widthDef = column.width) {
                is ColumnWidth.Fixed -> {
                    // Use fixed width directly
                    measuredWidths[colIndex] = with(density) { widthDef.width.roundToPx() }
                }

                is ColumnWidth.Adaptive -> {
                    var maxContentWidth = 0

                    // Measure Header
                    val headerPlaceables = subcompose("h_$colIndex") { column.header() }
                    headerPlaceables.forEach {
                        maxContentWidth =
                            max(maxContentWidth, it.maxIntrinsicWidth(Constraints.Infinity))
                    }

                    // Measure Sample Data Cells
                    sampleData.forEachIndexed { sampleRowIndex, rowData ->
                        // Unique slotId for each cell being measured
                        val cellSlotId = "c_${colIndex}_${sampleRowIndex}"
                        val cellPlaceables = subcompose(cellSlotId) { column.cell(rowData) }
                        cellPlaceables.forEach {
                            maxContentWidth =
                                max(maxContentWidth, it.maxIntrinsicWidth(Constraints.Infinity))
                        }
                    }

                    // Apply constraints
                    val minPx = with(density) { widthDef.min.roundToPx() }
                    val maxPx =
                        with(density) { if (widthDef.max == Dp.Infinity) Int.MAX_VALUE else widthDef.max.roundToPx() }
                    measuredWidths[colIndex] = maxContentWidth.coerceIn(minPx, maxPx)
                }
                // Add other width types (like Weighted) here if needed
            }
        }

        // Report calculated widths (in pixels)
        onWidthsCalculated(measuredWidths.toList())

        // Layout phase - we don't actually place anything here
        layout(0, 0) {}
    }
}

/**
 * Renders the Header Row.
 */
@Composable
private fun <T> TableHeaderRow(
    columns: List<ColumnDefinition<T>>,
    columnWidths: List<Dp>,
    cellPadding: PaddingValues,
    border: BorderStroke
) {
    Row(
        modifier = Modifier
            .border(border)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.fastForEachIndexed { index, column ->
            Box(
                modifier = Modifier
                    .width(columnWidths[index])
                    .padding(cellPadding)
            ) {
                column.header()
            }
        }
    }
}

/**
 * Renders a single Data Row.
 */
@Composable
private fun <T> TableRow(
    rowData: T,
    columns: List<ColumnDefinition<T>>,
    columnWidths: List<Dp>,
    cellPadding: PaddingValues,
    border: BorderStroke,
) {
    Row(
        modifier = Modifier
            .border(border),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.fastForEachIndexed { index, column ->
            key(index) {
                Box(
                    modifier = Modifier
                        .width(columnWidths[index])
                        .padding(cellPadding)
                ) {
                    column.cell(rowData)
                }
            }
        }
    }
}


// Sample Data Class
private data class User(val id: Int, val name: String, val email: String, val status: String)

// Sample Data
private val sampleUsers = List(50) { index ->
    User(
        id = index + 1,
        name = "User Name ${index + 1}".let { if (index % 5 == 0) it.repeat(3) else it }, // Make some names long
        email = "user${index + 1}@example.com",
        status = if (index % 3 == 0) "Active" else if (index % 3 == 1) "Inactive" else "Pending Review"
    )
}

@Composable
private fun MyDataTableScreen() {
    val columns = listOf(
        ColumnDefinition<User>(
            header = { Text("ID", fontWeight = FontWeight.Bold) },
            cell = { user -> Text(user.id.toString()) },
            // Fixed width for ID column
            width = ColumnWidth.Fixed(80.dp)
        ),
        ColumnDefinition(
            header = { Text("Name", fontWeight = FontWeight.Bold) },
            cell = { user -> Text(user.name) },
            // Adaptive width for Name, with a minimum
            width = ColumnWidth.Adaptive(min = 100.dp)
        ),
        ColumnDefinition(
            header = { Text("Email", fontWeight = FontWeight.Bold) },
            cell = { user -> Text(user.email) },
            // Fully adaptive width for Email
            width = ColumnWidth.Adaptive()
        ),
        ColumnDefinition(
            header = { Text("Status", fontWeight = FontWeight.Bold) },
            cell = { user ->
                Text(
                    text = user.status,
                    color = when (user.status) {
                        "Active" -> Color.Green.copy(alpha = 0.8f)
                        "Inactive" -> Color.Red.copy(alpha = 0.7f)
                        else -> Color.Gray
                    }
                )
            },
            // Adaptive width with min and max constraints
            width = ColumnWidth.Adaptive(min = 80.dp, max = 150.dp)
        )
    )
    DataTable(
        columns = columns,
        data = sampleUsers,
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun DefaultPreview() {
    RikkahubTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Data Table Example") },
                )
            }
        ) {
            Box(Modifier.padding(it)) {
                MyDataTableScreen()
            }
        }
    }
}
