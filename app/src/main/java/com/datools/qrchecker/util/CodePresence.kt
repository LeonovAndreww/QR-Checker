package com.datools.qrchecker.util

/**
 * Decides whether a code the camera picked deserves an answer.
 *
 * A code is answered once per presentation: while it keeps showing up in frames, it is
 * the same box still in front of the camera. It counts as gone only after [awayMs]
 * without a single sighting, so a box taken away and brought back is answered again.
 *
 * Every code in the frame is tracked, not just the picked one. With two codes in view
 * the pick can flip between them from frame to frame, and tracking only the last pick
 * made each flip look like a new presentation: the phone answered both codes in turn,
 * buzzing several times a second.
 */
class CodePresence(private val awayMs: Long = DEFAULT_AWAY_MS) {

    private class Sighting(var lastSeen: Long, var answered: Boolean)

    private val sightings = HashMap<String, Sighting>()

    /**
     * Records one decoded frame and returns true when [candidate] should be answered now.
     *
     * [visible] are all codes found in the frame; [candidate] is the one the camera
     * picked for an answer, or null when nothing in the frame qualifies.
     */
    fun onFrame(visible: Collection<String>, candidate: String?, now: Long): Boolean {
        dropGone(now)
        val answer = candidate != null && sightings[candidate]?.answered != true
        for (code in visible) see(code, now)
        if (candidate != null) see(candidate, now).also { if (answer) it.answered = true }
        return answer
    }

    /** The code was answered and is still in front of the camera. */
    fun isPresent(code: String, now: Long): Boolean {
        val sighting = sightings[code] ?: return false
        return sighting.answered && now - sighting.lastSeen < awayMs
    }

    /** Answers the next pick again, even for the codes still in view: the user asked. */
    fun forgetAnswers() {
        sightings.values.forEach { it.answered = false }
    }

    private fun see(code: String, now: Long): Sighting =
        sightings.getOrPut(code) { Sighting(now, answered = false) }.also { it.lastSeen = now }

    private fun dropGone(now: Long) {
        sightings.values.removeAll { now - it.lastSeen >= awayMs }
    }

    companion object {
        /** Longer than the gaps in detection a steady hand produces, short enough to re-ask. */
        const val DEFAULT_AWAY_MS = 2_500L
    }
}
