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
import com.datools.qrchecker.util.SourceSummary
import com.datools.qrchecker.util.readCodesFromFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "QRChecker"

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
                val result = readCodesFromFiles(appContext, files, scale) { i, n, name, page, pages ->
                    _progress.value = ParseProgress(i, n, name, page, pages)
                }

                when {
                    result.codes.isEmpty() ->
                        _errorMessage.value =
                            appContext.getString(R.string.error_no_codes_in_files)

                    // выбран ровно один файл сессии: имя, отметки и время уже внутри,
                    // придумывать нечего
                    result.sessionName != null -> _parsed.value = ParsedFile.Session(
                        SessionData(
                            id = "",
                            name = result.sessionName,
                            codes = result.codes,
                            scannedCodes = result.scanned,
                            scanTimes = result.scanTimes
                        )
                    )

                    else -> _parsed.value = ParsedFile.Codes(
                        codes = result.codes,
                        sources = result.sources,
                        scanned = result.scanned,
                        scanTimes = result.scanTimes
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionFileException) {
                _errorMessage.value = appContext.getString(e.messageRes)
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

}
