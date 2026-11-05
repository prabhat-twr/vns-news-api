package com.banarasbuzz.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val IST: ZoneId = ZoneId.of("Asia/Kolkata")

private val tithiHi = mapOf(
    "pratipada" to "प्रतिपदा", "dwitiya" to "द्वितीया", "dvitiya" to "द्वितीया", "tritiya" to "तृतीया",
    "chaturthi" to "चतुर्थी", "panchami" to "पंचमी", "shashthi" to "षष्ठी", "shashti" to "षष्ठी",
    "saptami" to "सप्तमी", "ashtami" to "अष्टमी", "navami" to "नवमी", "dashami" to "दशमी",
    "ekadashi" to "एकादशी", "dwadashi" to "द्वादशी", "dvadashi" to "द्वादशी", "trayodashi" to "त्रयोदशी",
    "chaturdashi" to "चतुर्दशी", "purnima" to "पूर्णिमा", "amavasya" to "अमावस्या",
)

private val nakshatraHi = mapOf(
    "ashwini" to "अश्विनी", "bharani" to "भरणी", "krittika" to "कृत्तिका", "rohini" to "रोहिणी",
    "mrigashira" to "मृगशिरा", "mrigashirsha" to "मृगशिरा", "ardra" to "आर्द्रा", "punarvasu" to "पुनर्वसु",
    "pushya" to "पुष्य", "ashlesha" to "आश्लेषा", "magha" to "मघा", "purva phalguni" to "पूर्वा फाल्गुनी",
    "uttara phalguni" to "उत्तरा फाल्गुनी", "hasta" to "हस्त", "chitra" to "चित्रा", "swati" to "स्वाती",
    "vishakha" to "विशाखा", "anuradha" to "अनुराधा", "jyeshtha" to "ज्येष्ठा", "mula" to "मूल",
    "moola" to "मूल", "purva ashadha" to "पूर्वाषाढ़ा", "purvashadha" to "पूर्वाषाढ़ा",
    "uttara ashadha" to "उत्तराषाढ़ा", "uttarashadha" to "उत्तराषाढ़ा", "shravana" to "श्रवण",
    "dhanishta" to "धनिष्ठा", "dhanishtha" to "धनिष्ठा", "shatabhisha" to "शतभिषा",
    "purva bhadrapada" to "पूर्वा भाद्रपद", "uttara bhadrapada" to "उत्तरा भाद्रपद", "revati" to "रेवती",
)

private val weekdayHi = listOf("सोमवार", "मंगलवार", "बुधवार", "गुरुवार", "शुक्रवार", "शनिवार", "रविवार")
private val monthHi = listOf(
    "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
    "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
)

fun tithiInHindi(name: String) = tithiHi[name.trim().lowercase()] ?: name
fun nakshatraInHindi(name: String) = nakshatraHi[name.trim().lowercase()] ?: name
fun pakshaInHindi(p: String?) = when (p?.trim()?.lowercase()) {
    "shukla" -> "शुक्ल पक्ष"
    "krishna" -> "कृष्ण पक्ष"
    null, "" -> ""
    else -> p
}

/** "गुरुवार, 6 नवंबर 2026" */
fun hindiLongDate(date: LocalDate): String =
    "${weekdayHi[date.dayOfWeek.value - 1]}, ${date.dayOfMonth} ${monthHi[date.monthValue - 1]} ${date.year}"

/** "6 नवंबर" */
fun hindiShortDate(date: LocalDate): String = "${date.dayOfMonth} ${monthHi[date.monthValue - 1]}"

fun parseIsoDate(s: String): LocalDate? = try {
    LocalDate.parse(s)
} catch (e: Exception) {
    null
}

private val clockFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
private val clockShortFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** "06:35 AM" in IST; empty for 0. */
fun clock(millis: Long): String =
    if (millis == 0L) "" else clockFmt.format(Instant.ofEpochMilli(millis).atZone(IST))

fun clockShort(millis: Long): String =
    if (millis == 0L) "" else clockShortFmt.format(Instant.ofEpochMilli(millis).atZone(IST))

fun todayIst(): LocalDate = LocalDate.now(IST)

/** "2 घंटे पहले" style relative time. */
fun timeAgo(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis == 0L) return ""
    val mins = (now - millis) / 60_000
    return when {
        mins < 1 -> "अभी"
        mins < 60 -> "$mins मिनट पहले"
        mins < 60 * 24 -> "${mins / 60} घंटे पहले"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)} दिन पहले"
        else -> hindiShortDate(Instant.ofEpochMilli(millis).atZone(IST).toLocalDate())
    }
}

/** Greeting for the home banner, by hour in Varanasi. */
fun greeting(): String {
    val hour = java.time.LocalTime.now(IST).hour
    return when (hour) {
        in 4..11 -> "शुभ प्रभात, बनारस!"
        in 12..16 -> "नमस्ते, बनारस!"
        in 17..20 -> "शुभ संध्या, बनारस!"
        else -> "शुभ रात्रि, बनारस!"
    }
}

fun greetingScene(): Scene = when (java.time.LocalTime.now(IST).hour) {
    in 4..11 -> Scene.DAWN
    in 12..18 -> Scene.DUSK
    else -> Scene.NIGHT
}
