package com.datools.qrchecker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.util.parsePdfForQRCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.State

class ScanViewModel(private val state: SavedStateHandle) : ViewModel() {

    var sessionName = mutableStateOf(state.get<String>("sessionName"))
        private set

    var pdfUriString = mutableStateOf(state.get<String>("pdfUri"))
        private set

    private val _isLoading = mutableStateOf(false)
    val isLoading get() = _isLoading

    private val _qrList = mutableStateListOf<String>()
    @Suppress("unused")
    val qrList: List<String> get() = _qrList

    private val _errorMessage = mutableStateOf<String?>(null)
    @Suppress("unused")
    val errorMessage get() = _errorMessage
    private val _createdSessionId = mutableStateOf<String?>(null)
    val createdSessionId: State<String?> get() = _createdSessionId

    @Suppress("unused")
    fun setSession(name: String?, uri: Uri?) {
        sessionName.value = name
        pdfUriString.value = uri?.toString()
        state["sessionName"] = name
        state["pdfUri"] = uri?.toString()
    }

    fun createSessionFromPdf(context: Context, sessionNameStr: String, uri: Uri, scale: Int = 3) {
        _isLoading.value = true
        _qrList.clear()
        _errorMessage.value = null
        _createdSessionId.value = null

        viewModelScope.launch {
            val codes = withContext(Dispatchers.IO) {
                try {
                    parsePdfForQRCodes(context, uri, scale)
                } catch (e: Exception) {
                    _errorMessage.value = e.message ?: "Unknown error"
                    emptyList()
                }
            }

            _qrList.addAll(codes)

            val sessionId = java.util.UUID.randomUUID().toString()
            val session = SessionData(
                id = sessionId,
                name = sessionNameStr,
                codes = codes.toMutableList(),
                scannedCodes = mutableListOf()
            )
            try {
                val repo = com.datools.qrchecker.data.SessionRepository(context)
                repo.migrateFromSharedPrefsIfNeeded()
                repo.insert(session)
                _createdSessionId.value = sessionId
            } catch (t: Throwable) {
                _errorMessage.value = "Can't save session: ${t.message}"
            }

            _isLoading.value = false
        }
    }

    // clear the value after navigation (to avoid navigating twice)
    fun clearCreatedSessionId() {
        _createdSessionId.value = null
    }

    fun scanPdf(context: Context, uri: Uri, scale: Int = 3) {
        _isLoading.value = true
        _qrList.clear()
        _errorMessage.value = null

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    parsePdfForQRCodes(context, uri, scale)
                } catch (e: Exception) {
                    _errorMessage.value = e.message
                    emptyList()
                }
            }
            _qrList.addAll(result)
            _isLoading.value = false
        }
    }

    @Suppress("unused")
    fun startScanIfNeeded(context: Context) {
        pdfUriString.value?.let { uriStr ->
            val uri = uriStr.toUri()
            scanPdf(context, uri)
        }
    }
}