package com.datools.qrchecker.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val codes: List<String>,
    val scannedCodes: List<String>
)