package com.datools.qrchecker.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * One row per code in a session.
 *
 * The previous schema kept both code lists as JSON blobs on the session row, so marking a
 * single code as scanned rewrote every code in the session. Here a scan touches one row.
 *
 * [position] preserves the order the codes were parsed out of the PDF, which the lists in
 * the UI rely on.
 */
@Entity(
    tableName = "session_codes",
    primaryKeys = ["sessionId", "code"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class SessionCodeEntity(
    val sessionId: String,
    val code: String,
    val scanned: Boolean,
    val position: Int,
    /**
     * Когда код отметили, в миллисекундах. null у неотсканированных и у тех, что были
     * отмечены до появления этого столбца - «неизвестно» и «не отсканирован» это разные
     * вещи, и различать их приходится.
     */
    val scannedAt: Long? = null
)
