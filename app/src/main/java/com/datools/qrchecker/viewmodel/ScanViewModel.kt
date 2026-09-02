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

/** Первые байты любого PDF. По ним файл и опознаётся - расширение и тип от системы врут. */
private val PDF_MAGIC = "%PDF-".toByteArray()

/** Что удалось вычитать из выбранного файла. */
sealed interface ParsedFile {
    /** Документ с кодами: имя сессии человек придумывает сам. */
    data class Pdf(val codes: List<String>, val pageCount: Int) : ParsedFile

    /** Готовая сессия: имя, коды и отметки уже внутри, придумывать нечего. */
    data class Session(val session: SessionData) : ParsedFile
}

/** Сколько страниц разобрано из скольких. */
data class ParseProgress(val done: Int, val total: Int)

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
    fun parseSelectedFile(context: Context, uri: Uri, scale: Int = 3) {
        val appContext = context.applicationContext

        // выбрали новый файл, пока разбирался прежний - прежний больше не нужен
        parseJob?.cancel()
        _isLoading.value = true
        _errorMessage.value = null
        _parsed.value = null
        _progress.value = null

        parseJob = viewModelScope.launch {
            try {
                if (looksLikePdf(appContext, uri)) {
                    val result = parsePdfForQRCodes(appContext, uri, scale) { done, total ->
                        _progress.value = ParseProgress(done, total)
                    }
                    if (result.codes.isEmpty()) {
                        _errorMessage.value = appContext.getString(
                            R.string.error_no_codes_in_pdf,
                            result.pageCount,
                            result.renderedSize
                        )
                    } else {
                        _parsed.value = ParsedFile.Pdf(result.codes, result.pageCount)
                    }
                } else {
                    val session = withContext(Dispatchers.IO) {
                        readSessionFile(readTextFromUri(appContext, uri)).session
                    }
                    _parsed.value = ParsedFile.Session(session)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionFileException) {
                _errorMessage.value = e.message
            } catch (t: Throwable) {
                Log.e(TAG, "Can't read the selected file", t)
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
                        is ParsedFile.Pdf -> source.codes
                        is ParsedFile.Session -> source.session.codes
                    }
                    val existing = repoForCheck.findWithSameCodes(codes)
                    if (existing != null) {
                        _conflict.value = existing
                        return@launch
                    }
                }

                val session = when (source) {
                    is ParsedFile.Pdf -> SessionData(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        codes = source.codes,
                        scannedCodes = emptyList()
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

    private suspend fun looksLikePdf(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val head = ByteArray(PDF_MAGIC.size)
                val read = input.read(head)
                read == PDF_MAGIC.size && head.contentEquals(PDF_MAGIC)
            } ?: false
        }
}
