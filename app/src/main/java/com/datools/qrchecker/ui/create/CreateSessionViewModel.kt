package com.datools.qrchecker.ui.create

import com.datools.qrchecker.R
import com.datools.qrchecker.codes.CodeFormat
import com.datools.qrchecker.codes.keepFormats
import com.datools.qrchecker.data.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.files.SessionFileException
import com.datools.qrchecker.files.SourceSummary
import com.datools.qrchecker.files.readCodesFromFiles
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.ui.UiText
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "CreateSession"

/** What came out of the picked files. */
sealed interface ParsedFile {
    /**
     * Codes from one or more sources; the user names the session. Marks can still be
     * present: a session file may have been picked together with a document.
     */
    data class Codes(
        val codes: List<String>,
        val sources: List<SourceSummary>,
        val scanned: List<String> = emptyList(),
        val scanTimes: Map<String, Long> = emptyMap(),
        /** How each code was printed, so an unwanted kind can be unticked. */
        val formats: Map<String, CodeFormat> = emptyMap()
    ) : ParsedFile

    /** A whole session from its file: name, codes and marks are all there. */
    data class Session(val session: SessionData) : ParsedFile
}

/** Which file is being read and, for a document, which page. */
data class ParseProgress(
    val fileIndex: Int,
    val fileCount: Int,
    val fileName: String,
    val page: Int = 0,
    val pageCount: Int = 0
)

data class CreateSessionUiState(
    /** Reading files or saving the session. */
    val isLoading: Boolean = false,
    /** Reading files specifically; saving shows no "reading" text. */
    val isParsing: Boolean = false,
    val progress: ParseProgress? = null,
    val parsed: ParsedFile? = null,
    val error: UiText? = null,
    /** An existing session with the same codes, found while saving. */
    val conflict: SessionData? = null,
    /** How many marks a merge added; shown before moving on. */
    val mergedMarks: Int? = null,
    /** Set once the session exists; the screen navigates and clears it. */
    val createdSessionId: String? = null
)

