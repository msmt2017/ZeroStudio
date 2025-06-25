package android.zero.mcp.ui

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.ktor.server.engine.ApplicationEngine
import kotlinx.coroutines.launch

/**
 * 可视化 MCP + Ktor 服务状态管理 Fragment
 */
class McpKtorManagerFragment(private val server: ApplicationEngine?) : Fragment() {

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        return ComposeView(requireContext()).apply {
            setContent {
                Surface(color = MaterialTheme.colorScheme.background) {
                    McpKtorManagerScreen(server)
                }
            }
        }
    }
}

@Composable
fun McpKtorManagerScreen(server: ApplicationEngine?) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🛠 MCP 服务控制中心", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        KtorControlPanelUi(server = server)
        Spacer(modifier = Modifier.height(16.dp))

        ToolDevConsoleSimulator()
        Spacer(modifier = Modifier.height(24.dp))

        PromptTemplateScreen()
        Spacer(modifier = Modifier.height(24.dp))

        McpSettingsImportExportScreen()
    }
}
