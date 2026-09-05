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

    /** Что лежит в корзине - свежеудалённое сверху. */
    @Query(
        """
        SELECT * FROM session_codes
        WHERE sessionId = :sessionId AND deletedAt IS NOT NULL
        ORDER BY deletedAt DESC
        """
    )
    abstract suspend fun getDeletedCodes(sessionId: String): List<SessionCodeEntity>

    /**
     * Убирает коды сессии, не трогая корзину.
     *
     * Замена документа не должна выметать то, что человек убрал руками: это разные
     * действия. Код из корзины, снова оказавшийся в новом документе, вернётся обычным -
     * вставка перезапишет его строку.
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
     * Отмечает пачку кодов разом. Возвращает, сколько отметок реально добавилось: коды,
     * которых в сессии нет или которые уже отмечены, не считаются.
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

    /**
     * Сессии ровно с таким числом кодов.
     *
     * Отбор идёт одним запросом, а не «сколько кодов» на каждую сессию по очереди: при
     * полусотне заведённых партий это была сотня обращений к базе на каждое открытие
     * файла.
     */
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
     * Убирает код в корзину. Отметка при этом снимается: код вернётся неотсканированным,
     * потому что убирали его именно из списка неотсканированных.
     */
    @Query(
        """
        UPDATE session_codes SET deletedAt = :at, scanned = 0, scannedAt = NULL
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

    /** Насовсем: это и есть «очистить корзину». */
    @Query("DELETE FROM session_codes WHERE sessionId = :sessionId AND deletedAt IS NOT NULL")
    abstract suspend fun purgeBin(sessionId: String): Int

    /** Один код из корзины - насовсем. */
    @Query(
        """
        DELETE FROM session_codes
        WHERE sessionId = :sessionId AND code = :code AND deletedAt IS NOT NULL
        """
    )
    abstract suspend fun purgeCode(sessionId: String, code: String): Int

    /** Всё, что пролежало в корзине дольше положенного, по всем сессиям разом. */
    @Query("DELETE FROM session_codes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    abstract suspend fun purgeBinnedBefore(before: Long): Int

    @Query(
        """
        SELECT s.id AS id,
               s.name AS name,
               COUNT(c.code) AS total,
               COALESCE(SUM(c.scanned), 0) AS scanned,
               s.createdAt AS createdAt,
               s.openedAt AS openedAt
        FROM sessions s
        LEFT JOIN session_codes c ON c.sessionId = s.id AND c.deletedAt IS NULL
        GROUP BY s.id, s.name, s.createdAt, s.openedAt, s.rowid
        ORDER BY s.openedAt DESC, s.rowid DESC
        """
    )
    abstract fun getSummariesFlow(): Flow<List<SessionSummary>>

    @Transaction
    open suspend fun replaceCodes(sessionId: String, codes: List<SessionCodeEntity>) {
        deleteAllCodes(sessionId)
        insertCodes(codes)
    }

    /**
     * Сессия и её коды пишутся вместе или не пишутся вовсе.
     *
     * По отдельности между ними есть промежуток, в который сессия уже заведена, а кодов
     * в ней ещё нет: приложение, снятое в этот момент, открывалось бы потом на пустой
     * партии.
     */
    @Transaction
    open suspend fun insertSessionWithCodes(
        session: SessionEntity,
        codes: List<SessionCodeEntity>
    ) {
        upsertSession(session)
        replaceCodes(session.id, codes)
    }

    /** То же для замены документа: имя и новый список кодов - одна правка. */
    @Transaction
    open suspend fun renameAndReplaceCodes(
        sessionId: String,
        name: String,
        codes: List<SessionCodeEntity>
    ) {
        renameSession(sessionId, name)
        replaceCodes(sessionId, codes)
    }
}
