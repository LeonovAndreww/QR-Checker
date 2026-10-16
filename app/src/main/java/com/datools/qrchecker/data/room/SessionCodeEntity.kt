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
    /** When the code was scanned, in ms; null when unscanned or when the time is unknown. */
    val scannedAt: Long? = null,
    /**
     * When the code was moved to the bin; null while it is in the session. The row is
     * kept so the code can be restored from the edit screen; lists, counters and exports
     * do not see it.
     */
    val deletedAt: Long? = null
)
