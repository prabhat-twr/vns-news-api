package com.banarasbuzz.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime

/** Data is read from the same GitHub repo that the scraper and the web admin page write to. */
object Config {
    const val RAW_BASE = "https://raw.githubusercontent.com/prabhat-twr/vns-news-api/main/"
}

data class NewsItem(
    val id: String,
    val title: String,
    val summary: String,
    val cityId: String,
    val cityName: String,
    val dateText: String,
    val imageUrl: String?,
    val sourceUrl: String?,
    val sourceName: String,
    val isBreaking: Boolean,
    val timestamp: Long,
)

data class EventItem(
    val id: String,
    val name: String,
    val category: String,
    val startMillis: Long,
    val allDay: Boolean,
    val venue: String?,
    val description: String,
    val sourceUrl: String?,
    val imageUrl: String?,
    val approximateDate: Boolean,
)

data class Feed(
    val news: List<NewsItem> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val offline: Boolean = false,
)

private fun JSONObject.str(key: String): String? {
    if (!has(key) || isNull(key)) return null
    val v = optString(key, "")
    return if (v.isBlank()) null else v
}

private fun parseMillis(iso: String?): Long {
    if (iso == null) return 0L
    return try {
        OffsetDateTime.parse(iso).toInstant().toEpochMilli()
    } catch (e: Exception) {
        0L
    }
}

private fun parseNews(text: String?): List<NewsItem> {
    if (text == null) return emptyList()
    val out = ArrayList<NewsItem>()
    try {
        val arr = JSONArray(text)
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.str("title") ?: continue
            out.add(
                NewsItem(
                    id = o.str("id") ?: title,
                    title = title,
                    summary = o.str("summary") ?: o.str("content") ?: "",
                    cityId = o.str("cityId") ?: "",
                    cityName = o.str("cityName") ?: "",
                    dateText = o.str("date") ?: "",
                    imageUrl = o.str("imageUrl"),
                    sourceUrl = o.str("sourceUrl"),
                    sourceName = o.str("sourceName") ?: "",
                    isBreaking = o.optBoolean("isBreaking", false),
                    timestamp = parseMillis(o.str("firstSeenAt")),
                )
            )
        }
    } catch (e: Exception) {
        // Ignore malformed file; whatever parsed so far is returned.
    }
    return out
}

private fun parseEvents(text: String?): List<EventItem> {
    if (text == null) return emptyList()
    val out = ArrayList<EventItem>()
    try {
        val arr = JSONObject(text).optJSONArray("events") ?: return emptyList()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.str("name") ?: continue
            val loc = o.optJSONObject("location")
            out.add(
                EventItem(
                    id = o.str("id") ?: name,
                    name = name,
                    category = o.str("category") ?: "general",
                    startMillis = parseMillis(o.str("start")),
                    allDay = o.optBoolean("all_day", true),
                    venue = loc?.str("name"),
                    description = o.str("description") ?: "",
                    sourceUrl = o.str("source_url"),
                    imageUrl = o.str("image_url"),
                    approximateDate = (o.str("date_confidence") ?: "confirmed") != "confirmed",
                )
            )
        }
    } catch (e: Exception) {
        // Ignore malformed file.
    }
    return out
}

private fun parseHidden(text: String?, key: String): Set<String> {
    if (text == null) return emptySet()
    return try {
        val arr = JSONObject(text).optJSONArray(key) ?: return emptySet()
        (0 until arr.length()).map { arr.optString(it) }.toSet()
    } catch (e: Exception) {
        emptySet()
    }
}

object Repository {
    suspend fun load(context: Context): Feed = withContext(Dispatchers.IO) {
        var offline = false

        // Returns file text from the network (and caches it); falls back to the cached copy.
        fun fetch(name: String): String? {
            val cache = File(context.cacheDir, name)
            return try {
                val conn = URL(Config.RAW_BASE + name + "?t=" + System.currentTimeMillis() / 60000)
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                val code = conn.responseCode
                if (code == 404) {
                    cache.delete()
                    return null
                }
                if (code != 200) throw IOException("HTTP $code")
                val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                cache.writeText(text)
                text
            } catch (e: Exception) {
                offline = true
                if (cache.exists()) cache.readText() else null
            }
        }

        val scraped = fetch("news.json")
        val manualNews = fetch("manual-news.json")
        val eventsFile = fetch("events.json")
        val manualEvents = fetch("manual-events.json")
        val hidden = fetch("hidden.json")

        val hiddenNews = parseHidden(hidden, "news")
        val hiddenEvents = parseHidden(hidden, "events")

        val seen = HashSet<String>()
        val news = (parseNews(manualNews) + parseNews(scraped))
            .filter { it.id !in hiddenNews && seen.add(it.id) }
            .sortedByDescending { it.timestamp }

        val events = (parseEvents(eventsFile) + parseEvents(manualEvents))
            .filter { it.id !in hiddenEvents }
            .sortedBy { it.startMillis }

        Feed(news = news, events = events, offline = offline)
    }
}
