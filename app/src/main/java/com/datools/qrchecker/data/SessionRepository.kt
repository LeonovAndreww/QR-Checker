package com.datools.qrchecker.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.data.room.SessionCodeEntity
import com.datools.qrchecker.data.room.SessionEntity
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.model.SessionSummary
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val TAG = "QRChecker"

class SessionRepository(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).sessionDao()

    suspend fun insert(session: SessionData) {
        dao.upsertSession(SessionEntity(id = session.id, name = session.name))
        dao.replaceCodes(session.id, session.toCodeEntities())
    }

    suspend fun getById(id: String): SessionData? {
        val session = dao.getSession(id) ?: return null
        val codes = dao.getCodes(id)
        return SessionData(
            id = session.id,
            name = session.name,
            codes = codes.map { it.code },
            scannedCodes = codes.filter { it.scanned }.map { it.code },
            scanTimes = codes.mapNotNull { row ->
                row.scannedAt?.let { row.code to it }
            }.toMap()
        )
    }

    fun getSummariesFlow(): Flow<List<SessionSummary>> = dao.getSummariesFlow()

    suspend fun delete(sessionId: String) = dao.deleteSession(sessionId)

    /**
     * Marks one code as scanned, touching a single row. Returns false when the code is not
     * part of the session or was already scanned.
     */
    suspend fun markScanned(
        sessionId: String,
        code: String,
        at: Long = System.currentTimeMillis()
    ): Boolean = dao.markScanned(sessionId, code, at) > 0

    /** Returns the code to the unscanned list without removing it from the session. */
    suspend fun unmarkScanned(sessionId: String, code: String) {
        dao.markUnscanned(sessionId, code)
    }

    suspend fun deleteCode(sessionId: String, code: String) {
        dao.deleteCode(sessionId, code)
    }

    suspend fun rename(sessionId: String, name: String) = dao.renameSession(sessionId, name)

    /** Replaces the code list, keeping the scanned state of the codes that survive. */
    suspend fun replaceCodes(sessionId: String, name: String, codes: List<String>) {
        val previous = dao.getCodes(sessionId).filter { it.scanned }
        val stillScanned = previous.mapTo(HashSet()) { it.code }
        // время отметки переживает замену PDF вместе с самой отметкой
        val previousTimes = previous.mapNotNull { row -> row.scannedAt?.let { row.code to it } }
            .toMap()

        dao.renameSession(sessionId, name)
        dao.replaceCodes(
            sessionId,
            codes.mapIndexed { position, code ->
                SessionCodeEntity(
                    sessionId = sessionId,
                    code = code,
                    scanned = code in stillScanned,
                    position = position,
                    scannedAt = previousTimes[code]
                )
            }
        )
    }

    /**
     * Сессия, у которой ровно тот же набор кодов, что у открываемого файла.
     *
     * Сравнивается набор, а не порядок: тот же PDF, разложенный в другом порядке, - та же
     * партия. Сначала отсеиваются сессии другого размера, чтобы не тянуть коды всех
     * сессий подряд.
     */
    suspend fun findWithSameCodes(codes: Collection<String>): SessionData? {
        val wanted = codes.toHashSet()
        for (id in dao.getSessionIds()) {
            if (dao.countCodes(id) != wanted.size) continue
            if (dao.getCodeValues(id).toHashSet() == wanted) return getById(id)
        }
        return null
    }

    /**
     * Переносит отметки из другой копии той же сессии. Возвращает, сколько отметок
     * добавилось - только их и стоит показывать пользователю, «объединено 0» это ответ.
     *
     * Пачка режется на куски: в списке IN у SQLite ограничение на число параметров, а
     * партия бывает и на несколько тысяч коробок.
     */
    suspend fun mergeScanned(
        sessionId: String,
        scanned: Collection<String>,
        at: Long = System.currentTimeMillis()
    ): Int = scanned.chunked(500).sumOf { dao.markScannedIn(sessionId, it, at) }

    /** Идентификаторы всех сессий - чтобы при восстановлении не заводить дубликаты. */
    suspend fun existingIds(): Set<String> = dao.getSessionIds().toHashSet()

    suspend fun migrateFromSharedPrefsIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migrated_to_room", false)) return@withContext

        val gson = Gson()
        for ((_, value) in prefs.all) {
            try {
                val json = value as? String ?: continue
                val old = gson.fromJson(json, SessionData::class.java) ?: continue
                insert(old)
            } catch (t: Throwable) {
                Log.w(TAG, "Can't migrate a legacy session", t)
            }
        }
        prefs.edit { clear().putBoolean("migrated_to_room", true) }
    }
}

private fun SessionData.toCodeEntities(): List<SessionCodeEntity> {
    val scanned = scannedCodes.toHashSet()
    return codes.mapIndexed { position, code ->
        SessionCodeEntity(
            sessionId = id,
            code = code,
            scanned = code in scanned,
            position = position,
            scannedAt = scanTimes?.get(code)
        )
    }
}
