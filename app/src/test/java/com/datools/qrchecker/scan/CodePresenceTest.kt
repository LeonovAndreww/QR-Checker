package com.datools.qrchecker.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodePresenceTest {

    private val presence = CodePresence(awayMs = 1_000L)

    @Test
    fun `a code held in front of the camera is answered once`() {
        assertTrue(presence.onFrame(listOf("A"), "A", now = 0))
        for (t in 30L..900L step 30) assertFalse(presence.onFrame(listOf("A"), "A", t))
    }

    @Test
    fun `two codes in view with the pick flipping get one answer each`() {
        var answers = 0
        var t = 0L
        repeat(100) { i ->
            val pick = if (i % 2 == 0) "A" else "B"
            if (presence.onFrame(listOf("A", "B"), pick, t)) answers++
            t += 30
        }
        assertEquals(2, answers)
    }

    @Test
    fun `a neighbour seen all along is still answered when picked for the first time`() {
        presence.onFrame(listOf("A", "B"), "A", now = 0)
        assertTrue(presence.onFrame(listOf("A", "B"), "B", now = 500))
    }

    @Test
    fun `a code taken away and brought back is answered again`() {
        assertTrue(presence.onFrame(listOf("A"), "A", now = 0))
        assertFalse(presence.onFrame(listOf("A"), "A", now = 500))
        assertTrue(presence.onFrame(listOf("A"), "A", now = 1_600))
    }

    @Test
    fun `short detection gaps do not count as taking the code away`() {
        assertTrue(presence.onFrame(listOf("A"), "A", now = 0))
        presence.onFrame(listOf("B"), null, now = 400)
        assertFalse(presence.onFrame(listOf("A"), "A", now = 900))
    }

    @Test
    fun `seen outside the viewfinder keeps the code present`() {
        assertTrue(presence.onFrame(listOf("A"), "A", now = 0))
        presence.onFrame(listOf("A"), null, now = 800)
        assertFalse(presence.onFrame(listOf("A"), "A", now = 1_500))
    }

    @Test
    fun `forgetting answers re-asks about codes still in view`() {
        assertTrue(presence.onFrame(listOf("A"), "A", now = 0))
        presence.forgetAnswers()
        assertTrue(presence.onFrame(listOf("A"), "A", now = 100))
    }

    @Test
    fun `presence follows the last sighting of an answered code`() {
        assertFalse(presence.isPresent("A", now = 0))
        presence.onFrame(listOf("A"), "A", now = 0)
        assertTrue(presence.isPresent("A", now = 999))
        assertFalse(presence.isPresent("A", now = 1_000))
        presence.onFrame(listOf("B"), null, now = 0)
        assertFalse(presence.isPresent("B", now = 10))
    }
}
