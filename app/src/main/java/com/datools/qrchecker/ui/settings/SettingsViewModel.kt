package com.datools.qrchecker.ui.settings

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datools.qrchecker.R
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.ui.UiText
import com.datools.qrchecker.util.SessionBackup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BackupUiState(
    val folderName: String? = null,
    val autoEnabled: Boolean = false,
    val restoring: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SessionRepository,
    private val backup: SessionBackup
) : ViewModel() {

    private val _backupState = MutableStateFlow(
        BackupUiState(folderName = backup.folderName(), autoEnabled = backup.isEnabled())
    )
    val backupState: StateFlow<BackupUiState> = _backupState.asStateFlow()

    private val _messages = Channel<UiText>(Channel.BUFFERED)
    /** One-off messages for the snackbar. */
    val messages: Flow<UiText> = _messages.receiveAsFlow()

    fun onFolderPicked(uri: Uri) {
        if (backup.setFolder(uri)) {
            refresh()
        } else {
            _messages.trySend(UiText.Resource(R.string.backup_folder_failed))
        }
    }

    fun forgetFolder() {
        backup.clearFolder()
        refresh()
    }

    /** The switch is never locked: a locked one looks broken. Without a folder it explains why. */
    fun setAutoEnabled(enabled: Boolean) {
        if (_backupState.value.folderName == null) {
            _messages.trySend(UiText.Resource(R.string.backup_pick_folder_first))
            return
        }
        backup.setEnabled(enabled)
        refresh()
    }

    /** Brings back only sessions missing on the device, so a second restore overwrites nothing. */
    fun restore() {
        if (_backupState.value.restoring) return
        _backupState.update { it.copy(restoring = true) }
        viewModelScope.launch {
            try {
                val known = repository.existingIds()
                val missing = backup.readAll().filter { it.id !in known }
                for (session in missing) repository.insert(session)
                _messages.send(UiText.Resource(R.string.backup_restored, listOf(missing.size)))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e("Settings", "Can't restore from the backup folder", t)
                _messages.send(UiText.Resource(R.string.backup_restore_failed))
            } finally {
                _backupState.update { it.copy(restoring = false) }
            }
        }
    }

    private fun refresh() {
        _backupState.update {
            it.copy(folderName = backup.folderName(), autoEnabled = backup.isEnabled())
        }
    }
}
