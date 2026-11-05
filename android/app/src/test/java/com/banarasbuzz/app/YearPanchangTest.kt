package com.banarasbuzz.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Reads the real varanasi-panchang-year.json from the repo root. */
class YearPanchangTest {
    private val year = parseYear(File("../../varanasi-panchang-year.json").readText())!!

    @Test
    fun coversAFullYearDayByDay() {
        assertTrue("got ${year.days.size} days", year.days.size >= 365)
        val start = LocalDate.parse(year.start)
        for (i in 0 until 365) {
            val key = start.plusDays(i.toLong()).toString()
            val p = year.days[key]
            assertNotNull("missing $key", p)
            assertTrue("$key has no tithi", p!!.tithi.isNotEmpty())
            assertTrue("$key sunrise after sunset", p.sunrise < p.sunset)
            val r = p.rahuKaal!!
            assertTrue("$key rahu kaal outside the day", r.start >= p.sunrise && r.end <= p.sunset + 60_000)
        }
    }

    @Test
    fun hasTheYearsFestivals() {
        assertTrue("got ${year.observances.size} observances", year.observances.size > 100)
        val names = year.observances.map { it.name }
        listOf("Diwali (Lakshmi Puja)", "Dev Deepawali", "Maha Shivaratri", "Holi", "Ram Navami", "Makar Sankranti")
            .forEach { assertTrue("missing $it", it in names) }
        assertTrue(year.observances.all { it.date in year.start..year.end })
    }

    @Test
    fun brokenFileGivesNull() {
        assertNull(parseYear("{"))
        assertNull(parseYear(null))
        assertEquals(null, parseYear("""{"observances":[]}"""))
    }
}
