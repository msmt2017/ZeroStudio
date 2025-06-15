// File: android/zero/mcp/MainActivity.kt
package android.zero.mcp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor
import android.zero.mcp.handlers.CodeEditorProvider
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectAsState
import kotlinx.coroutines.flow.map // Import map extension function
import java.io.File

/**
 * [MainActivity] serves as the host application's entry point for the MCP Server.
 * It's responsible for:
 * 1. Starting the [McpServer] as a foreground service.
 * 2. Requesting necessary Android permissions.
 * 3. Providing the [CodeEditor] instance to the [McpService] via [CodeEditorProvider].
 * 4. Displaying MCP server logs in a Compose UI using [McpServerLogFragment].
 *
 * @author Android Zero
 */
class MainActivity : ComponentActivity() {

    // Lateinit var for McpService, initialized in onCreate after application cast
    private lateinit var mcpService: McpService

    // In-memory mock for opened file path in the editor
    private var mockOpenedFilePath: String? = null
    private var codeEditorInstance: CodeEditor? = null

    // Register a permission request launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.entries.all { it.value }
            if (allGranted) {
                LogManager.addLog("MainActivity: All permissions granted.", "INFO", "Permissions")
                startMcpService()
            } else {
                LogManager.addLog("MainActivity: Not all permissions granted. MCP service might be limited.", "WARN", "Permissions")
                Toast.makeText(this, "Permissions not fully granted, MCP functionality may be limited.", Toast.LENGTH_LONG).show()
                startMcpService() // Try to start service even if not all granted, for partial functionality
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LogManager.addLog("MainActivity: onCreate called.", "INFO", "MainActivity")

        // Get McpService instance from the application class
        mcpService = (application as McpApplication).getMcpService()

        // Set the CodeEditorProvider implementation
        mcpService.setCodeEditorProvider(object : CodeEditorProvider {
            override fun getCodeEditor(): CodeEditor? {
                return codeEditorInstance
            }

            override fun getCodeEditorContent(): Content? {
                return codeEditorInstance?.text
            }

            override fun getOpenedFilePath(): String? {
                // Return a mock path for demonstration. In a real IDE, this would be the actual file path.
                return mockOpenedFilePath ?: "/data/data/${packageName}/files/mock_project/app/src/main/java/com/example/MyApplication.kt"
            }

            override fun runOnEditorUi(action: (editor: CodeEditor, content: Content) -> Unit) {
                codeEditorInstance?.post {
                    codeEditorInstance?.let { editor ->
                        editor.text?.let { content ->
                            action(editor, content)
                        }
                    }
                }
            }

            override fun getCursorPosition(): Pair<Int, Int>? {
                return codeEditorInstance?.cursor?.run {
                    Pair(leftLine, leftColumn)
                }
            }

            override fun getSelectionRange(): Triple<Int, Int, Int>? {
                return codeEditorInstance?.cursor?.run {
                    if (isSelected) {
                        Triple(leftLine, leftColumn, rightColumn)
                    } else {
                        null
                    }
                }
            }
        })

        // Request necessary permissions at runtime
        requestPermissions()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    /**
     * Requests necessary runtime permissions for file access and Termux command execution.
     */
    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        // For Termux:API, com.termux.permission.RUN_COMMAND is crucial.
        // This is a custom permission granted by the Termux app itself.
        // It won't be in Manifest.permission, but we still need to request it.
        // Direct request via ActivityResultContracts.RequestMultiplePermissions
        // might not work for custom permissions from other apps.
        // Users typically grant this in Termux settings or when prompted by Termux itself.
        // We'll primarily rely on Termux's internal permission checks.
        // However, if the user explicitly needs to grant it through your app,
        // you might need to guide them to Termux settings.
        permissionsToRequest.add("com.termux.permission.RUN_COMMAND")


        // Filter out permissions already granted
        val ungrantedPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (ungrantedPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(ungrantedPermissions.toTypedArray())
        } else {
            LogManager.addLog("MainActivity: All required permissions already granted.", "INFO", "Permissions")
            startMcpService()
        }
    }

    /**
     * Starts the [McpServer] foreground service.
     */
    private fun startMcpService() {
        val serviceIntent = Intent(this, McpServer::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        LogManager.addLog("MainActivity: Attempted to start McpServer.", "INFO", "MainActivity")
    }

    override fun onDestroy() {
        super.onDestroy()
        LogManager.addLog("MainActivity: onDestroy called. Stopping McpServer...", "INFO", "MainActivity")
        val serviceIntent = Intent(this, McpServer::class.java)
        stopService(serviceIntent) // Stop the foreground service
    }

    /**
     * Composable for the main screen UI, including the mock editor and log display.
     */
    @Composable
    fun MainScreen() {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "MCP Server Host Application",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            // Mock CodeEditor Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up available space
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = "Mock Code Editor (TabFile)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            CodeEditor(context).apply {
                                // Initialize CodeEditor here
                                text = Content("fun main() {\n    println(\"Hello, MCP!\")\n}\n")
                                // Set this instance to the class variable
                                codeEditorInstance = this
                                // Set a mock file path
                                mockOpenedFilePath = File(context.filesDir, "mock_project/app/src/main/java/com/example/MyApplication.kt").absolutePath
                                LogManager.addLog("MainActivity: CodeEditor initialized with mock content.", "INFO", "MainActivity")
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MCP Server Log Display (McpServerLogFragment equivalent)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up available space
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = "MCP Server Logs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { LogManager.clearLogs() }) {
                            Text("Clear Logs")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    McpServerLogFragment()
                }
            }
        }
    }

    /**
     * [McpServerLogFragment] Composable equivalent for displaying logs.
     * This directly observes the [LogManager.logEntries] flow.
     */
    @Composable
    fun McpServerLogFragment() {
        val logs by LogManager.logEntries.collectAsState()

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 4.dp)
        ) {
            items(logs.toList()) { logEntry ->
                Text(
                    text = logEntry.message,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        logEntry.message.contains("[ERROR]", ignoreCase = true) -> Color.Red
                        logEntry.message.contains("[WARN]", ignoreCase = true) -> Color(0xFFFFA500) // Orange
                        logEntry.message.contains("[DEBUG]", ignoreCase = true) -> Color.Gray
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        // You'd typically need to mock McpApplication and its services for a full preview
        // For simplicity, we just show the screen structure.
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "MCP Server Host Application (Preview)",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.headlineMedium
            )

            // Mock CodeEditor Area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Text(
                        text = "Mock Code Editor (TabFile) - Preview",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "fun main() {\n    println(\"Hello, MCP!\")\n}\n",
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MCP Server Log Display (McpServerLogFragment equivalent)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            text = "MCP Server Logs (Preview)",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = { /* No action in preview */ }) {
                            Text("Clear Logs")
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    // Mock logs for preview
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                    ) {
                        items(listOf(
                            LogEntry("[10:00:00.000] [INFO] [KtorServer] Server started.", 0),
                            LogEntry("[10:00:01.000] [DEBUG] [McpService] Command received...", 0),
                            LogEntry("[10:00:02.000] [WARN] [Permissions] Missing storage permission.", 0),
                            LogEntry("[10:00:03.000] [ERROR] [FileHandler] File not found.", 0)
                        )) { logEntry ->
                            Text(
                                text = logEntry.message,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    logEntry.message.contains("[ERROR]", ignoreCase = true) -> Color.Red
                                    logEntry.message.contains("[WARN]", ignoreCase = true) -> Color(0xFFFFA500)
                                    logEntry.message.contains("[DEBUG]", ignoreCase = true) -> Color.Gray
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
