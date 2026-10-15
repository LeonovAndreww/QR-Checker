package com.datools.qrchecker.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.datools.qrchecker.QrCheckerApp
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "QRChecker"

private const val PREFS = "backup"
private const val KEY_FOLDER = "folderUri"
private const val KEY_ENABLED = "enabled"

/**
 * Копия сессий в папке, которую выбрал сам пользователь.
 *
 * Удаление приложения стирает его личное хранилище целиком, и никакая настройка этого не
 * меняет: Auto Backup от Google при установке APK мимо Play не срабатывает вовсе, а через
 * Play работает раз в сутки, по Wi-Fi, на зарядке и без обещаний. Файлы в выбранной
 * пользователем папке приложению не принадлежат - их удаление приложения не трогает.
 * Это единственный способ пережить переустановку, который зависит только от нас.
 */
object SessionBackup {

    /**
     * Запись и удаление копий одной сессии идут по очереди. Копия пишется в фоне при
     * уходе с экрана сканирования, и удаление сессии сразу после этого могло проскочить
     * раньше записи - тогда запись возвращала файл удалённой сессии обратно.
     *
     * Очередь своя у каждой сессии: копия большой партии в облачную папку пишется
     * секундами, и создание новой сессии не должно стоять за ней.
     */
    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(sessionId: String): Mutex = locks.getOrPut(sessionId) { Mutex() }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? =
        prefs(context).getString(KEY_FOLDER, null)?.toUri()

    /** Включена ли автокопия. Без выбранной папки бессмысленна и считается выключенной. */
    fun isEnabled(context: Context): Boolean =
        folder(context) != null && prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit { putBoolean(KEY_ENABLED, enabled) }
    }

    /**
     * Запоминает папку и удерживает право писать в неё после перезапуска: без
     * takePersistableUriPermission разрешение живёт только до конца процесса, и наутро
     * копия молча перестала бы писаться.
     */
    fun setFolder(context: Context, uri: Uri): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        prefs(context).edit { putString(KEY_FOLDER, uri.toString()).putBoolean(KEY_ENABLED, true) }
        true
    } catch (t: Throwable) {
        Log.e(TAG, "Can't hold on to the backup folder", t)
        false
    }

    fun clearFolder(context: Context) {
        folder(context)?.let { uri ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (t: Throwable) {
                Log.w(TAG, "The backup folder permission was already gone", t)
            }
        }
        prefs(context).edit { remove(KEY_FOLDER).putBoolean(KEY_ENABLED, false) }
    }

    /** Человекочитаемое имя выбранной папки для экрана настроек. */
    fun folderName(context: Context): String? =
        folder(context)?.let { uri ->
            runCatching { DocumentFile.fromTreeUri(context, uri)?.name }.getOrNull()
                ?: uri.lastPathSegment
        }

    /**
     * Имя файла копии. Читаемая часть - для того, кто откроет папку в проводнике,
     * идентификатор в конце - чтобы переименование сессии не плодило файлы-двойники и
     * чтобы две сессии с одинаковым названием не затирали друг друга.
     */
    private fun fileNameFor(session: SessionData) =
        "${sessionFileName(session.name)}__${session.id}.$SESSION_FILE_EXTENSION"

    /**
     * Файл принадлежит этой сессии.
     *
     * Проверяется вхождение, а не окончание: провайдер документов вправе дописать к
     * имени своё расширение по MIME-типу, и «Имя__id.qrcheck» лежит в папке как
     * «Имя__id.qrcheck.json». По точному окончанию такие файлы не находились - копии
     * плодились вместо перезаписи, а восстановление не видело ни одной.
     */
    private fun belongsTo(name: String?, sessionId: String) =
        name != null && name.contains("__$sessionId.$SESSION_FILE_EXTENSION")

    /**
     * Пишет копию сессии. Возвращает false, если папка не выбрана, отозвана или
     * недоступна - вызывающий решает, шуметь ему об этом или нет.
     */
    suspend fun save(context: Context, session: SessionData): Boolean =
        withContext(Dispatchers.IO) {
            val folder = folder(context) ?: return@withContext false
            lockFor(session.id).withLock {
                try {
                    // удалённую сессию не воскрешать: запись могла встать в очередь
                    // раньше удаления, а дойти до файлов позже
                    if (!SessionRepository(context).exists(session.id)) return@withContext false

                    val dir = DocumentFile.fromTreeUri(context, folder)
                    if (dir == null || !dir.canWrite()) {
                        Log.w(TAG, "The backup folder is not writable any more")
                        return@withContext false
                    }

                    val wanted = fileNameFor(session)
                    val mine = dir.listFiles().filter { belongsTo(it.name, session.id) }

                    // Ищется не по точному имени: провайдер мог дописать своё расширение,
                    // и findFile(wanted) промахивался бы каждый раз, создавая новый файл.
                    val reusable = mine.firstOrNull {
                        it.name == wanted || it.name?.startsWith("$wanted.") == true
                    }
                    // переименовали сессию - файлы со старым именем больше не нужны
                    mine.filter { it.uri != reusable?.uri }.forEach { it.delete() }

                    val file = reusable
                        ?: dir.createFile(SESSION_FILE_MIME, wanted)
                        ?: return@withContext false

                    // "wt" обрезает файл: без этого копия короче прежней оставила бы хвост
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

    /** Пишет копию, только если автокопия включена. Молча, это фоновое действие. */
    suspend fun autoSave(context: Context, session: SessionData) {
        if (isEnabled(context)) save(context, session)
    }

    /**
     * Ставит копию в очередь на области приложения, а не экрана: вызывается в том числе
     * при уходе с экрана сканирования, когда область композиции уже отменена и запись из
     * неё не состоялась бы.
     */
    fun scheduleSave(context: Context, session: SessionData) {
        val app = context.applicationContext as? QrCheckerApp ?: return
        if (!isEnabled(app)) return
        app.backgroundScope.launch { save(app, session) }
    }

    /**
     * Убирает копию удалённой сессии.
     *
     * Без этого восстановление возвращало всё, что человек когда-либо удалял: копия
     * переживала сессию, и после переустановки удалённые партии появлялись снова.
     */
    fun scheduleDelete(context: Context, sessionId: String) {
        val app = context.applicationContext as? QrCheckerApp ?: return
        val folder = folder(app) ?: return
        app.backgroundScope.launch {
            lockFor(sessionId).withLock {
                try {
                    DocumentFile.fromTreeUri(app, folder)?.listFiles()
                        ?.filter { belongsTo(it.name, sessionId) }
                        ?.forEach { it.delete() }
                } catch (t: Throwable) {
                    Log.w(TAG, "Can't remove the backup of a deleted session", t)
                }
            }
            // сессии больше нет, и её очередь не нужна: запись, если ещё придёт, увидит,
            // что сессии нет, и ничего не напишет
            locks.remove(sessionId)
        }
    }

    /**
     * Читает все копии из папки. Файлы, которые не разобрались, пропускаются: одна
     * испорченная копия не должна отменять восстановление остальных.
     *
     * Копией считается только файл, в имени которого стоит идентификатор той самой
     * сессии, что внутри. Папкой бэкапа часто выбирают «Загрузки», а туда же ложатся
     * присланные сессии: по одному расширению они восстанавливались как свои, и рядом с
     * партией появлялась её чужая копия. Если у одной сессии копий несколько, берётся
     * самая свежая - иначе выигрывала та, что попалась последней.
     */
    suspend fun readAll(context: Context): List<SessionData> = withContext(Dispatchers.IO) {
        val folder = folder(context) ?: return@withContext emptyList()
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
}
