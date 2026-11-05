package com.banarasbuzz.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * "Today's panchang" pinned in the notification bar and on the lock screen: the current or next
 * Rahu Kaal / Abhijit Muhurat with a live countdown, plus tithi, nakshatra, sunrise and today's
 * vrat. It refreshes itself at every start and end of those periods and at midnight.
 */
object StickyPanchang {
    private const val NOTIFICATION_ID = 10
    private const val CHANNEL = "panchang_today"
    const val ACTION_UPDATE = "com.banarasbuzz.app.STICKY_UPDATE"

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        // Default importance so it shows on the lock screen; no sound or vibration since it updates often.
        val ch = NotificationChannel(CHANNEL, "आज का पंचांग (स्थायी)", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "नोटिफिकेशन बार और लॉक स्क्रीन पर आज का राहुकाल, शुभ मुहूर्त और तिथि"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 50,
        Intent(context, AlertReceiver::class.java).setAction(ACTION_UPDATE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private class Today(
        val tithi: String?,
        val nakshatra: String?,
        val sunrise: Long,
        val sunset: Long,
        val festivals: List<String>,
    )

    /** Tithi, nakshatra and today's vrat from the cached feed files, if they are for today. */
    private fun today(context: Context, date: LocalDate): Today {
        var tithi: String? = null
        var nakshatra: String? = null
        var sunrise = SolarDay.event(date, rising = true)
        var sunset = SolarDay.event(date, rising = false)
        val festivals = mutableListOf<String>()
        try {
            val o = cachedPanchangJson(context)
            if (o != null) {
                if (o.optString("date") == date.toString()) {
                    o.optJSONArray("tithi")?.optJSONObject(0)?.let { t ->
                        val name = t.optString("name_hi").ifBlank { tithiInHindi(t.optString("name")) }
                        tithi = listOf(pakshaInHindi(t.optString("paksha")).removeSuffix(" पक्ष"), name)
                            .filter { it.isNotBlank() }.joinToString(" ")
                    }
                    o.optJSONArray("nakshatra")?.optJSONObject(0)?.let { n ->
                        nakshatra = n.optString("name_hi").ifBlank { nakshatraInHindi(n.optString("name")) }
                    }
                    o.optString("sunrise").takeIf { it.isNotBlank() }
                        ?.let { sunrise = ZonedDateTime.parse(it).toInstant().toEpochMilli() }
                    o.optString("sunset").takeIf { it.isNotBlank() }
                        ?.let { sunset = ZonedDateTime.parse(it).toInstant().toEpochMilli() }
                }
            }
            val c = File(context.cacheDir, "varanasi-panchang-calendar.json")
            if (c.exists()) {
                val arr = JSONObject(c.readText()).optJSONArray("observances")
                if (arr != null) for (i in 0 until arr.length()) {
                    val ob = arr.optJSONObject(i) ?: continue
                    if (ob.optString("date") == date.toString()) {
                        festivals += ob.optString("name_hi").ifBlank { ob.optString("name") }
                    }
                }
            }
        } catch (e: Exception) {
            // Fall back to the calculated sunrise/sunset only.
        }
        return Today(tithi, nakshatra, sunrise, sunset, festivals.distinct())
    }

    const val ACTION_RESTORE = "com.banarasbuzz.app.STICKY_RESTORE"
    const val ACTION_TURN_OFF = "com.banarasbuzz.app.STICKY_TURN_OFF"

    /** Everything the notification and the home-screen widget show, worked out once. */
    class Status(
        val date: LocalDate,
        val title: String,
        val line2: String,
        val details: String,
        val countdownTo: Long?,
        val countdownLabel: String?,
        val inRahu: Boolean,
        val inShubh: Boolean,
        val rahuRange: String,
        val shubhRange: String?,
        val nextRefresh: Long,
    )

    fun status(context: Context, now: Long = System.currentTimeMillis()): Status {
        val date = todayIst()
        val w = PanchangAlerts.windows(context, date)
        val shubhToday = date.dayOfWeek != DayOfWeek.WEDNESDAY
        val info = today(context, date)
        val midnight = date.plusDays(1).atStartOfDay(IST).toInstant().toEpochMilli()

        val inRahu = now in w.rahu.start until w.rahu.end
        val inShubh = shubhToday && now in w.abhijit.start until w.abhijit.end
        val rahuRange = "${clockShort(w.rahu.start)} – ${clockShort(w.rahu.end)}"
        val shubhRange = "${clockShort(w.abhijit.start)} – ${clockShort(w.abhijit.end)}"

        // Headline: what is happening now, or what comes next today.
        val upcoming = listOfNotNull(
            ("राहुकाल" to w.rahu).takeIf { w.rahu.start > now },
            ("शुभ मुहूर्त" to w.abhijit).takeIf { shubhToday && w.abhijit.start > now },
        ).minByOrNull { it.second.start }
        val title: String
        var countdownTo: Long? = null
        var label: String? = null
        when {
            inRahu -> { title = "🌑 राहुकाल चल रहा है · ${clockShort(w.rahu.end)} तक"; countdownTo = w.rahu.end; label = "समाप्त होने में" }
            inShubh -> { title = "✨ शुभ मुहूर्त चल रहा है · ${clockShort(w.abhijit.end)} तक"; countdownTo = w.abhijit.end; label = "समाप्त होने में" }
            upcoming != null -> {
                title = "आगे: ${upcoming.first} ${clockShort(upcoming.second.start)} से"
                countdownTo = upcoming.second.start
                label = "शुरू होने में"
            }
            else -> {
                val tomorrow = PanchangAlerts.windows(context, date.plusDays(1))
                title = "आज के राहुकाल व शुभ मुहूर्त बीत चुके · कल राहुकाल ${clockShort(tomorrow.rahu.start)}"
            }
        }

        val line2 = listOfNotNull(info.tithi, info.nakshatra).joinToString(" · ")
            .ifBlank { "सूर्योदय ${clockShort(info.sunrise)} · सूर्यास्त ${clockShort(info.sunset)}" }
        val details = buildList {
            if (info.tithi != null) add("तिथि: ${info.tithi}")
            if (info.nakshatra != null) add("नक्षत्र: ${info.nakshatra}")
            add("राहुकाल: $rahuRange")
            add(if (shubhToday) "शुभ (अभिजित): $shubhRange" else "शुभ (अभिजित): बुधवार को नहीं")
            add("सूर्योदय ${clockShort(info.sunrise)} · सूर्यास्त ${clockShort(info.sunset)}")
            if (info.festivals.isNotEmpty()) add("आज: ${info.festivals.joinToString(", ")}")
        }.joinToString("\n")

        val next = listOf(w.rahu.start, w.rahu.end, w.abhijit.start, w.abhijit.end, midnight)
            .filter { it > now }.minOrNull() ?: midnight
        return Status(date, title, line2, details, countdownTo, label, inRahu, inShubh, rahuRange,
            if (shubhToday) shubhRange else null, next)
    }

    fun openPanchang(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java)
            .putExtra(PanchangAlerts.EXTRA_ROUTE, Route.PANCHANG.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code,
        Intent(context, AlertReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Refreshes the pinned notification (or removes it when switched off) and any home-screen
     * widgets, then arms the next refresh for as long as either is in use.
     */
    fun update(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarm(context))
        val st = status(context)
        val widgets = PanchangWidget.update(context, st)
        val pinned = postNotification(context, st)
        if (pinned || widgets) {
            // Refresh again at the next start/end, or at midnight for the new day.
            PanchangAlerts.setAlarm(alarms, context, st.nextRefresh + 1_000, alarm(context))
        }
    }

    /** Swiping the pinned notification away brings it straight back; "बंद करें" is the way to remove it. */
    fun turnOff(context: Context) {
        AlertPrefs.setSticky(context, false)
        update(context)
    }

    private fun postNotification(context: Context, st: Status): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!AlertPrefs.sticky(context) || !PanchangAlerts.canNotify(context)) {
            nm.cancel(NOTIFICATION_ID)
            return false
        }
        createChannel(context)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(if (st.inRahu) R.drawable.ic_stat_rahu else R.drawable.ic_stat_shubh)
            .setColor(if (st.inRahu) 0xFFB3261E.toInt() else 0xFFE8600F.toInt())
            .setContentTitle(st.title)
            .setContentText(st.line2)
            .setStyle(NotificationCompat.BigTextStyle().bigText(st.details))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPanchang(context, 110))
            // Android 13+ lets users swipe away even ongoing notifications; put it straight back.
            .setDeleteIntent(broadcast(context, ACTION_RESTORE, 51))
            .addAction(0, "बंद करें", broadcast(context, ACTION_TURN_OFF, 52))
        if (st.countdownTo != null) {
            b.setWhen(st.countdownTo).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
                .setSubText(st.countdownLabel)
        } else {
            b.setShowWhen(false)
        }
        return try {
            nm.notify(NOTIFICATION_ID, b.build())
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
