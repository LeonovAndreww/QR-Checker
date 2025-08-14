package com.datools.qrchecker.model

data class SessionData(
    val id: String,
    val name: String,
    val codes: MutableList<String>,
    val scannedCodes: MutableList<String>
)
