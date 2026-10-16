package com.datools.qrchecker.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.datools.qrchecker.model.SessionSummary
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertSession(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun getSession(id: String): SessionEntity?

    @Query("DELETE FROM sessions WHERE id = :id")
    abstract suspend fun deleteSession(id: String)

    @Query("UPDATE sessions SET openedAt = :at WHERE id = :id")
    abstract suspend fun touchOpened(id: String, at: Long)

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    abstract suspend fun renameSession(id: String, name: String)

    @Query("UPDATE sessions SET collecting = 0 WHERE id = :id")
    abstract suspend fun stopCollecting(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCodes(codes: List<SessionCodeEntity>)

    @Query(
        """
        SELECT * FROM session_codes
        WHERE sessionId = :sessionId AND deletedAt IS NULL
        ORDER BY position
        """
    )
    abstract suspend fun getCodes(sessionId: String): List<SessionCodeEntity>

    /** The bin, most recently deleted first. */
    @Query(
        """
        SELECT * FROM session_codes
        WHERE sessionId = :sessionId AND deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
        """
    )
    abstract suspend fun getDeletedCodes(sessionId: String): List<SessionCodeEntity>

    /**
     * Removes the session's codes but leaves the bin alone: replacing the document and
     * binning a code by hand are different actions. A binned code that is in the new
     * document comes back as a regular one, since the insert replaces its row.
     */
    @Query("DELETE FROM session_codes WHERE sessionId = :sessionId AND deletedAt IS NULL")
    abstract suspend fun deleteAllCodes(sessionId: String)

    /** Returns 0 when the code does not belong to the session or was already scanned. */
    @Query(
        """
        UPDATE session_codes SET scanned = 1, scannedAt = :at
        WHERE sessionId = :sessionId AND code = :code AND scanned = 0
          AND deletedAt IS NULL
        """
    )
    abstract suspend fun markScanned(sessionId: String, code: String, at: Long): Int

    /**
     * Marks a batch of codes at once. Returns how many marks were actually added; codes
     * not in the session or already marked do not count.
     */
    @Query(
        """
        UPDATE session_codes SET scanned = 1, scannedAt = :at
        WHERE sessionId = :sessionId AND scanned = 0 AND deletedAt IS NULL
          AND code IN (:codes)
        """
    )
    abstract suspend fun markScannedIn(sessionId: String, codes: List<String>, at: Long): Int

    @Query(
        "SELECT code FROM session_codes WHERE sessionId = :sessionId AND deletedAt IS NULL"
    )
    abstract suspend fun getCodeValues(sessionId: String): List<String>

    @Query("SELECT id FROM sessions")
    abstract suspend fun getSessionIds(): List<String>

    @Query("SELECT name FROM sessions")
    abstract suspend fun getSessionNames(): List<String>

    /** Sessions with exactly this many codes, in one query rather than one per session. */
    @Query(
        """
        SELECT sessionId FROM session_codes WHERE deletedAt IS NULL
        GROUP BY sessionId HAVING COUNT(*) = :size
        """
    )
    abstract suspend fun sessionIdsWithCodeCount(size: Int): List<String>

    @Query(
        """
        UPDATE session_codes SET scanned = 0, scannedAt = NULL
        WHERE sessionId = :sessionId AND code = :code AND deletedAt IS NULL
        """
    )
    abstract suspend fun markUnscanned(sessionId: String, code: String): Int

    /**
     * Moves a code to the bin, keeping its mark.
     *
     * Restoring must return exactly what was binned. Clearing the mark would make a
     * restore also undo the scan and lose its time; in a collecting session the code
     * would come back unmarked, which means invisible there.
     *
     * Counters are unaffected: they already exclude binned rows by deletedAt.
     */
    @Query(
        """
        UPDATE session_codes SET deletedAt = :at
        WHERE sessionId = :sessionId AND code = :code AND deletedAt IS NULL
        """
    )
    abstract suspend fun moveCodeToBin(sessionId: String, code: String, at: Long): Int

    @Query(
        """
        UPDATE session_codes SET deletedAt = NULL
        WHERE sessionId = :sessionId AND code = :code AND deletedAt IS NOT NULL
        """
    )
    abstract suspend fun restoreCode(sessionId: String, code: String): Int

    /** For good: this is "empty the bin". */
    @Query("DELETE FROM session_codes WHERE sessionId = :sessionId AND deletedAt IS NOT NULL")
    abstract suspend fun purgeBin(sessionId: String): Int

    /** One binned code, for good. */
    @Query(
        """
        DELETE FROM session_codes
        WHERE sessionId = :sessionId AND code = :code AND deletedAt IS NOT NULL
        """
    )
    abstract suspend fun purgeCode(sessionId: String, code: String): Int

    /** Bin entries older than [before], across all sessions. */
    @Query("DELETE FROM session_codes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    abstract suspend fun purgeBinnedBefore(before: Long): Int

    @Query(
        """
        SELECT s.id AS id,
               s.name AS name,
               COUNT(c.code) AS total,
               COALESCE(SUM(c.scanned), 0) AS scanned,
               s.createdAt AS createdAt,
               s.openedAt AS openedAt,
               s.collecting AS collecting
        FROM sessions s
        LEFT JOIN session_codes c ON c.sessionId = s.id AND c.deletedAt IS NULL
        GROUP BY s.id, s.name, s.createdAt, s.openedAt, s.collecting, s.rowid
        ORDER BY s.openedAt DESC, s.rowid DESC
        """
    )
    abstract fun getSummariesFlow(): Flow<List<SessionSummary>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM session_codes WHERE sessionId = :sessionId")
    abstract suspend fun nextPosition(sessionId: String): Int

    /**
     * Adds a code that was not in the session, already marked. Collecting sessions only:
     * in a checking session such a code is "not in this batch", not a new row.
     *
     * It goes after the last position, since arrival order is the only order a collected
     * session has.
     */
    @Transaction
    open suspend fun addScannedCode(sessionId: String, code: String, at: Long) {
        insertCodes(
            listOf(
                SessionCodeEntity(
                    sessionId = sessionId,
                    code = code,
                    scanned = true,
                    position = nextPosition(sessionId),
                    scannedAt = at
                )
            )
        )
    }

    /**
     * Marks codes, each with its own time, in one transaction. One transaction per mark
     * made merging a file with thousands of marks take seconds.
     */
    @Transaction
    open suspend fun markScannedEach(sessionId: String, times: Map<String, Long>): Int =
        times.entries.sumOf { (code, at) -> markScanned(sessionId, code, at) }

    @Transaction
    open suspend fun replaceCodes(sessionId: String, codes: List<SessionCodeEntity>) {
        deleteAllCodes(sessionId)
        insertCodes(codes)
    }

    /**
     * The session and its codes are written together or not at all, so a process killed
     * in between cannot leave an empty session behind.
     */
    @Transaction
    open suspend fun insertSessionWithCodes(
        session: SessionEntity,
        codes: List<SessionCodeEntity>
    ) {
        upsertSession(session)
        replaceCodes(session.id, codes)
    }

    /**
     * Rename and code replacement as one change. A collecting session that gets a
     * document becomes a checking one: it was collecting because there was no list, and
     * now a code outside the list is "not in this session" rather than a new row.
     */
    @Transaction
    open suspend fun renameAndReplaceCodes(
        sessionId: String,
        name: String,
        codes: List<SessionCodeEntity>
    ) {
        renameSession(sessionId, name)
        replaceCodes(sessionId, codes)
        stopCollecting(sessionId)
    }
}
