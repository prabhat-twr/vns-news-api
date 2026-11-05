package com.banarasbuzz.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The phone-side sunrise maths behind the Rahu Kaal / Shubh Muhurat alerts, checked against
 * scripts/build_panchang.py (JPL ephemeris via Skyfield) for Varanasi across the year.
 */
class SolarDayTest {
    private fun at(date: String, time: String): Long =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), IST).toInstant().toEpochMilli()

    private fun assertNear(label: String, expected: Long, actual: Long, toleranceSec: Long = 90) {
        val diff = (actual - expected) / 1000
        assertTrue("$label off by ${diff}s (got ${clock(actual)})", kotlin.math.abs(diff) <= toleranceSec)
    }

    @Test
    fun sunriseAndSunsetMatchEphemeris() {
        val reference = listOf(
            Triple("2026-01-15", "06:45:31", "17:29:29"),
            Triple("2026-03-20", "06:02:26", "18:09:08"),
            Triple("2026-06-21", "05:08:22", "18:51:22"),
            Triple("2026-08-26", "05:36:14", "18:23:22"),
            Triple("2026-11-05", "06:08:13", "17:14:50"),
            Triple("2027-12-31", "06:43:17", "17:18:29"),
        )
        for ((d, rise, set) in reference) {
            val date = LocalDate.parse(d)
            assertNear("$d sunrise", at(d, rise), SolarDay.event(date, rising = true))
            assertNear("$d sunset", at(d, set), SolarDay.event(date, rising = false))
        }
    }

    @Test
    fun windowsMatchPanchangFile() {
        // varanasi-panchang-daily.json for Thursday 5 Nov 2026.
        val w = computeWindows(LocalDate.parse("2026-11-05"))
        assertNear("rahu start", at("2026-11-05", "13:04:00"), w.rahu.start, 120)
        assertNear("rahu end", at("2026-11-05", "14:28:00"), w.rahu.end, 120)
        assertNear("abhijit start", at("2026-11-05", "11:19:00"), w.abhijit.start, 120)
        assertNear("abhijit end", at("2026-11-05", "12:03:00"), w.abhijit.end, 120)
    }

    @Test
    fun rahuKaalSegmentFollowsWeekday() {
        // Wednesday 26 Aug 2026: Rahu Kaal is the 5th eighth of the day (published 11:59–13:35).
        val w = computeWindows(LocalDate.parse("2026-08-26"))
        assertNear("rahu start", at("2026-08-26", "11:59:00"), w.rahu.start, 120)
        assertNear("rahu end", at("2026-08-26", "13:35:00"), w.rahu.end, 120)
    }
}
