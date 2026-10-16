package com.datools.qrchecker.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = 0,
    /** When it was last opened; the home list is ordered by this. */
    val openedAt: Long = 0,
    /**
     * The session collects codes instead of checking them against a list. It starts
     * empty and records every new code; the only thing it catches is repeats.
     */
    val collecting: Boolean = false
)
