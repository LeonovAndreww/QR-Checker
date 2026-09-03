package com.datools.qrchecker.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** Когда сессия заведена. Ноль у заведённых до появления этого столбца. */
    val createdAt: Long = 0,
    /**
     * Когда её последний раз открывали. По этому полю список и упорядочен: то, чем
     * занимаются сейчас, должно быть сверху, а не то, что завели раньше всех.
     */
    val openedAt: Long = 0
)
