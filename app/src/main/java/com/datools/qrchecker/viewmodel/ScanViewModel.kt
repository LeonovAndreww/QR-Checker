package com.datools.qrchecker.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.datools.qrchecker.util.parsePdfForQRCodes
import kotlinx.coroutines.launch
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.core.net.toUri

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

    fun setSession(name: String?, uri: Uri?) {
        sessionName.value = name
        pdfUriString.value = uri?.toString()
        state["sessionName"] = name
        state["pdfUri"] = uri?.toString()
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

    fun startScanIfNeeded(context: Context) {
        pdfUriString.value?.let { uriStr ->
            val uri = uriStr.toUri()
            scanPdf(context, uri)
        }
    }
}