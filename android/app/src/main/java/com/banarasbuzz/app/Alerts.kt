package com.banarasbuzz.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Sunrise and sunset for Varanasi, worked out on the phone (NOAA solar equations, accurate to
 * about a minute), so Rahu Kaal and Abhijit alerts work for any day, even offline.
 */
object SolarDay {
    private const val LAT = 25.3176
    private const val LNG = 82.9739
    private const val ALTITUDE = -0.833 // upper limb at the horizon, with refraction

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)

    /** Solar declination (degrees) and equation of time (minutes) at an instant. */
    private fun sun(epochMillis: Long): Pair<Double, Double> {
        val jd = epochMillis / 86_400_000.0 + 2440587.5
        val t = (jd - 2451545.0) / 36525.0
        val l0 = (280.46646 + t * (36000.76983 + t * 0.0003032)) % 360.0
        val m = 357.52911 + t * (35999.05029 - 0.0001537 * t)
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
        val c = sin(rad(m)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(rad(2 * m)) * (0.019993 - 0.000101 * t) + sin(rad(3 * m)) * 0.000289
        val omega = 125.04 - 1934.136 * t
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(rad(omega))
        val eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
        val eps = eps0 + 0.00256 * cos(rad(omega))
        val dec = deg(asin(sin(rad(eps)) * sin(rad(lambda))))
        val y = tan(rad(eps / 2)).let { it * it }
        val eot = 4 * deg(
            y * sin(2 * rad(l0)) - 2 * e * sin(rad(m)) + 4 * e * y * sin(rad(m)) * cos(2 * rad(l0)) -
                0.5 * y * y * sin(4 * rad(l0)) - 1.25 * e * e * sin(2 * rad(m))
        )
        return dec to eot
    }

    /** Sunrise (rising = true) or sunset on [date] in IST, as epoch millis. */
    fun event(date: LocalDate, rising: Boolean): Long {
        val midnightUtc = date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        var minutes = if (rising) 0.0 else 720.0 // first guess, minutes after 00:00 UTC
        repeat(4) {
            val (dec, eot) = sun(midnightUtc + (minutes * 60_000).toLong())
            val cosH = (sin(rad(ALTITUDE)) - sin(rad(LAT)) * sin(rad(dec))) / (cos(rad(LAT)) * cos(rad(dec)))
            val h = deg(acos(cosH.coerceIn(-1.0, 1.0)))
            val noon = 720 - 4 * LNG - eot
            minutes = if (rising) noon - 4 * h else noon + 4 * h
        }
        return midnightUtc + (minutes * 60_000).toLong()
    }
}

enum class AlertKind(val id: String, val channel: String, val channelName: String) {
    RAHU("rahu", "rahu_kaal", "राहुकाल"),
    SHUBH("shubh", "shubh_muhurat", "शुभ मुहूर्त (अभिजित)"),
}

/** Rahu Kaal and Abhijit Muhurat for one day. */
data class DayWindows(val date: LocalDate, val rahu: TimeWindow, val abhijit: TimeWindow) {
    fun of(kind: AlertKind) = if (kind == AlertKind.RAHU) rahu else abhijit
}

// Which eighth of the daytime Rahu Kaal occupies, Monday first.
private val RAHU_PART = intArrayOf(2, 7, 5, 6, 4, 3, 8)

fun computeWindows(date: LocalDate): DayWindows {
    val rise = SolarDay.event(date, rising = true)
    val set = SolarDay.event(date, rising = false)
    val part = (set - rise) / 8
    val rahuStart = rise + part * (RAHU_PART[date.dayOfWeek.value - 1] - 1)
    val muhurta = (set - rise) / 15
    val abhijitStart = rise + muhurta * 7
    return DayWindows(date, TimeWindow(rahuStart, rahuStart + part), TimeWindow(abhijitStart, abhijitStart + muhurta))
}

object AlertPrefs {
    private fun p(c: Context) = c.getSharedPreferences("alerts", Context.MODE_PRIVATE)
    fun enabled(c: Context, k: AlertKind) = p(c).getBoolean(k.id, false)
    fun setEnabled(c: Context, k: AlertKind, on: Boolean) = p(c).edit().putBoolean(k.id, on).apply()
    fun early(c: Context) = p(c).getBoolean("early", true)
    fun setEarly(c: Context, on: Boolean) = p(c).edit().putBoolean("early", on).apply()
    fun anyEnabled(c: Context) = AlertKind.entries.any { enabled(c, it) }
}

object PanchangAlerts {
    const val EARLY_MINUTES = 10L
    private const val EXTRA_KIND = "kind"
    private const val EXTRA_EARLY = "early"
    const val EXTRA_ROUTE = "route"

    /** Today's windows: the panchang file's own times when we have today's copy, else calculated. */
    fun windows(context: Context, date: LocalDate): DayWindows {
        val calculated = computeWindows(date)
        return try {
            val file = File(context.cacheDir, "varanasi-panchang-daily.json")
            if (!file.exists()) return calculated
            val o = JSONObject(file.readText())
            if (o.optString("date") != date.toString()) return calculated
            fun window(key: String): TimeWindow? {
                val w = o.optJSONObject(key) ?: return null
                val s = ZonedDateTime.parse(w.getString("start")).toInstant().toEpochMilli()
                val e = ZonedDateTime.parse(w.getString("end")).toInstant().toEpochMilli()
                return if (e > s) TimeWindow(s, e) else null
            }
            DayWindows(date, window("rahu_kaal") ?: calculated.rahu, window("abhijit_muhurat") ?: calculated.abhijit)
        } catch (e: Exception) {
            calculated
        }
    }

