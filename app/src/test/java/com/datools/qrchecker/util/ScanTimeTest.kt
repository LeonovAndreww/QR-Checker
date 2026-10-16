package com.datools.qrchecker.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Calendar days between two moments. A DST day lasts 23 or 25 hours, and a truncated
 * difference of midnights turned a spring "yesterday" into today.
 */
class ScanTimeTest {

    private lateinit var saved: TimeZone

    @Before
    fun setUp() {
        saved = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(saved)
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0)
        }.timeInMillis

    @Test
    fun `spring DST change does not eat yesterday`() {
        // March 29, 2026 in Berlin clocks go forward: that day has 23 hours
        assertEquals(1, daysBetween(at(2026, 3, 29, 10), at(2026, 3, 30, 9)))
    }

    @Test
    fun `autumn DST change does not add a day`() {
        // October 25, 2026 clocks go back: that day has 25 hours
        assertEquals(1, daysBetween(at(2026, 10, 25, 10), at(2026, 10, 26, 9)))
    }

    @Test
    fun `export times do not mix up when built on several threads`() {
        // SimpleDateFormat keeps state inside; a shared one mixed up neighbouring rows
        val failures = java.util.concurrent.atomic.AtomicInteger()
        val threads = (1..4).map { t ->
            Thread {
                val own = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                repeat(5_000) { i ->
                    val millis = 1_600_000_000_000L + t * 1_000_000_007L + i * 86_400_123L
                    if (formatScanTimeForExport(millis) != own.format(java.util.Date(millis))) {
                        failures.incrementAndGet()
                    }
                }
            }.apply { start() }
        }
        threads.forEach { it.join() }
        assertEquals(0, failures.get())
    }

    @Test
    fun `ordinary days count as before`() {
        assertEquals(0, daysBetween(at(2026, 6, 10, 1), at(2026, 6, 10, 23)))
        assertEquals(7, daysBetween(at(2026, 3, 25, 12), at(2026, 4, 1, 12)))
    }
}
