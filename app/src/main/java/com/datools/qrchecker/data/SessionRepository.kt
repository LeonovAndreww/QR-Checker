package com.datools.qrchecker.data

import com.datools.qrchecker.data.room.SessionCodeEntity
import com.datools.qrchecker.data.room.SessionDao
import com.datools.qrchecker.data.room.SessionEntity
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.model.SessionSummary
import com.datools.qrchecker.util.SessionBackup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** How long a code stays in the bin before it is purged: a week. */
private const val BIN_LIFETIME_MS = 7L * 24 * 60 * 60 * 1000

/** Codes per SQL "IN (...)" list, under SQLite's limit on bound parameters. */
private const val IN_CHUNK = 500

@Singleton
class SessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val backup: SessionBackup
) {
    suspend fun insert(session: SessionData, now: Long = System.currentTimeMillis()) {
        dao.insertSessionWithCodes(
            SessionEntity(
                id = session.id,
                name = session.name,
                createdAt = now,
                openedAt = now,
                collecting = session.collecting
            ),
            session.toCodeEntities()
        )
    }

    suspend fun getById(id: String): SessionData? {
        val session = dao.getSession(id) ?: return null
        val codes = dao.getCodes(id)
        return SessionData(
            id = session.id,
            name = session.name,
            codes = codes.map { it.code },
            scannedCodes = codes.filter { it.scanned }.map { it.code },
            scanTimes = codes.mapNotNull { row -> row.scannedAt?.let { row.code to it } }.toMap(),
            collecting = session.collecting
        )
    }

    /** The home list is ordered by this, so what is being worked on now stays on top. */
    suspend fun touchOpened(sessionId: String, at: Long = System.currentTimeMillis()) =
        dao.touchOpened(sessionId, at)

    fun getSummariesFlow(): Flow<List<SessionSummary>> = dao.getSummariesFlow()

    /** Deletes the session together with its backup copy, wherever the delete comes from. */
    suspend fun delete(sessionId: String) {
        dao.deleteSession(sessionId)
        backup.scheduleDelete(sessionId)
    }

    /** False when the code is not in the session or was already scanned. */
    suspend fun markScanned(
        sessionId: String,
        code: String,
        at: Long = System.currentTimeMillis()
    ): Boolean = dao.markScanned(sessionId, code, at) > 0

    /** Adds a new code to a collecting session, already scanned. */
    suspend fun recordScanned(
        sessionId: String,
        code: String,
        at: Long = System.currentTimeMillis()
    ) = dao.addScannedCode(sessionId, code, at)

    /** Returns the code to the unscanned list without removing it from the session. */
    suspend fun unmarkScanned(sessionId: String, code: String) {
        dao.markUnscanned(sessionId, code)
    }

    /**
     * Moves a code to the bin rather than deleting it: it is the only destructive action
     * here, and missing a row in a list of thousands is easy.
     */
    suspend fun deleteCode(sessionId: String, code: String, at: Long = System.currentTimeMillis()) {
        dao.moveCodeToBin(sessionId, code, at)
    }

    /** Binned codes with the time they were binned, newest first. */
    suspend fun binnedCodes(sessionId: String): List<Pair<String, Long>> =
        dao.getDeletedCodes(sessionId).map { it.code to (it.deletedAt ?: 0L) }

    suspend fun restoreCode(sessionId: String, code: String) {
        dao.restoreCode(sessionId, code)
    }

    /** Empties the bin for good. */
    suspend fun emptyBin(sessionId: String): Int = dao.purgeBin(sessionId)

    /** Deletes one binned code for good. */
    suspend fun purgeCode(sessionId: String, code: String) {
        dao.purgeCode(sessionId, code)
    }

    /** Drops bin entries older than a week, across all sessions. */
    suspend fun purgeOldBin(now: Long = System.currentTimeMillis()): Int =
        dao.purgeBinnedBefore(now - BIN_LIFETIME_MS)

    suspend fun rename(sessionId: String, name: String) = dao.renameSession(sessionId, name)

    /** Replaces the code list, keeping the marks and their times on codes that survive. */
    suspend fun replaceCodes(sessionId: String, name: String, codes: List<String>) {
        val previous = dao.getCodes(sessionId).filter { it.scanned }
        val stillScanned = previous.mapTo(HashSet()) { it.code }
        val previousTimes = previous.mapNotNull { row -> row.scannedAt?.let { row.code to it } }.toMap()

        dao.renameAndReplaceCodes(
            sessionId,
            name,
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
     * A session with exactly the same set of codes, in any order: the same document opened
     * twice is the same batch. Sessions of another size are filtered out in SQL first.
     */
    suspend fun findWithSameCodes(codes: Collection<String>): SessionData? {
        val wanted = codes.toHashSet()
        for (id in dao.sessionIdsWithCodeCount(wanted.size)) {
            if (dao.getCodeValues(id).toHashSet() == wanted) return getById(id)
        }
        return null
    }

    /**
     * Copies marks from a file into an existing session and returns how many were new.
     * Each mark keeps its own time from the file; codes without one get [at].
     */
    suspend fun mergeScanned(
        sessionId: String,
        scanned: Collection<String>,
        times: Map<String, Long> = emptyMap(),
        at: Long = System.currentTimeMillis()
    ): Int {
        val (timed, untimed) = scanned.partition { it in times }
        val changed = untimed.chunked(IN_CHUNK).sumOf { dao.markScannedIn(sessionId, it, at) }
        return changed + dao.markScannedEach(sessionId, timed.associateWith { times.getValue(it) })
    }

    /** Ids of all sessions, so a restore does not create duplicates. */
    suspend fun existingIds(): Set<String> = dao.getSessionIds().toHashSet()

    /** The wanted name, or the same with a number in brackets if it is taken. */
    suspend fun freeName(wanted: String): String {
        val taken = dao.getSessionNames().toHashSet()
        if (wanted !in taken) return wanted
        var n = 2
        while ("$wanted ($n)" in taken) n++
        return "$wanted ($n)"
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
