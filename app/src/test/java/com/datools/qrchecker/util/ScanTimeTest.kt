package com.datools.qrchecker.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Календарные сутки между двумя моментами.
 *
 * Сутки перевода часов длятся 23 или 25 часов, и разница полуночей, просто обрезанная до
 * суток, весной делала «вчера» сегодняшним днём.
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
    fun `весенний перевод часов не съедает вчерашний день`() {
        // 29 марта 2026 в Берлине часы переводят вперёд: в этих сутках 23 часа
        assertEquals(1, daysBetween(at(2026, 3, 29, 10), at(2026, 3, 30, 9)))
    }

    @Test
    fun `осенний перевод часов не добавляет лишний день`() {
        // 25 октября 2026 - назад, в сутках 25 часов
        assertEquals(1, daysBetween(at(2026, 10, 25, 10), at(2026, 10, 26, 9)))
    }

    @Test
    fun `время в выгрузке не путается, когда её собирают в нескольких потоках`() {
        // SimpleDateFormat хранит дату в себе: один общий на все потоки путал время
        // соседних строк
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
    fun `обычные сутки считаются как раньше`() {
        assertEquals(0, daysBetween(at(2026, 6, 10, 1), at(2026, 6, 10, 23)))
        assertEquals(7, daysBetween(at(2026, 3, 25, 12), at(2026, 4, 1, 12)))
    }
}
