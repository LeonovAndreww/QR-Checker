package com.datools.qrchecker.util

/**
 * Decides which reads of a linear barcode to trust.
 *
 * Data Matrix and QR have error correction and never return a misread symbol. EAN and
 * UPC only have a check digit, which two swapped digits pass: a tilted label produced
 * several different "codes" from one box within seconds, and a collecting session
 * recorded them all.
 *
 * Misreads differ from frame to frame while the real code stays the same, so a code is
 * accepted once it has been read [votes] times within the last second. Not in a row: on
 * a tilted label a misread slips in every other frame. Old reads expire, so a new box
 * does not inherit the previous one's votes.
 */
class LinearReadVote(
    private val votes: Int = 3,
    private val windowMs: Long = 1_000L
) {
    private val reads = ArrayDeque<Pair<String, Long>>()

    /** Records a read and tells whether this code now has enough votes. */
    fun offer(value: String, now: Long): Boolean {
        while (reads.isNotEmpty() && now - reads.first().second > windowMs) reads.removeFirst()
        reads.addLast(value to now)
        return reads.count { it.first == value } >= votes
    }

    fun clear() = reads.clear()
}
