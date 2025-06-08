package me.rerere.rikkahub.ui.pages.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.Favicon
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.utils.plus
import me.rerere.rikkahub.utils.urlEncode
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Calendar

@Composable
fun MenuPage() {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    BackButton()
                },
                title = {},
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = it + PaddingValues(16.dp)
        ) {
            item {
                Greeting()
            }

            item {
                FeaturesSection()
            }

            item {
                LeaderBoard()
            }
        }
    }
}

@Composable
private fun Greeting() {
    @Composable
    fun getGreetingMessage(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> stringResource(id = R.string.menu_page_morning_greeting)
            in 12..17 -> stringResource(id = R.string.menu_page_afternoon_greeting)
            in 18..22 -> stringResource(id = R.string.menu_page_evening_greeting)
            else -> stringResource(id = R.string.menu_page_night_greeting)
        }
    }

    Column {
        Text(
            text = getGreetingMessage(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(vertical = 24.dp)
        )
    }
}

@Composable
private fun FeaturesSection() {
    val navController = LocalNavController.current

    @Composable
    fun CarouselItemScope.FeatureCard(
        title: @Composable () -> Unit,
        image: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
    ) {
        Box(
            modifier = modifier
                .clickable { onClick() }
                .maskClip(MaterialTheme.shapes.medium)
                .fillMaxWidth()
                .height(200.dp)
        ) {
            image()
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = Color.White)) {
                    title()
                }
            }
        }
    }

    HorizontalMultiBrowseCarousel(
        state = rememberCarouselState { 2 },
        itemSpacing = 8.dp,
        preferredItemWidth = 250.dp
    ) { index ->
        when (index) {
            0 -> {
                FeatureCard(
                    title = {
                        Text(stringResource(id = R.string.menu_page_ai_translator))
                    },
                    image = {
                        AsyncImage(
                            model = "file:///android_asset/banner/translator.jpeg",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    },
                ) {
                    navController.navigate("translator")
                }
            }

            1 -> {
                FeatureCard(
                    title = {
                        Text(stringResource(id = R.string.menu_page_knowledge_base))
                    },
                    image = {
                        AsyncImage(
                            model = "file:///android_asset/banner/library.jpeg",
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    },
                ) {
                    // navController.navigate("library")
                }
            }
        }
    }
}

@Composable
private fun LeaderBoard() {
    val navController = LocalNavController.current

    @Composable
    fun LeaderBoardItem(
        url: String,
        name: String,
    ) {
        Card(
            onClick = {
                navController.navigate("webview?url=${url.urlEncode()}")
            },
            modifier = Modifier.widthIn(min = 150.dp)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Favicon(
                    url = url,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = url.toHttpUrl().host,
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalContentColor.current.copy(alpha = 0.75f)
                )
            }
        }
    }

    Column {
        Text(
            text = stringResource(id = R.string.menu_page_llm_leaderboard),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LeaderBoardItem(
                url = "https://lmarena.ai/leaderboard",
                name = "LMArena"
            )

            LeaderBoardItem(
                url = "https://livebench.ai/#/",
                name = "LiveBench"
            )
        }
    }
}