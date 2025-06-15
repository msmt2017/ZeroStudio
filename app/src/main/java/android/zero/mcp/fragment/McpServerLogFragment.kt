package android.zero.mcp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment

/**
 * A Fragment that displays real-time logs from the [LogManager] using Jetpack Compose UI.
 * This provides a visual output for server operations, communication, and errors,
 * which is essential for debugging and monitoring.
 */
class McpServerLogFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme { // Use MaterialTheme for consistent styling
                    McpServerLogScreen()
                }
            }
        }
    }
}

/**
 * Composable function for the MCP Server Log UI screen.
 * It observes logs from the [LogManager] and displays them in a scrollable list.
 */
@Composable
fun McpServerLogScreen() {
    // Collect logs as State to trigger recomposition when logs change
    val logs by LogManager.logs.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Log display area
        Text(
            text = "MCP Server Logs",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Occupy available vertical space
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Clear Logs Button
        Button(
            onClick = { LogManager.clearLogs() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Clear Logs")
        }
    }
}
