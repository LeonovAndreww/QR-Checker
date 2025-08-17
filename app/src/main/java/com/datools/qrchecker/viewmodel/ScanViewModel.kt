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
    val qrList: List<String> get() = _qrList

    private val _errorMessage = mutableStateOf<String?>(null)
    val errorMessage get() = _errorMessage

    // Новое: id только что созданной сессии (null пока не создана)
    private val _createdSessionId = mutableStateOf<String?>(null)
    val createdSessionId: State<String?> get() = _createdSessionId

    fun setSession(name: String?, uri: Uri?) {
        sessionName.value = name
        pdfUriString.value = uri?.toString()
        state["sessionName"] = name
        state["pdfUri"] = uri?.toString()
    }

    /**
     * Запускает парсинг PDF (внутри ViewModel).
     * По завершении создаёт SessionData, сохраняет через SessionManager и выставляет createdSessionId.
     */
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
                    // сохраняем текст ошибки для UI
                    _errorMessage.value = e.message ?: "Unknown error"
                    emptyList()
                }
            }

            // результат в state
            _qrList.addAll(codes)

            // создаём и сохраняем сессию — это теперь делает ViewModel
            val sessionId = java.util.UUID.randomUUID().toString()
            val session = SessionData(
                id = sessionId,
                name = sessionNameStr,
                codes = codes.toMutableList(),
                scannedCodes = mutableListOf()
            )
            try {
                val repo = com.datools.qrchecker.data.SessionRepository(context)
                // если нужно, сначала мигрируем старые prefs (опционально)
                repo.migrateFromSharedPrefsIfNeeded()
                repo.insert(session)
                _createdSessionId.value = sessionId
            } catch (t: Throwable) {
                _errorMessage.value = "Can't save session: ${t.message}"
            }

            _isLoading.value = false
        }
    }

    // очистить значение после навигации (чтобы не навигировать дважды)
    fun clearCreatedSessionId() {
        _createdSessionId.value = null
    }

    fun scanPdf(context: Context, uri: Uri, scale: Int = 3) {
        // старый метод можно оставить, но теперь createSessionFromPdf — основной путь для CreateScreen
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

    fun startScanIfNeeded(context: Context) {
        pdfUriString.value?.let { uriStr ->
            val uri = uriStr.toUri()
            scanPdf(context, uri)
        }
    }
}
