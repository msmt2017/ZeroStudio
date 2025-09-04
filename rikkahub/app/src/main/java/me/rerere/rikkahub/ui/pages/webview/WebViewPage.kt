package me.rerere.rikkahub.ui.pages.webview

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import com.composables.icons.lucide.ArrowRight
import com.composables.icons.lucide.Earth
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RefreshCw
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.utils.base64Decode

@Composable
fun WebViewPage(url: String, content: String) {
    val state = if (url.isNotEmpty()) {
        rememberWebViewState(
            url = url,
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            })
    } else {
        rememberWebViewState(
            data = content.base64Decode(),
            settings = {
                builtInZoomControls = true
                displayZoomControls = false
            }
        )
    }

    BackHandler(state.canGoBack) {
        state.goBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.pageTitle?.takeIf { it.isNotEmpty() } ?: state.currentUrl
                        ?: "",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleSmall
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    IconButton(onClick = { state.reload() }) {
                        Icon(Lucide.RefreshCw, contentDescription = "Refresh")
                    }

                    IconButton(
                        onClick = { state.goForward() },
                        enabled = state.canGoForward
                    ) {
                        Icon(Lucide.ArrowRight, contentDescription = "Forward")
                    }

                    val urlHandler = LocalUriHandler.current
                    IconButton(
                        onClick = {
                            state.currentUrl?.let { url ->
                                if (url.isNotBlank()) {
                                    urlHandler.openUri(url)
                                }
                            }
                        }
                    ) {
                        Icon(Lucide.Earth, contentDescription = "Open in Browser")
                    }
                }
            )
        }
    ) {
        WebView(
            state = state,
            modifier = Modifier
                .fillMaxSize()
                .padding(it),
        )
    }
}
