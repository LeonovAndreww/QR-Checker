package com.datools.qrchecker.ui.codes

import com.datools.qrchecker.ARG_SESSION_ID
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.settings.DeleteConfirmation
import com.datools.qrchecker.ui.UiText
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "CodeList"

data class CodeListUiState(
    val loading: Boolean = true,
    val session: SessionData? = null,
    /** The user asked not to confirm deletes in this session. */
    val skipDeleteConfirm: Boolean = false
)

@HiltViewModel
class CodeListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
    private val backup: SessionBackup,
    private val deleteConfirmation: DeleteConfirmation
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID])

    private val _uiState = MutableStateFlow(
        CodeListUiState(skipDeleteConfirm = deleteConfirmation.isSkipped(sessionId))
    )
    val uiState: StateFlow<CodeListUiState> = _uiState.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            val session = try {
                repository.getById(sessionId)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't load session $sessionId", t)
                null
            }
            _uiState.update { it.copy(loading = false, session = session) }
        }
    }

    fun skipDeleteConfirmation() {
        deleteConfirmation.skip(sessionId)
        _uiState.update { it.copy(skipDeleteConfirm = true) }
    }

    /**
     * Removes a code from the list it is shown in. From the scanned list of a checking
     * session it only loses its mark; from anywhere else it goes to the bin. A collecting
     * session has no unscanned list to send it back to.
     */
    fun delete(code: String, fromScannedList: Boolean) {
        val collecting = _uiState.value.session?.collecting == true
        val unmark = fromScannedList && !collecting
        viewModelScope.launch {
            try {
                if (unmark) repository.unmarkScanned(sessionId, code) else repository.deleteCode(sessionId, code)
                val updated = repository.getById(sessionId)
                if (updated == null) {
                    _messages.send(UiText.Resource(R.string.delete_code_failed))
                    return@launch
                }
                _uiState.update { it.copy(session = updated) }
                backup.scheduleSave(updated)
                _messages.send(
                    UiText.Resource(if (unmark) R.string.delete_code_unmarked else R.string.delete_code_binned)
                )
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't delete a code from session $sessionId", t)
                _messages.send(UiText.Resource(R.string.delete_code_error_with_reason, listOf(t.message.orEmpty())))
            }
        }
    }
}
