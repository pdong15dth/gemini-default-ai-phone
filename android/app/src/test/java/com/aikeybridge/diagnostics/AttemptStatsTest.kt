package com.aikeybridge.diagnostics

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttemptStatsTest {

    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 25, 10, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun daysAgo(days: Int, hour: Int = 9) = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, -days)
        set(Calendar.HOUR_OF_DAY, hour)
    }.timeInMillis

    @Test
    fun emptyHistoryHasNoRateButSevenBuckets() {
        val summary = AttemptStats.summarize(emptyList(), now)

        assertNull(summary.rate)
        assertNull(summary.avgMs)
        assertEquals(7, summary.days.size)
        assertTrue(summary.days.last().today)
        assertEquals("CN", summary.days.last().label)
    }

    @Test
    fun countsRateAverageAndTodayErrors() {
        val attempts = listOf(
            Attempt(daysAgo(0), "a", ok = true, durationMs = 1000, detail = ""),
            Attempt(daysAgo(0, 8), "a", ok = false, durationMs = 2400, detail = "", micFailed = true),
            Attempt(daysAgo(2), "a", ok = true, durationMs = 1200, detail = ""),
            Attempt(daysAgo(1), "a", ok = true, durationMs = 1100, detail = ""),
            Attempt(daysAgo(9), "old", ok = false, durationMs = 9000, detail = ""),
        )

        val summary = AttemptStats.summarize(attempts, now)

        assertEquals(4, summary.total)
        assertEquals(75, summary.rate)
        assertEquals(1100L, summary.avgMs)
        assertEquals(2, summary.today)
        assertEquals(1, summary.failedToday)
        assertEquals(1, summary.micFailed)
        assertEquals(2, summary.days.last().total)
        assertEquals(1, summary.days[5].total)
        assertEquals(1, summary.days[4].total)
    }
}
