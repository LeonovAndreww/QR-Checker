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
import com.datools.qrchecker.util.parsePdfForQRCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

private const val TAG = "QRChecker"

class ScanViewModel : ViewModel() {

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> get() = _isLoading

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage: State<String?> get() = _errorMessage

    private val _createdSessionId = mutableStateOf<String?>(null)
    val createdSessionId: State<String?> get() = _createdSessionId

    /**
     * Parses the selected PDF and stores a new session.
     *
     * A session is created only when the document actually yielded codes — otherwise the
     * user would end up with an empty session and no explanation of what went wrong.
     */
    fun createSessionFromPdf(
        context: Context,
        sessionName: String,
        uri: Uri,
        scale: Int = 3
    ) {
        if (_isLoading.value) return
        val appContext = context.applicationContext

        _isLoading.value = true
        _errorMessage.value = null
        _createdSessionId.value = null

        viewModelScope.launch {
            try {
                // parsePdfForQRCodes switches to Dispatchers.IO on its own
                val codes = parsePdfForQRCodes(appContext, uri, scale)

                if (codes.isEmpty()) {
                    _errorMessage.value = appContext.getString(R.string.error_no_codes_in_pdf)
                    return@launch
                }

                val session = SessionData(
                    id = UUID.randomUUID().toString(),
                    name = sessionName,
                    codes = codes,
                    scannedCodes = emptyList()
                )

                val repo = SessionRepository(appContext)
                repo.migrateFromSharedPrefsIfNeeded()
                repo.insert(session)
                _createdSessionId.value = session.id
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                Log.e(TAG, "Can't create a session from the selected PDF", t)
                _errorMessage.value =
                    appContext.getString(R.string.error_parsing_pdf, t.message ?: "")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // clear the value after navigation (to avoid navigating twice)
    fun clearCreatedSessionId() {
        _createdSessionId.value = null
    }
}
