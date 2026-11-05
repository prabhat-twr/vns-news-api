package com.banarasbuzz.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/**
 * Home-screen widget: today's Rahu Kaal / Shubh Muhurat status with a live countdown, tithi and
 * nakshatra. Unlike a notification it cannot be swiped away. It is refreshed together with the
 * pinned notification (StickyPanchang) at every period start/end and at midnight.
 */
class PanchangWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = StickyPanchang.update(context)
    override fun onEnabled(context: Context) = StickyPanchang.update(context)
    override fun onDisabled(context: Context) = StickyPanchang.update(context)

    companion object {
        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PanchangWidget::class.java))

        fun hasWidgets(context: Context) = ids(context).isNotEmpty()

        /** Asks the launcher to add the widget to the home screen (Android 8+ launchers that support it). */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, PanchangWidget::class.java), null, null)
        }

        /** Redraws every placed widget. Returns false when none are placed. */
        fun update(context: Context, st: StickyPanchang.Status): Boolean {
            val ids = ids(context)
            if (ids.isEmpty()) return false
            val v = RemoteViews(context.packageName, R.layout.widget_panchang)
            v.setInt(
                R.id.widget_root, "setBackgroundResource",
                when {
                    st.inRahu -> R.drawable.widget_bg_rahu
                    st.inShubh -> R.drawable.widget_bg_shubh
                    else -> R.drawable.widget_bg
                },
            )
            v.setTextViewText(R.id.widget_date, hindiShortDate(st.date))
            v.setTextViewText(R.id.widget_title, st.title)
            v.setTextViewText(R.id.widget_line2, st.line2)
            v.setTextViewText(R.id.widget_rahu, "🌑 राहुकाल\n${st.rahuRange}")
            v.setTextViewText(R.id.widget_shubh, "✨ शुभ (अभिजित)\n${st.shubhRange ?: "बुधवार को नहीं"}")
            if (st.countdownTo != null) {
                val base = SystemClock.elapsedRealtime() + (st.countdownTo - System.currentTimeMillis())
                v.setChronometer(R.id.widget_countdown, base, null, true)
                v.setChronometerCountDown(R.id.widget_countdown, true)
                v.setTextViewText(R.id.widget_countdown_label, st.countdownLabel ?: "")
                v.setViewVisibility(R.id.widget_countdown_row, View.VISIBLE)
            } else {
                v.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)
                v.setViewVisibility(R.id.widget_countdown_row, View.GONE)
            }
            v.setOnClickPendingIntent(R.id.widget_root, StickyPanchang.openPanchang(context, 120))
            AppWidgetManager.getInstance(context).updateAppWidget(ids, v)
            return true
        }
    }
}
