package me.rerere.search

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.rerere.search.SearchResult.SearchResultItem
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.Locale

object BingSearchService : SearchService<SearchServiceOptions.BingLocalOptions> {
    override val name: String = "Bing"

    @Composable
    override fun Description() {
        Text(stringResource(R.string.bing_desc))
    }

    override suspend fun search(
        query: String,
        commonOptions: SearchCommonOptions,
        serviceOptions: SearchServiceOptions.BingLocalOptions
    ): Result<SearchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://www.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8")
            val locale = Locale.getDefault()
            val acceptLanguage = "${locale.language}-${locale.country},${locale.language}"
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 6.2; WOW64) AppleWebKit/537.15 (KHTML, like Gecko) Chrome/24.0.1295.0 Safari/537.15")
                .header(
                    "Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8"
                )
                .header("Accept-Language", acceptLanguage)
                .header("Accept-Encoding", "gzip, deflate, sdch")
                .header("Accept-Charset", "utf-8")
                .header("Connection", "keep-alive")
                .referrer("https://www.bing.com/")
                .cookie("SRCHHPGUSR", "ULSR=1")
                .timeout(5000)
                .get()

            // 解析搜索结果
            val results = doc.select("li.b_algo").map { element ->
                val title = element.select("h2").text()
                val link = element.select("h2 > a").attr("href")
                val snippet = element.select(".b_caption p").text()
                SearchResultItem(
                    title = title,
                    url = link,
                    text = snippet
                )
            }

            require(results.isNotEmpty()) {
                "Search failed: no results found"
            }

            SearchResult(items = results)
        }
    }
}
