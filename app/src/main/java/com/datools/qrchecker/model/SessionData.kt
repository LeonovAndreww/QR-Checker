package com.datools.qrchecker.model

data class SessionData(
    val id: String,
    val name: String,
    val codes: List<String>,
    val scannedCodes: List<String>,
    /** When each code was scanned, in ms; codes without a known time are absent. */
    val scanTimes: Map<String, Long>? = null,
    /** The session collects codes instead of checking them against a list. */
    val collecting: Boolean = false
)
