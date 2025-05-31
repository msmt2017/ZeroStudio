package me.rerere.rikkahub.ui.pages.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownBlock
import me.rerere.rikkahub.ui.components.richtext.MathBlock
import me.rerere.rikkahub.ui.components.richtext.Mermaid
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.search.SearchService
import org.koin.androidx.compose.koinViewModel
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.uuid.Uuid

@Composable
fun DebugPage(vm: DebugVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Debug Mode")
                },
                navigationIcon = {
                    BackButton()
                }
            )
        }
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Mermaid(
                code = """
                mindmap
                  root((mindmap))
                    Origins
                      Long history
                      ::icon(fa fa-book)
                      Popularisation
                        British popular psychology author Tony Buzan
                    Research
                      On effectiveness<br/>and features
                      On Automatic creation
                        Uses
                            Creative techniques
                            Strategic planning
                            Argument mapping
                    Tools
                      Pen and paper
                      Mermaid
                """.trimIndent(),
                modifier = Modifier.fillMaxWidth(),
            )

            DebugTtsDemoComponent()

            var counter by remember {
                mutableIntStateOf(0)
            }
            val toaster = LocalToaster.current
            Button(
                onClick = {
                    toaster.show("测试 ${counter++}")
                    toaster.show("测试 ${counter++}", type = ToastType.Info)
                    toaster.show("测试 ${counter++}", type = ToastType.Error)
                }
            ) {
                Text("toast")
            }
            Button(
                onClick = {
                    vm.updateSettings(
                        settings.copy(
                            chatModelId = Uuid.random()
                        )
                    )
                }
            ) {
                Text("重置Chat模型")
            }

            Button(
                onClick = {
                    error("测试崩溃 ${Random.nextInt(0..1000)}")
                }
            ) {
                Text("崩溃")
            }

            val scope = rememberCoroutineScope()
            Button(
                onClick = {
                    scope.launch {
                        val service = SearchService.getService(settings.searchServiceOptions)
                        val result = service.search(
                            query = "mc 1.21.5更新内容",
                            commonOptions = settings.searchCommonOptions,
                            serviceOptions = settings.searchServiceOptions
                        )
                        result.onSuccess {
                            println(it)
                        }.onFailure {
                            it.printStackTrace()
                        }
                    }
                }
            ) {
                Text("测试搜索")
            }

            var markdown by remember { mutableStateOf("") }
            MarkdownBlock(markdown,  modifier = Modifier.fillMaxWidth())
            MathBlock(markdown)
            OutlinedTextField(
                value = markdown,
                onValueChange = { markdown = it },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}