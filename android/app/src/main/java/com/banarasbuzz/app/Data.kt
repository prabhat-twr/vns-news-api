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

/** One tithi / nakshatra / yoga / karana entry. `endsAt` is 0 when it runs past the day. */
data class Anga(val name: String, val paksha: String?, val endsAt: Long, val nameHi: String? = null)

data class TimeWindow(val start: Long, val end: Long)

/** One day's Varanasi panchang (varanasi-panchang-daily.json). */
data class Panchang(
    val date: String,
    val vara: String,
    val tithi: List<Anga>,
    val nakshatra: List<Anga>,
    val yoga: List<Anga>,
    val karana: List<Anga>,
    val sunrise: Long,
    val sunset: Long,
    val moonrise: Long,
    val moonset: Long,
    val rahuKaal: TimeWindow?,
    val abhijit: TimeWindow?,
    val festivals: List<String>,
)

/** An upcoming vrat or tyohar (varanasi-panchang-calendar.json). */
data class Observance(
    val id: String,
    val name: String,
    val nameHi: String?,
    val type: String,
    val date: String,
    val tithi: String,
    val paksha: String,
    val description: String,
)

data class Feed(
    val news: List<NewsItem> = emptyList(),
    val events: List<EventItem> = emptyList(),
    val panchang: Panchang? = null,
    val observances: List<Observance> = emptyList(),
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

private fun parseAngas(arr: JSONArray?): List<Anga> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val name = o.str("name") ?: return@mapNotNull null
        Anga(name, o.str("paksha"), parseMillis(o.str("ends_at")), o.str("name_hi"))
    }
}

private fun parseWindow(o: JSONObject?): TimeWindow? {
    if (o == null) return null
    val start = parseMillis(o.str("start"))
    val end = parseMillis(o.str("end"))
    return if (start == 0L || end == 0L) null else TimeWindow(start, end)
}

/**
 * Today's panchang as a JSON object: the matching day from the 8-day file if there is one,
 * otherwise the single-day file (which may be older; the UI says so).
 */
internal fun todaysPanchangJson(daysText: String?, dailyText: String?, today: String = todayIst().toString()): JSONObject? {
    try {
        val days = daysText?.let { JSONObject(it).optJSONArray("days") }
        if (days != null) for (i in 0 until days.length()) {
            val d = days.optJSONObject(i) ?: continue
            if (d.optString("date") == today) return d
        }
    } catch (e: Exception) {
        // Fall through to the single-day file.
    }
    return try {
        dailyText?.let { JSONObject(it) }
    } catch (e: Exception) {
        null
    }
}

/** Reads today's panchang from the files the app last downloaded (used by alerts and the pinned notification). */
fun cachedPanchangJson(context: android.content.Context): JSONObject? {
    fun read(name: String) = File(context.cacheDir, name).takeIf { it.exists() }?.readText()
    return todaysPanchangJson(read("varanasi-panchang-days.json"), read("varanasi-panchang-daily.json"))
}

private fun parsePanchang(o: JSONObject?): Panchang? {
    if (o == null) return null
    return try {
        val fest = o.optJSONArray("festivals_and_vrats")
        Panchang(
            date = o.str("date") ?: return null,
            vara = o.str("vara") ?: "",
            tithi = parseAngas(o.optJSONArray("tithi")),
            nakshatra = parseAngas(o.optJSONArray("nakshatra")),
            yoga = parseAngas(o.optJSONArray("yoga")),
            karana = parseAngas(o.optJSONArray("karana")),
            sunrise = parseMillis(o.str("sunrise")),
            sunset = parseMillis(o.str("sunset")),
            moonrise = parseMillis(o.str("moonrise")),
            moonset = parseMillis(o.str("moonset")),
            rahuKaal = parseWindow(o.optJSONObject("rahu_kaal")),
            abhijit = parseWindow(o.optJSONObject("abhijit_muhurat")),
            festivals = if (fest == null) emptyList() else (0 until fest.length()).mapNotNull { i ->
                fest.optJSONObject(i)?.let { it.str("name_hi") ?: it.str("name") }
                    ?: fest.optString(i).takeIf { it.isNotBlank() }
            },
        )
    } catch (e: Exception) {
        null
    }
}

private fun parseObservances(text: String?): List<Observance> {
    if (text == null) return emptyList()
    return try {
        val arr = JSONObject(text).optJSONArray("observances") ?: return emptyList()
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            Observance(
                id = o.str("id") ?: name,
                name = name,
                nameHi = o.str("name_hi"),
                type = o.str("type") ?: "tyohar",
                date = o.str("date") ?: "",
                tithi = o.str("tithi") ?: "",
                paksha = o.str("paksha") ?: "",
                description = o.str("description") ?: "",
            )
        }.sortedBy { it.date }
    } catch (e: Exception) {
        emptyList()
    }
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

        assembleFeed(::fetch).copy(offline = offline)
    }
}

/** Merges the feed files (read through [fetch]) into one [Feed], minus anything hidden. */
internal fun assembleFeed(fetch: (String) -> String?): Feed {
    val scraped = fetch("news.json")
    val manualNews = fetch("manual-news.json")
    val eventsFile = fetch("events.json")
    val manualEvents = fetch("manual-events.json")
    val hidden = fetch("hidden.json")
    val panchang = parsePanchang(todaysPanchangJson(fetch("varanasi-panchang-days.json"), fetch("varanasi-panchang-daily.json")))
    val observances = parseObservances(fetch("varanasi-panchang-calendar.json"))

    val hiddenNews = parseHidden(hidden, "news")
    val hiddenEvents = parseHidden(hidden, "events")

    val seen = HashSet<String>()
    val news = (parseNews(manualNews) + parseNews(scraped))
        .filter { it.id !in hiddenNews && seen.add(it.id) }
        .sortedByDescending { it.timestamp }

    val events = (parseEvents(eventsFile) + parseEvents(manualEvents))
        .filter { it.id !in hiddenEvents }
        .sortedBy { it.startMillis }

    return Feed(
        news = news,
        events = events,
        panchang = panchang,
        observances = observances,
    )
}
