package com.datools.qrchecker.scan

import org.junit.Assert.assertEquals
import org.junit.Test

class LinearReadVoteTest {

    private fun run(vote: LinearReadVote, vararg reads: Pair<String, Long>) =
        reads.map { (value, at) -> vote.offer(value, at) }

    @Test
    fun `a code is accepted on the third read`() {
        assertEquals(listOf(false, false, true), run(LinearReadVote(), "A" to 0, "A" to 80, "A" to 160))
    }

    @Test
    fun `a misread every other frame does not block the real code`() {
        // how a tilted label reads: real, real, misread, real
        assertEquals(
            listOf(false, false, false, true),
            run(LinearReadVote(), "B" to 0, "B" to 80, "X" to 160, "B" to 240)
        )
    }

    @Test
    fun `single misreads are never accepted`() {
        assertEquals(
            listOf(false, false, false, false),
            run(LinearReadVote(), "X1" to 0, "X2" to 80, "X3" to 160, "X4" to 240)
        )
    }

    @Test
    fun `old reads do not count`() {
        // a misread twice, the box taken away, then once more two seconds later
        assertEquals(
            listOf(false, false, false),
            run(LinearReadVote(), "X" to 0, "X" to 80, "X" to 2_100)
        )
    }
}
