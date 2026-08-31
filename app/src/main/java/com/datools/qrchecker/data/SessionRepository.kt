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
            scannedCodes = codes.filter { it.scanned }.map { it.code }
        )
    }

    fun getSummariesFlow(): Flow<List<SessionSummary>> = dao.getSummariesFlow()

    suspend fun delete(sessionId: String) = dao.deleteSession(sessionId)

    /**
     * Marks one code as scanned, touching a single row. Returns false when the code is not
     * part of the session or was already scanned.
     */
    suspend fun markScanned(sessionId: String, code: String): Boolean =
        dao.markScanned(sessionId, code) > 0

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
        val stillScanned = dao.getCodes(sessionId)
            .filter { it.scanned }
            .mapTo(HashSet()) { it.code }

        dao.renameSession(sessionId, name)
        dao.replaceCodes(
            sessionId,
            codes.mapIndexed { position, code ->
                SessionCodeEntity(
                    sessionId = sessionId,
                    code = code,
                    scanned = code in stillScanned,
                    position = position
                )
            }
        )
    }

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
            position = position
        )
    }
}
