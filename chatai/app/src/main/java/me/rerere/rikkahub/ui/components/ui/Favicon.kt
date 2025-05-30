package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun Favicon(url: String, modifier: Modifier = Modifier) {
    val faviconUrl = remember(url) {
        url.toHttpUrlOrNull()?.host?.let { host ->
            "https://icon.horse/icon/$host"
        }
    }
    AsyncImage(
        model = faviconUrl,
        modifier = modifier
            .clip(RoundedCornerShape(25))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .size(24.dp),
        contentDescription = null
    )
}