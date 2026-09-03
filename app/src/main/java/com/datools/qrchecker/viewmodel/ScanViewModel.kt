package com.datools.qrchecker.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.util.SessionBackup
import com.datools.qrchecker.util.SessionFileException
import com.datools.qrchecker.util.parseCodeList
import com.datools.qrchecker.util.parseImageForCodes
import com.datools.qrchecker.util.parsePdfForQRCodes
import com.datools.qrchecker.util.readSessionFile
import com.datools.qrchecker.util.readTextFromUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "QRChecker"

/**
 * Вид файла определяется по первым байтам, а не по имени и не по типу от системы: своё
 * расширение .qrcheck система не знает и отдаёт файл как octet-stream, а документ из
 * мессенджера приезжает и вовсе без имени.
 */
private enum class FileKind { PDF, IMAGE, TEXT }

private val PDF_MAGIC = "%PDF-".toByteArray()

private val IMAGE_MAGICS = listOf(
    byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),               // PNG
    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),   // JPEG
    byteArrayOf(0x47, 0x49, 0x46, 0x38),                        // GIF
    byteArrayOf(0x42, 0x4D),                                    // BMP
    byteArrayOf(0x52, 0x49, 0x46, 0x46)                         // RIFF, он же WebP
)

/** Что удалось вычитать из выбранных файлов. */
sealed interface ParsedFile {
    /**
     * Коды из одного или нескольких источников: имя сессии человек придумывает сам.
     *
     * Отметки здесь не всегда пустые: среди выбранных файлов мог оказаться файл сессии, и
     * терять его отметки только потому, что рядом лежит ещё документ, нельзя.
     */
    data class Codes(
        val codes: List<String>,
        val sources: List<SourceSummary>,
        val scanned: List<String> = emptyList(),
        val scanTimes: Map<String, Long> = emptyMap()
    ) : ParsedFile

    /** Готовая сессия: имя, коды и отметки уже внутри, придумывать нечего. */
    data class Session(val session: SessionData) : ParsedFile
}

/** Сколько кодов дал каждый выбранный файл - чтобы было видно, какой приехал пустым. */
data class SourceSummary(val name: String, val codes: Int)

/** Какой файл разбирается и, если это документ, какая страница. */
data class ParseProgress(
    val fileIndex: Int,
    val fileCount: Int,
    val fileName: String,
    val page: Int = 0,
    val pageCount: Int = 0
)

