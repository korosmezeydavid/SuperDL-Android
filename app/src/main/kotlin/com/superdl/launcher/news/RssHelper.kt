package com.superdl.launcher.news

import android.os.Handler
import android.os.Looper
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.net.HttpURLConnection
import java.net.URL

data class NewsFeed(
    val id: String,
    val name: String,
    val category: String,
    val url: String
) {
    fun speakPreview(): String = if (category.isBlank()) name else "$category. $name"
}

data class RssItem(
    val title: String,
    val description: String,
    val source: String
) {
    fun speakPreview(): String {
        val preview = description.ifBlank { title }
        val trimmed = if (preview.length > 100) preview.take(100) + "…" else preview
        return "$source. $title. $trimmed"
    }

    fun speakFull(): String {
        val body = description.ifBlank { "Nincs leírás." }
        return "$source. $title. $body"
    }
}

object RssHelper {

    private const val ALL_FEEDS_ID = "all"

    private val FEEDS = listOf(
        NewsFeed(ALL_FEEDS_ID, "Összes hír", "", ""),
        NewsFeed("telex", "Telex", "Általános", "https://telex.hu/rss"),
        NewsFeed("444", "444", "Általános", "https://444.hu/feed"),
        NewsFeed("index", "Index", "Általános", "https://index.hu/24ora/rss/"),
        NewsFeed("hvg", "HVG", "Általános", "https://hvg.hu/rss"),
        NewsFeed("24hu", "24.hu", "Általános", "https://24.hu/feed/"),
        NewsFeed("portfolio", "Portfolio", "Gazdaság", "https://www.portfolio.hu/rss/all.xml"),
        NewsFeed("origo", "Origo", "Általános", "https://www.origo.hu/contentpartner/rss/hircentrum/origo.xml"),
        NewsFeed("rtl", "RTL", "Általános", "https://www.rtl.hu/content/rss"),
        NewsFeed("nepszava", "Népszava", "Politika", "https://nepszava.hu/rss"),
        NewsFeed("magyarnemzet", "Magyar Nemzet", "Politika", "https://magyarnemzet.hu/rss"),
        NewsFeed("hirstart", "Hírstart", "Általános", "https://www.hirstart.hu/rss.php"),
        NewsFeed("nemzetisport", "Nemzeti Sport", "Sport", "https://www.nemzetisport.hu/rss"),
        NewsFeed("index_sport", "Index Sport", "Sport", "https://index.hu/sport/rss/"),
        NewsFeed("hwsw", "HWSW", "Tech", "https://www.hwsw.hu/rss"),
        NewsFeed("itbusiness", "IT Business", "Tech", "https://www.itbusiness.hu/rss"),
        NewsFeed("kultura", "Kultúra.hu", "Kultúra", "https://kultura.hu/rss/"),
        NewsFeed("magyarorszag", "Magyarorszag.hu", "Általános", "https://magyarorszag.hu/rss")
    )

    fun allFeeds(): List<NewsFeed> = FEEDS

    fun fetchHeadlines(
        onResult: (List<RssItem>) -> Unit,
        onError: () -> Unit
    ) = fetchFromFeed(ALL_FEEDS_ID, onResult, onError)

    fun fetchFromFeed(
        feedId: String,
        onResult: (List<RssItem>) -> Unit,
        onError: () -> Unit
    ) {
        Thread {
            try {
                val items = if (feedId == ALL_FEEDS_ID) fetchMixed() else fetchSingle(feedId)
                Handler(Looper.getMainLooper()).post {
                    if (items.isEmpty()) onError() else onResult(items)
                }
            } catch (_: Exception) {
                Handler(Looper.getMainLooper()).post { onError() }
            }
        }.start()
    }

    private fun fetchSingle(feedId: String): List<RssItem> {
        val feed = FEEDS.find { it.id == feedId } ?: return emptyList()
        return parseFeed(feed.url, feed.name).take(15)
    }

    private fun fetchMixed(): List<RssItem> {
        val items = mutableListOf<RssItem>()
        for (feed in FEEDS.drop(1)) {
            if (items.size >= 15) break
            items.addAll(parseFeed(feed.url, feed.name).take(3))
        }
        return items.distinctBy { it.title }.take(15)
    }

    private fun parseFeed(feedUrl: String, source: String): List<RssItem> {
        val connection = URL(feedUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", "SuperDL/1.10")
        if (connection.responseCode !in 200..299) return emptyList()
        connection.inputStream.use { stream ->
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(stream, null)

            val items = mutableListOf<RssItem>()
            var event = parser.eventType
            var inItem = false
            var title = ""
            var description = ""

            while (event != XmlPullParser.END_DOCUMENT && items.size < 8) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name.lowercase()) {
                        "item", "entry" -> {
                            inItem = true
                            title = ""
                            description = ""
                        }
                        "title" -> if (inItem) title = parser.nextText().stripTags().trim()
                        "description", "summary", "content" ->
                            if (inItem && description.isBlank()) {
                                description = parser.nextText().stripTags().trim()
                            }
                    }
                    XmlPullParser.END_TAG -> if (parser.name.lowercase() in listOf("item", "entry")) {
                        if (title.isNotBlank()) {
                            items.add(RssItem(title, description, source))
                        }
                        inItem = false
                    }
                }
                event = parser.next()
            }
            return items
        }
    }

    private fun String.stripTags(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}