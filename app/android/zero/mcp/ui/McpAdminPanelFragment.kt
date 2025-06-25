package android.zero.mcp.ui

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import io.ktor.server.engine.ApplicationEngine

/**
 * MCP 总管理面板 Fragment：集成所有子 UI 控件统一入口
 */
class McpAdminPanelFragment(private val server: ApplicationEngine?) : Fragment() {

    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): android.view.View {
        return ComposeView(requireContext()).apply {
            setContent {
                Surface(color = MaterialTheme.colorScheme.background) {
                    McpAdminPanelScreen(server)
                }
            }
        }
    }
}

@Composable
fun McpAdminPanelScreen(server: ApplicationEngine?) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("服务控制", "工具测试", "Prompt 管理", "配置管理", "SSE 会话", "统计日志")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabTitles.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Text(text = title, modifier = Modifier.padding(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        when (selectedTab) {
            0 -> KtorControlPanelUi(server)
            1 -> ToolDevConsoleSimulator()
            2 -> PromptTemplateScreen()
            3 -> McpSettingsImportExportScreen()
            4 -> SseMonitorPanelUi()
            5 -> McpStatsLogPanelUi()
        }
    }
}
