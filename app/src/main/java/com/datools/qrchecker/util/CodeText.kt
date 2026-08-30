package com.datools.qrchecker.util

/**
 * Control and formatting characters that some encoders embed into QR payloads
 * (line breaks, zero-width marks, DEL, ...).
 */
private val CONTROL_CHARS = Regex("\\p{C}")

/**
 * Normalizes a raw decoded QR payload.
 *
 * Codes parsed from a PDF and codes read by the camera are compared to each other,
 * so both paths must clean the text exactly the same way — otherwise a scanned code
 * silently fails to match its entry in the session list.
 */
fun normalizeCode(raw: String): String = CONTROL_CHARS.replace(raw, "").trim()
