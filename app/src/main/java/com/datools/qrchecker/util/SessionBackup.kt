package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.datools.qrchecker.data.room.SessionDao
import com.datools.qrchecker.di.ApplicationScope
import com.datools.qrchecker.model.SessionData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SessionBackup"

private const val PREFS = "backup"
private const val KEY_FOLDER = "folderUri"
private const val KEY_ENABLED = "enabled"

/**
 * Copies of sessions in a folder the user picked.
 *
 * Uninstalling wipes the app's private storage, and Auto Backup does not run for apps
 * installed outside Play. Files in a user-chosen folder survive both, so this is the one
 * way across a reinstall that does not depend on anybody else.
 */
@Singleton
class SessionBackup @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dao: SessionDao,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    /**
     * Writes and deletes of one session are serialised, so a save queued just before a
     * delete cannot bring the file back. One lock per session, so a slow write of a big
     * session does not hold up the others.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(sessionId: String): Mutex = locks.getOrPut(sessionId) { Mutex() }

    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(): Uri? = prefs.getString(KEY_FOLDER, null)?.toUri()

    /** Automatic copies are on. Without a folder there is nowhere to write, so off. */
    fun isEnabled(): Boolean = folder() != null && prefs.getBoolean(KEY_ENABLED, true)

    fun setEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /**
     * Remembers the folder and keeps the right to write there across restarts; without a
     * persistable grant the permission ends with the process.
     */
    fun setFolder(uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs.edit { putString(KEY_FOLDER, uri.toString()).putBoolean(KEY_ENABLED, true) }
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Can't hold on to the backup folder", t)
        false
    }

    fun clearFolder() {
        folder()?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (t: Throwable) {
                Log.w(TAG, "The backup folder permission was already gone", t)
            }
        }
        prefs.edit { remove(KEY_FOLDER).putBoolean(KEY_ENABLED, false) }
    }

    /** Readable name of the chosen folder for the settings screen. */
    fun folderName(): String? = folder()?.let { uri ->
        runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
            ?: uri.lastPathSegment
    }

    /**
     * Writes a copy. Returns false when there is no folder or it cannot be written; the
     * caller decides whether that is worth telling the user.
     */
    suspend fun save(session: SessionData): Boolean = withContext(Dispatchers.IO) {
        val folder = folder() ?: return@withContext false
        lockFor(session.id).withLock {
            try {
                // the save may have been queued before the session was deleted
                if (dao.getSession(session.id) == null) return@withContext false

                val dir = DocumentFile.fromTreeUri(context, folder)
                if (dir == null || !dir.canWrite()) {
                    Log.w(TAG, "The backup folder is not writable any more")
                    return@withContext false
                }

                val wanted = fileNameFor(session)
                val mine = dir.listFiles().filter { belongsTo(it.name, session.id) }
                // the provider may have appended its own extension, so match by prefix
                val reusable = mine.firstOrNull {
                    it.name == wanted || it.name?.startsWith("$wanted.") == true
                }
                // a renamed session leaves files under the old name behind
                mine.filter { it.uri != reusable?.uri }.forEach { it.delete() }

                val file = reusable
                    ?: dir.createFile(SESSION_FILE_MIME, wanted)
                    ?: return@withContext false

                // "wt" truncates, so a shorter copy leaves no tail of the old one
                context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                    it.write(writeSessionFile(session).toByteArray())
                } ?: return@withContext false
                true
            } catch (t: Throwable) {
                Log.e(TAG, "Can't write the backup", t)
                false
            }
        }
    }

    /** Writes a copy only when automatic copies are on. Silent: it runs in the background. */
    suspend fun autoSave(session: SessionData) {
        if (isEnabled()) save(session)
    }

    /** Queues a copy that must happen even if the calling screen is already gone. */
    fun scheduleSave(session: SessionData) {
        if (!isEnabled()) return
        scope.launch { save(session) }
    }

    /** Removes the copy of a deleted session, or Restore would bring it back. */
    fun scheduleDelete(sessionId: String) {
        val folder = folder() ?: return
        scope.launch {
            lockFor(sessionId).withLock {
                try {
                    DocumentFile.fromTreeUri(context, folder)?.listFiles()
                        ?.filter { belongsTo(it.name, sessionId) }
                        ?.forEach { it.delete() }
                } catch (t: Throwable) {
                    Log.w(TAG, "Can't remove the backup of a deleted session", t)
                }
            }
            locks.remove(sessionId)
        }
    }

    /**
     * Reads every copy in the folder, skipping files that do not parse.
     *
     * A file counts as a copy only when its name carries the id of the session inside it:
     * the folder is often Downloads, where shared session files land too. When one
     * session has several copies, the newest one wins.
     */
    suspend fun readAll(): List<SessionData> = withContext(Dispatchers.IO) {
        val folder = folder() ?: return@withContext emptyList()
        val dir = DocumentFile.fromTreeUri(context, folder) ?: return@withContext emptyList()

        dir.listFiles()
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                if (!name.contains(".$SESSION_FILE_EXTENSION")) return@mapNotNull null
                try {
                    readSessionFile(readTextFromUri(context, file.uri))
                        .takeIf { belongsTo(name, it.session.id) }
                } catch (t: Throwable) {
                    Log.w(TAG, "Skipping an unreadable backup: $name", t)
                    null
                }
            }
            .groupBy { it.session.id }
            .map { (_, copies) -> copies.maxBy { it.savedAt }.session }
    }

    /**
     * Readable part for whoever opens the folder, id at the end so a rename does not
     * duplicate the file and two sessions with one name do not overwrite each other.
     */
    private fun fileNameFor(session: SessionData) =
        "${sessionFileName(session.name)}__${session.id}.$SESSION_FILE_EXTENSION"

    /**
     * Matched by "contains", not "ends with": the provider may append an extension for
     * the MIME type, turning "Name__id.qrcheck" into "Name__id.qrcheck.json".
     */
    private fun belongsTo(name: String?, sessionId: String) =
        name != null && name.contains("__$sessionId.$SESSION_FILE_EXTENSION")
}
