package com.datools.qrchecker.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Кривая сопротивления свайпа.
 *
 * Проверяется не «красиво ли», а три свойства, без которых жест ломается: плашка не
 * уезжает за отведённые ей пределы, идёт в ту же сторону, что и палец, и с самого начала
 * не отстаёт от него заметно.
 */
class SwipeActionsTest {

    private val limit = 420f

    @Test
    fun `плашка не уходит дальше предела, как её ни тяни`() {
        for (raw in listOf(100f, 500f, 2_000f, 100_000f)) {
            assertTrue("сдвиг $raw", resistedOffset(raw, limit) <= limit)
            assertTrue("сдвиг -$raw", resistedOffset(-raw, limit) >= -limit)
        }
    }

    @Test
    fun `плашка идёт туда же, куда палец`() {
        assertTrue(resistedOffset(50f, limit) > 0f)
        assertTrue(resistedOffset(-50f, limit) < 0f)
        assertEquals(0f, resistedOffset(0f, limit), 0f)
    }

    @Test
    fun `в начале движения плашка держится за палец`() {
        // первые десятки пикселей - это ещё не «тянем», а обычное касание: отставание
        // здесь читалось бы как подтормаживание
        val raw = 20f
        assertEquals(raw, resistedOffset(raw, limit), raw * 0.05f)
    }

    @Test
    fun `чем дальше, тем туже`() {
        val steps = (0..9).map { resistedOffset(it * 60f, limit) }
        val gaps = steps.zipWithNext { a, b -> b - a }
        for ((before, after) in gaps.zipWithNext()) {
            assertTrue("шаг $after не меньше предыдущего $before", after < before)
        }
    }

    @Test
    fun `порог достижим - до него плашку дотянуть можно`() {
        // порог берётся от ширины, предел - тоже; важно, что первый лежит внутри второго,
        // иначе действие не вызвать вообще никаким усилием
        val width = 1000f
        val trigger = width * 0.22f
        val reach = resistedOffset(width, width * 0.42f)
        assertTrue("дотягивается до $reach, а нужно $trigger", reach > trigger)
    }

    @Test
    fun `нулевая ширина не роняет расчёт`() {
        assertEquals(0f, resistedOffset(100f, 0f), 0f)
        assertTrue(abs(resistedOffset(-100f, 0f)) == 0f)
    }
}
