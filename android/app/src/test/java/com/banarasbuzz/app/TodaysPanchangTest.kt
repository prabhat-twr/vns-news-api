package com.banarasbuzz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The app must show today's panchang from the 8-day file, not a stale single-day file. */
class TodaysPanchangTest {
    private val days = """{"days":[{"date":"2026-11-05","vara":"Thursday"},{"date":"2026-11-06","vara":"Friday"}]}"""
    private val daily = """{"date":"2026-08-26","vara":"Wednesday"}"""

    @Test
    fun picksTodayFromDaysFile() {
        assertEquals("2026-11-06", todaysPanchangJson(days, daily, today = "2026-11-06")!!.getString("date"))
    }

    @Test
    fun fallsBackToDailyFileWhenTodayMissing() {
        assertEquals("2026-08-26", todaysPanchangJson(days, daily, today = "2026-12-01")!!.getString("date"))
        assertEquals("2026-08-26", todaysPanchangJson(null, daily, today = "2026-11-05")!!.getString("date"))
    }

    @Test
    fun survivesBrokenFiles() {
        assertEquals("2026-08-26", todaysPanchangJson("not json", daily, today = "2026-11-05")!!.getString("date"))
        assertNull(todaysPanchangJson(null, "also not json", today = "2026-11-05"))
    }
}