    /** Abhijit Muhurat is traditionally not observed on Wednesdays. */
    private fun observed(kind: AlertKind, date: LocalDate) =
        !(kind == AlertKind.SHUBH && date.dayOfWeek == java.time.DayOfWeek.WEDNESDAY)

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pending(context: Context, kind: AlertKind, early: Boolean): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java)
            .setAction("com.banarasbuzz.app.ALERT_${kind.id}_${if (early) "early" else "start"}")
            .putExtra(EXTRA_KIND, kind.id)
            .putExtra(EXTRA_EARLY, early)
        val code = kind.ordinal * 2 + if (early) 1 else 0
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /**
     * Sets the next alarm for each enabled alert (and its 10-minute heads-up). With [postRunning],
     * also shows the alert right away if that period is already under way (used when the user
     * switches an alert on). Safe to call any time.
     */
    fun schedule(context: Context, postRunning: Boolean = false) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        val today = todayIst()
        for (kind in AlertKind.entries) {
            for (early in listOf(false, true)) alarms.cancel(pending(context, kind, early))
            if (!AlertPrefs.enabled(context, kind)) {
                NotificationManagerCompat.from(context).cancel(kind.ordinal)
                continue
            }
            val days = (0..7).map { today.plusDays(it.toLong()) }.filter { observed(kind, it) }
            val current = days.firstOrNull { it == today }?.let { windows(context, it).of(kind) }
            if (postRunning && current != null && now in current.start until current.end) {
                notify(context, kind, early = false, current)
            }
            val next = days.map { windows(context, it).of(kind) }.firstOrNull { it.start > now } ?: continue
            setAlarm(alarms, context, next.start, pending(context, kind, early = false))
            val earlyAt = next.start - EARLY_MINUTES * 60_000
            if (AlertPrefs.early(context) && earlyAt > now) {
                setAlarm(alarms, context, earlyAt, pending(context, kind, early = true))
            }
        }
    }

    private fun setAlarm(alarms: AlarmManager, context: Context, at: Long, pi: PendingIntent) {
        try {
            if (canScheduleExact(context)) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // Without the exact-alarm permission Android may deliver this a few minutes late.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(AlertKind.RAHU.channel, AlertKind.RAHU.channelName, NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "राहुकाल शुरू होने पर सूचना" }
        )
        nm.createNotificationChannel(
            NotificationChannel(AlertKind.SHUBH.channel, AlertKind.SHUBH.channelName, NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "अभिजित मुहूर्त शुरू होने पर सूचना" }
        )
    }

    /** Posts the alert. It counts down live in the notification shade and removes itself when the period ends. */
    fun notify(context: Context, kind: AlertKind, early: Boolean, w: TimeWindow) {
        if (!canNotify(context)) return
        createChannels(context)
        val now = System.currentTimeMillis()
        val until = if (early) w.start else w.end
        if (until <= now) return
        val range = "${clockShort(w.start)} – ${clockShort(w.end)}"
        val (title, text) = when (kind) {
            AlertKind.RAHU -> if (early) "राहुकाल 10 मिनट में शुरू होगा" to "$range • नए काम की शुरुआत टालें"
            else "राहुकाल चल रहा है — सावधान रहें" to "$range • नए और शुभ काम अभी शुरू न करें"
            AlertKind.SHUBH -> if (early) "शुभ मुहूर्त 10 मिनट में" to "अभिजित मुहूर्त $range"
            else "शुभ मुहूर्त चल रहा है ✨" to "अभिजित मुहूर्त $range • शुभ काम के लिए अच्छा समय"
        }
        val open = PendingIntent.getActivity(
            context, 100 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ROUTE, Route.PANCHANG.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, kind.channel)
            .setSmallIcon(if (kind == AlertKind.RAHU) R.drawable.ic_stat_rahu else R.drawable.ic_stat_shubh)
            .setColor(if (kind == AlertKind.RAHU) 0xFFB3261E.toInt() else 0xFFE8600F.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(if (early) "शुरू होने में" else "समाप्त होने में")
            .setWhen(until)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(until - now)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(kind.ordinal, n)
        } catch (e: SecurityException) {
            // Permission was withdrawn after the check.
        }
    }

    fun handle(context: Context, intent: Intent) {
        val kind = AlertKind.entries.firstOrNull { it.id == intent.getStringExtra(EXTRA_KIND) }
        if (kind != null && AlertPrefs.enabled(context, kind)) {
            val early = intent.getBooleanExtra(EXTRA_EARLY, false)
            val now = System.currentTimeMillis()
            val today = todayIst()
            // The window this alarm was set for: the next one starting after now (heads-up),
            // or the one running now (start alarm, possibly delivered a little late).
            val w = (0..1).map { windows(context, today.plusDays(it.toLong())).of(kind) }
                .firstOrNull { if (early) it.start > now else now < it.end }
            if (w != null) notify(context, kind, early, w)
        }
        schedule(context)
    }
}

/** Fires at each alert time; also re-arms the alarms after a reboot, app update or clock change. */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> PanchangAlerts.schedule(context)
            else -> PanchangAlerts.handle(context, intent)
        }
    }
}
