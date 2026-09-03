package com.datools.qrchecker.model

/**
 * Session without its codes, for the list on the home screen — loading every code of every
 * session only to draw a row of names does not scale.
 */
data class SessionSummary(
    val id: String,
    val name: String,
    val total: Int,
    val scanned: Int,
    val createdAt: Long,
    val openedAt: Long
)
