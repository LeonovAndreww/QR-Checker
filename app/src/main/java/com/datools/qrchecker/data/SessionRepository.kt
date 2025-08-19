package com.datools.qrchecker.data

import android.content.Context
import android.util.Log
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.data.room.SessionEntity
import com.datools.qrchecker.model.SessionData
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.core.content.edit

class SessionRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.sessionDao()
    private val gson = Gson()

    suspend fun insert(session: SessionData) {
        dao.insert(session.toEntity())
    }

    suspend fun update(session: SessionData) {
        dao.insert(session.toEntity())
    }

    suspend fun getById(id: String): SessionData? {
        return dao.getById(id)?.toModel()
    }

    fun getAllFlow(): Flow<List<SessionData>> {
        return dao.getAllFlow().map { list -> list.map { it.toModel() } }
    }

    suspend fun delete(session: SessionData) {
        dao.delete(session.toEntity())
    }

    suspend fun migrateFromSharedPrefsIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("sessions", Context.MODE_PRIVATE)
        if (prefs.getBoolean("migrated_to_room", false)) return@withContext

        for ((_, value) in prefs.all) {
            try {
                val json = value as? String ?: continue
                val old = gson.fromJson(json, SessionData::class.java)
                if (old != null) dao.insert(old.toEntity())
            } catch (t: Throwable) {
                Log.w("SessionRepo", "Can't migrate: ${t.message}")
            }
        }
        prefs.edit { clear().putBoolean("migrated_to_room", true) }
    }
}

private fun SessionData.toEntity() = SessionEntity(
    id = id, name = name, codes = codes, scannedCodes = scannedCodes
)

private fun SessionEntity.toModel() = SessionData(
    id = id, name = name, codes = codes, scannedCodes = scannedCodes
)