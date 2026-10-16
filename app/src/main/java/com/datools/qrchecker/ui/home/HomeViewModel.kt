package com.datools.qrchecker.ui.home

import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.model.SessionSummary
import com.datools.qrchecker.settings.DeleteConfirmation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val deleteConfirmation: DeleteConfirmation
) : ViewModel() {

    /**
     * null until the database answers, so a cold start does not flash "no sessions yet"
     * at someone with a dozen batches.
     */
    val sessions: StateFlow<List<SessionSummary>?> = repository.getSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(sessionId: String) {
        viewModelScope.launch {
            repository.delete(sessionId)
            // the "don't ask again" choice belonged to this session
            deleteConfirmation.forget(sessionId)
        }
    }
}
