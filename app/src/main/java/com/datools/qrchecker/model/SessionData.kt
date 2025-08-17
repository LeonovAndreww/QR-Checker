package com.datools.qrchecker.model

data class SessionData(
    val id: String,
    val name: String,
    val codes: List<String>,
    val scannedCodes: List<String>
)