@HiltViewModel
class CreateSessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: SessionRepository,
    private val backup: SessionBackup
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    private var parseJob: Job? = null

    /**
     * Reads the picked files right away rather than on a button: nothing on screen would
     * otherwise tell the user the work has not started.
     */
    fun parseFiles(files: List<Pair<Uri, String>>) {
        if (files.isEmpty()) return

        // a newer pick replaces the one still being read
        parseJob?.cancel()
        _uiState.update {
            it.copy(isLoading = true, isParsing = true, error = null, parsed = null, progress = null)
        }

        parseJob = viewModelScope.launch {
            try {
                val result = readCodesFromFiles(context, files) { i, n, name, page, pages ->
                    if (isActive) {
                        _uiState.update { it.copy(progress = ParseProgress(i, n, name, page, pages)) }
                    }
                }
                val parsed: ParsedFile? = when {
                    // an empty collecting session file is a session, not "no codes"
                    result.codes.isEmpty() && !result.collecting -> null
                    result.sessionName != null -> ParsedFile.Session(
                        SessionData(
                            id = "",
                            name = result.sessionName,
                            codes = result.codes,
                            scannedCodes = result.scanned,
                            scanTimes = result.scanTimes,
                            collecting = result.collecting
                        )
                    )
                    else -> ParsedFile.Codes(
                        codes = result.codes,
                        sources = result.sources,
                        scanned = result.scanned,
                        scanTimes = result.scanTimes,
                        formats = result.formats
                    )
                }
                _uiState.update {
                    it.copy(
                        parsed = parsed,
                        error = if (parsed == null) UiText.Resource(R.string.error_no_codes_in_files) else null
                    )
                }
            } catch (c: CancellationException) {
                throw c
            } catch (e: SessionFileException) {
                _uiState.update { it.copy(error = UiText.Resource(e.messageRes)) }
            } catch (t: Throwable) {
                Log.e(TAG, "Can't read the selected files", t)
                _uiState.update {
                    it.copy(error = UiText.Resource(R.string.error_parsing_pdf, listOf(t.message.orEmpty())))
                }
            } finally {
                // a replaced parse was cancelled before the new one started and must not
                // switch off the new one's progress
                if (isActive) {
                    _uiState.update { it.copy(isLoading = false, isParsing = false, progress = null) }
                }
            }
        }
    }

    fun cancelParsing() {
        parseJob?.cancel()
        parseJob = null
        _uiState.update { it.copy(isLoading = false, isParsing = false, progress = null) }
    }

    /**
     * Saves what was parsed as a new session. [keepFormats] are the code kinds the user
     * left ticked; empty means all. [force] skips the same-codes check.
     */
    fun createSession(name: String, force: Boolean = false, keepFormats: Set<CodeFormat> = emptySet()) {
        val current = _uiState.value
        val source = current.parsed?.let { keep(it, keepFormats) } ?: return
        if (current.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, createdSessionId = null) }

        viewModelScope.launch {
            try {
                if (!force) {
                    // the same document opened again is the same batch, not a second one
                    val codes = when (source) {
                        is ParsedFile.Codes -> source.codes
                        is ParsedFile.Session -> source.session.codes
                    }
                    repository.findWithSameCodes(codes)?.let { existing ->
                        _uiState.update { it.copy(conflict = existing) }
                        return@launch
                    }
                }

                val free = repository.freeName(name)
                val session = when (source) {
                    is ParsedFile.Codes -> SessionData(
                        id = UUID.randomUUID().toString(),
                        name = free,
                        codes = source.codes,
                        scannedCodes = source.scanned,
                        scanTimes = source.scanTimes
                    )
                    // a fresh id, so a file opened twice never overwrites a stored session
                    is ParsedFile.Session -> source.session.copy(id = UUID.randomUUID().toString(), name = free)
                }
                repository.insert(session)
                backup.autoSave(session)
                _uiState.update { it.copy(createdSessionId = session.id) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't create a session", t)
                _uiState.update { it.copy(error = savingError(t)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** An empty session that records whatever is scanned. */
    fun createCollectingSession(name: String) {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, error = null, createdSessionId = null) }

        viewModelScope.launch {
            try {
                val session = SessionData(
                    id = UUID.randomUUID().toString(),
                    name = repository.freeName(name),
                    codes = emptyList(),
                    scannedCodes = emptyList(),
                    scanTimes = emptyMap(),
                    collecting = true
                )
                repository.insert(session)
                backup.autoSave(session)
                _uiState.update { it.copy(createdSessionId = session.id) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't create a collecting session", t)
                _uiState.update { it.copy(error = savingError(t)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** Copies the marks of the parsed file into the existing session with the same codes. */
    fun mergeIntoExisting() {
        val state = _uiState.value
        val target = state.conflict ?: return
        // a session file picked together with a document arrives as Codes, marks included
        val (marks, times) = when (val source = state.parsed) {
            is ParsedFile.Session -> source.session.scannedCodes to source.session.scanTimes.orEmpty()
            is ParsedFile.Codes -> source.scanned to source.scanTimes
            null -> emptyList<String>() to emptyMap()
        }
        _uiState.update { it.copy(conflict = null, isLoading = true) }

        viewModelScope.launch {
            try {
                val added = repository.mergeScanned(target.id, marks, times)
                repository.getById(target.id)?.let { backup.autoSave(it) }
                _uiState.update { it.copy(mergedMarks = added, createdSessionId = target.id) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't merge into the existing session", t)
                _uiState.update { it.copy(error = savingError(t)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun dismissConflict() = _uiState.update { it.copy(conflict = null) }

    fun clearMergedMarks() = _uiState.update { it.copy(mergedMarks = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearParsed() = _uiState.update { it.copy(parsed = null) }

    fun clearCreatedSessionId() = _uiState.update { it.copy(createdSessionId = null) }

    private fun savingError(t: Throwable) =
        UiText.Resource(R.string.error_saving_session, listOf(t.message.orEmpty()))

    /** Keeps only the code kinds the user left ticked, with their marks. */
    private fun keep(source: ParsedFile, formats: Set<CodeFormat>): ParsedFile {
        if (formats.isEmpty() || source !is ParsedFile.Codes) return source
        val codes = keepFormats(source.codes, source.formats, formats)
        if (codes.size == source.codes.size) return source
        val kept = codes.toHashSet()
        return source.copy(
            codes = codes,
            scanned = source.scanned.filter { it in kept },
            scanTimes = source.scanTimes.filterKeys { it in kept }
        )
    }
}
