package com.datools.qrchecker.scan

import com.datools.qrchecker.model.SessionData

/** The app's answer to a presented code. Kept out of the screen so it can be tested. */
sealed interface ScanVerdict {

    val code: String

    /** The code was in the list and is marked for the first time. */
    data class Marked(override val code: String) : ScanVerdict

    /** The code was not in the session and has been added. Collecting sessions only. */
    data class Recorded(override val code: String) : ScanVerdict

    /** The code is already marked; [at] is when, null if unknown. */
    data class Repeat(override val code: String, val at: Long?) : ScanVerdict

    /** The code is not in this batch. Never happens in a collecting session. */
    data class Foreign(override val code: String) : ScanVerdict
}

/**
 * The order matters. A repeat comes first: a marked code stays marked in any session,
 * and a collecting one must not add it again. Then the list: a code from the document is
 * marked. Only then the session kinds part: collecting adds an unknown code, checking
 * calls it foreign.
 */
fun verdictFor(session: SessionData, code: String): ScanVerdict = when {
    code in session.scannedCodes -> ScanVerdict.Repeat(code, session.scanTimes?.get(code))
    code in session.codes -> ScanVerdict.Marked(code)
    session.collecting -> ScanVerdict.Recorded(code)
    else -> ScanVerdict.Foreign(code)
}