class ScanViewModel : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> get() = _isLoading

    private val _progress = mutableStateOf<ParseProgress?>(null)
    val progress: State<ParseProgress?> get() = _progress

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> get() = _errorMessage

    private val _parsed = mutableStateOf<ParsedFile?>(null)
    val parsed: State<ParsedFile?> get() = _parsed

    private val _createdSessionId = mutableStateOf<String?>(null)
    val createdSessionId: State<String?> get() = _createdSessionId

    /** Сессия на устройстве с тем же набором кодов, если она нашлась при сохранении. */
    private val _conflict = mutableStateOf<SessionData?>(null)
    val conflict: State<SessionData?> get() = _conflict

    private val _mergedMarks = mutableStateOf<Int?>(null)
    val mergedMarks: State<Int?> get() = _mergedMarks

    private var parseJob: Job? = null

    /**
     * Разбирает выбранный файл сразу, не дожидаясь кнопки.
     *
     * Ждать нажатия было нечестно: человек выбрал документ, экран ничем не отличается от
     * прежнего, и понять, что работа ещё не начата, неоткуда.
     *
     * Тип определяется по содержимому. Своё расширение .qrcheck система не знает и отдаёт
     * файл как octet-stream, а PDF из мессенджера может приехать вообще без имени.
     */
    fun parseSelectedFiles(
        context: Context,
        files: List<Pair<Uri, String>>,
        scale: Int = 3
    ) {
        if (files.isEmpty()) return
        val appContext = context.applicationContext

        // выбрали новую пачку, пока разбиралась прежняя - прежняя больше не нужна
        parseJob?.cancel()
        _isLoading.value = true
        _errorMessage.value = null
        _parsed.value = null
        _progress.value = null

        parseJob = viewModelScope.launch {
            try {
                // файл сессии несёт имя, отметки и время, и смешивать его с чужими кодами
                // нечего: выбран один такой файл - открываем сессию целиком
                if (files.size == 1) {
                    val session = readSessionOrNull(appContext, files.first().first)
                    if (session != null) {
                        _parsed.value = ParsedFile.Session(session)
                        return@launch
                    }
                }

                val codes = LinkedHashSet<String>()
                val sources = ArrayList<SourceSummary>(files.size)
                val scanned = LinkedHashSet<String>()
                val scanTimes = HashMap<String, Long>()

                files.forEachIndexed { index, (uri, name) ->
                    _progress.value = ParseProgress(index, files.size, name)
                    val before = codes.size

                    when (kindOf(appContext, uri)) {
                        FileKind.PDF -> {
                            val result = parsePdfForQRCodes(appContext, uri, scale) { done, total ->
                                _progress.value =
                                    ParseProgress(index, files.size, name, done, total)
                            }
                            codes += result.codes
                        }

                        FileKind.IMAGE -> codes += parseImageForCodes(appContext, uri)

                        FileKind.TEXT -> {
                            // файл сессии среди прочих отдаёт и коды, и отметки: раньше он
                            // уходил в разбор списка, и отметки пропадали молча
                            val session = readSessionOrNull(appContext, uri)
                            if (session != null) {
                                codes += session.codes
                                scanned += session.scannedCodes
                                session.scanTimes?.let { scanTimes.putAll(it) }
                            } else {
                                codes += withContext(Dispatchers.IO) {
                                    parseCodeList(readTextFromUri(appContext, uri))
                                }
                            }
                        }
                    }

                    sources += SourceSummary(name, codes.size - before)
                }

                if (codes.isEmpty()) {
                    _errorMessage.value = appContext.getString(R.string.error_no_codes_in_files)
                } else {
                    _parsed.value = ParsedFile.Codes(
                        codes = codes.toList(),
                        sources = sources,
                        scanned = scanned.filter { it in codes },
                        scanTimes = scanTimes.filterKeys { it in codes }
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionFileException) {
                _errorMessage.value = e.message
            } catch (t: Throwable) {
                Log.e(TAG, "Can't read the selected files", t)
                _errorMessage.value =
                    appContext.getString(R.string.error_parsing_pdf, t.message ?: "")
            } finally {
                _isLoading.value = false
                _progress.value = null
            }
        }
    }

    /** Бросает разбор, когда человек передумал ждать. */
    fun cancelParsing() {
        parseJob?.cancel()
        parseJob = null
        _isLoading.value = false
        _progress.value = null
    }

    /**
     * Сохраняет разобранное как новую сессию. Ничего не разбирает заново - к этому моменту
     * коды уже прочитаны, и повторять минуту работы на нажатие кнопки незачем.
     */
    fun createSession(context: Context, name: String, force: Boolean = false) {
        val source = _parsed.value ?: return
        if (_isLoading.value) return
        val appContext = context.applicationContext

        _isLoading.value = true
        _errorMessage.value = null
        _createdSessionId.value = null

        viewModelScope.launch {
            try {
                val repoForCheck = SessionRepository(appContext)
                if (!force) {
                    // тот же файл, открытый второй раз, - это не вторая партия, а та же
                    val codes = when (source) {
                        is ParsedFile.Codes -> source.codes
                        is ParsedFile.Session -> source.session.codes
                    }
                    val existing = repoForCheck.findWithSameCodes(codes)
                    if (existing != null) {
                        _conflict.value = existing
                        return@launch
                    }
                }

                val session = when (source) {
                    is ParsedFile.Codes -> SessionData(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        codes = source.codes,
                        scannedCodes = source.scanned,
                        scanTimes = source.scanTimes
                    )
                    // у открытой сессии свой идентификатор: новый, чтобы файл, открытый
                    // дважды, не затирал уже лежащую на устройстве сессию
                    is ParsedFile.Session -> source.session.copy(
                        id = UUID.randomUUID().toString(),
                        name = name
                    )
                }

                val repo = SessionRepository(appContext)
                repo.migrateFromSharedPrefsIfNeeded()
                repo.insert(session)
                SessionBackup.autoSave(appContext, session)
                _createdSessionId.value = session.id
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't create a session", t)
                _errorMessage.value =
                    appContext.getString(R.string.error_saving_session, t.message ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Переносит отметки открытого файла в уже существующую сессию с теми же кодами. */
    fun mergeIntoExisting(context: Context) {
        val target = _conflict.value ?: return
        val marks = (_parsed.value as? ParsedFile.Session)?.session?.scannedCodes.orEmpty()
        val appContext = context.applicationContext
        _conflict.value = null
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val repo = SessionRepository(appContext)
                _mergedMarks.value = repo.mergeScanned(target.id, marks)
                repo.getById(target.id)?.let { SessionBackup.autoSave(appContext, it) }
                _createdSessionId.value = target.id
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't merge into the existing session", t)
                _errorMessage.value =
                    appContext.getString(R.string.error_saving_session, t.message ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissConflict() {
        _conflict.value = null
    }

    fun clearMergedMarks() {
        _mergedMarks.value = null
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearParsed() {
        _parsed.value = null
    }

    fun clearCreatedSessionId() {
        _createdSessionId.value = null
    }

    private suspend fun kindOf(context: Context, uri: Uri): FileKind = withContext(Dispatchers.IO) {
        val head = context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(8)
            val read = input.read(buffer)
            if (read <= 0) ByteArray(0) else buffer.copyOf(read)
        } ?: ByteArray(0)

        when {
            head.startsWith(PDF_MAGIC) -> FileKind.PDF
            IMAGE_MAGICS.any { head.startsWith(it) } -> FileKind.IMAGE
            else -> FileKind.TEXT
        }
    }

    /** Пробует прочитать файл как сессию. null - значит это что-то другое. */
    private suspend fun readSessionOrNull(context: Context, uri: Uri): SessionData? =
        withContext(Dispatchers.IO) {
            try {
                if (kindOf(context, uri) != FileKind.TEXT) return@withContext null
                readSessionFile(readTextFromUri(context, uri)).session
            } catch (t: Throwable) {
                null
            }
        }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
}
