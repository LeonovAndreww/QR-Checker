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

    @Query("UPDATE sessions SET name = :name WHERE id = :id")
    abstract suspend fun renameSession(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCodes(codes: List<SessionCodeEntity>)

    @Query("SELECT * FROM session_codes WHERE sessionId = :sessionId ORDER BY position")
    abstract suspend fun getCodes(sessionId: String): List<SessionCodeEntity>

    @Query("DELETE FROM session_codes WHERE sessionId = :sessionId")
    abstract suspend fun deleteAllCodes(sessionId: String)

    /** Returns 0 when the code does not belong to the session or was already scanned. */
    @Query("UPDATE session_codes SET scanned = 1 WHERE sessionId = :sessionId AND code = :code AND scanned = 0")
    abstract suspend fun markScanned(sessionId: String, code: String): Int

    /**
     * Отмечает пачку кодов разом. Возвращает, сколько отметок реально добавилось: коды,
     * которых в сессии нет или которые уже отмечены, не считаются.
     */
    @Query(
        """
        UPDATE session_codes SET scanned = 1
        WHERE sessionId = :sessionId AND scanned = 0 AND code IN (:codes)
        """
    )
    abstract suspend fun markScannedIn(sessionId: String, codes: List<String>): Int

    @Query("SELECT COUNT(*) FROM session_codes WHERE sessionId = :sessionId")
    abstract suspend fun countCodes(sessionId: String): Int

    @Query("SELECT code FROM session_codes WHERE sessionId = :sessionId")
    abstract suspend fun getCodeValues(sessionId: String): List<String>

    @Query("SELECT id FROM sessions")
    abstract suspend fun getSessionIds(): List<String>

    @Query("UPDATE session_codes SET scanned = 0 WHERE sessionId = :sessionId AND code = :code")
    abstract suspend fun markUnscanned(sessionId: String, code: String): Int

    @Query("DELETE FROM session_codes WHERE sessionId = :sessionId AND code = :code")
    abstract suspend fun deleteCode(sessionId: String, code: String): Int

    @Query(
        """
        SELECT s.id AS id,
               s.name AS name,
               COUNT(c.code) AS total,
               COALESCE(SUM(c.scanned), 0) AS scanned
        FROM sessions s
        LEFT JOIN session_codes c ON c.sessionId = s.id
        GROUP BY s.id, s.name, s.rowid
        ORDER BY s.rowid DESC
        """
    )
    abstract fun getSummariesFlow(): Flow<List<SessionSummary>>

    @Transaction
    open suspend fun replaceCodes(sessionId: String, codes: List<SessionCodeEntity>) {
        deleteAllCodes(sessionId)
        insertCodes(codes)
    }
}
