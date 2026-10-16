package com.datools.qrchecker.ui.edit

import com.datools.qrchecker.ARG_SESSION_ID
import com.datools.qrchecker.R
import com.datools.qrchecker.codes.CodeFormat
import com.datools.qrchecker.codes.keepFormats
import com.datools.qrchecker.data.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.files.readCodesFromFiles
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.ui.UiText
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "EditSession"

data class EditSessionUiState(
    val isLoading: Boolean = true,
    /** The session as stored; null until loaded. */
    val original: SessionData? = null,
    val name: String = "",
    val selectedNames: List<String> = emptyList(),
    /** Codes read from the picked files; null when nothing usable was picked. */
    val parsedCodes: List<String>? = null,
    val parsedFormats: Map<String, CodeFormat> = emptyMap(),
    val keptFormats: Set<CodeFormat> = emptySet(),
    val error: UiText? = null,
    /** Binned codes with the time they were binned, newest first. */
    val binned: List<Pair<String, Long>> = emptyList(),
    /** The screen should close: saved, or the session no longer exists. */
    val finished: Boolean = false
) {
    /** What would go into the session: the parsed codes minus the unticked kinds. */
    val keptCodes: List<String>? get() = parsedCodes?.let { keepFormats(it, parsedFormats, keptFormats) }
}

@HiltViewModel
class EditSessionViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
    private val backup: SessionBackup
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID])

    private val _uiState = MutableStateFlow(EditSessionUiState())
    val uiState: StateFlow<EditSessionUiState> = _uiState.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    private var parseJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val loaded = repository.getById(sessionId)
                if (loaded == null) {
                    _uiState.update { it.copy(finished = true) }
                    return@launch
                }
                _uiState.update { it.copy(original = loaded, name = loaded.name) }
                reloadBin()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't load session $sessionId", t)
                _uiState.update { it.copy(error = errorText(R.string.error_loading_session, t)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun setName(name: String) {
        _uiState.update { it.copy(name = name.filterNot { c -> c == '\n' }) }
    }

    fun setKeptFormats(formats: Set<CodeFormat>) {
        _uiState.update { it.copy(keptFormats = formats) }
    }

    fun parseFiles(files: List<Pair<Uri, String>>) {
        if (files.isEmpty()) return
        // Two picks read in parallel would let the slower one win, saving codes of a file
        // whose name is no longer on screen.
        parseJob?.cancel()
        _uiState.update { it.copy(selectedNames = files.map { f -> f.second }, isLoading = true, error = null) }
        parseJob = viewModelScope.launch {
            try {
                val result = readCodesFromFiles(context, files)
                // An empty result is an error, not an empty list: saving it would wipe the
                // session together with its marks.
                if (result.codes.isEmpty()) {
                    _uiState.update {
                        it.copy(parsedCodes = null, error = UiText.Resource(R.string.error_no_codes_in_files))
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            parsedCodes = result.codes,
                            parsedFormats = result.formats,
                            keptFormats = result.codes.mapTo(HashSet()) { c ->
                                result.formats[c] ?: CodeFormat.OTHER
                            }
                        )
                    }
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't read the selected files", t)
                _uiState.update { it.copy(parsedCodes = null, error = errorText(R.string.error_parsing_pdf, t)) }
            } finally {
                // a replaced parse must not stop the spinner of the one that replaced it
                if (isActive) _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    /** [newCodes] is null when only the name changed, so the code rows are left alone. */
    fun save(newCodes: List<String>?) {
        // nothing loaded, nothing to save: the session may have failed to open
        if (_uiState.value.original == null) return
        val name = _uiState.value.name
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                if (newCodes == null) {
                    repository.rename(sessionId, name)
                } else {
                    // the repository keeps the marks of the codes that survive
                    repository.replaceCodes(sessionId, name, newCodes)
                }
                // back up what is stored, not what was on screen
                repository.getById(sessionId)?.let { backup.scheduleSave(it) }
                _uiState.update { it.copy(finished = true) }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't save session $sessionId", t)
                _uiState.update { it.copy(error = errorText(R.string.error_saving_session, t)) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun restoreCode(code: String) = binAction("restore $code") {
        repository.restoreCode(sessionId, code)
        val updated = repository.getById(sessionId)
        _uiState.update { it.copy(original = updated ?: it.original) }
    }

    fun purgeCode(code: String) = binAction("purge $code") {
        repository.purgeCode(sessionId, code)
        _messages.send(UiText.Resource(R.string.bin_deleted))
    }

    fun emptyBin() = binAction("empty the bin") {
        repository.emptyBin(sessionId)
    }

    private fun binAction(what: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                reloadBin()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't $what in session $sessionId", t)
            }
        }
    }

    private suspend fun reloadBin() {
        val binned = try {
            repository.binnedCodes(sessionId)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "Can't read the bin of session $sessionId", t)
            emptyList()
        }
        _uiState.update { it.copy(binned = binned) }
    }

    private fun errorText(id: Int, t: Throwable) = UiText.Resource(id, listOf(t.message.orEmpty()))
}
