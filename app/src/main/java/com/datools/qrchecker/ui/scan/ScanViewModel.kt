package com.datools.qrchecker.ui.scan

import com.datools.qrchecker.ARG_SESSION_ID
import com.datools.qrchecker.codes.normalizeCode
import com.datools.qrchecker.data.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.di.ApplicationScope
import com.datools.qrchecker.model.SessionData
import com.datools.qrchecker.scan.CodePresence
import com.datools.qrchecker.scan.ScanVerdict
import com.datools.qrchecker.scan.verdictFor
import com.datools.qrchecker.util.Outcome
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "Scan"

/** Backups are written after a pause in scanning, not per scan: a batch can be thousands. */
private const val BACKUP_DEBOUNCE_MS = 5_000L

/** The banner stays at least this long, then for as long as its code is in view. */
private const val BANNER_MIN_MS = 1_200L
private const val BANNER_HOLD_CHECK_MS = 150L

enum class AnswerKind { MARKED, RECORDED, REPEAT, FOREIGN, SAVE_FAILED }

data class ScanAnswer(
    val kind: AnswerKind,
    val code: String,
    /** When a repeated code was first scanned; null for other answers or if unknown. */
    val scannedAt: Long? = null,
    /** Tells two identical answers in a row apart, so the banner timer restarts. */
    val id: Long
)

data class ScanUiState(
    val session: SessionData? = null,
    /** Loading is over; a null session now means it does not exist. */
    val loadFinished: Boolean = false,
    val banner: ScanAnswer? = null,
    /**
     * The last answer given to each code on this screen. The frame around a code keeps
     * its colour after the banner is gone.
     */
    val answered: Map<String, AnswerKind> = emptyMap()
)

@HiltViewModel
class ScanViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SessionRepository,
    private val backup: SessionBackup,
    /** Marks are written here, so leaving the screen right after a scan does not lose one. */
    @param:ApplicationScope private val appScope: CoroutineScope
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle[ARG_SESSION_ID])

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    /**
     * Each answer once, for its sound and vibration. An event rather than state: coming
     * back to the screen must not replay the last answer.
     */
    private val _answers = Channel<AnswerKind>(Channel.CONFLATED)
    val answers: Flow<AnswerKind> = _answers.receiveAsFlow()

    private val presence = CodePresence()
    private var answerCount = 0L
    private var bannerJob: Job? = null
    private var backupJob: Job? = null
    private var touched = false

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
            _uiState.update { it.copy(session = session, loadFinished = true) }
        }
    }

    /**
     * One decoded camera frame: [visible] are all codes in it, [candidate] the one picked
     * for an answer. Called on the main thread, so frames cannot interleave.
     */
    fun onFrame(visible: List<String>, candidate: String?, now: Long = System.currentTimeMillis()) {
        if (presence.onFrame(visible, candidate, now) && candidate != null) answer(candidate)
    }

    /** A code picked or typed in the manual entry dialog is always answered. */
    fun submitManual(rawCode: String) {
        val code = normalizeCode(rawCode)
        if (code.isNotEmpty()) answer(code)
    }

    /** A tap on the preview asks again about the code in view. */
    fun askAgain() = presence.forgetAnswers()

    /**
     * The home list is ordered by opening time. The screen calls this once its transition
     * is over, so the session does not jump to the top while home is still visible.
     */
    fun touchOpened() {
        if (touched) return
        touched = true
        appScope.launch {
            try {
                repository.touchOpened(sessionId)
            } catch (t: Throwable) {
                Log.w(TAG, "Can't mark session $sessionId as opened", t)
            }
        }
    }

    override fun onCleared() {
        // left before the transition finished: it was still an opening
        touchOpened()
        // the debounced backup dies with viewModelScope; the last marks must not
        _uiState.value.session?.let { backup.scheduleSave(it) }
    }

    private fun answer(code: String) {
        val current = _uiState.value.session ?: return
        when (val verdict = verdictFor(current, code)) {
            is ScanVerdict.Foreign -> show(AnswerKind.FOREIGN, code)
            is ScanVerdict.Repeat -> show(AnswerKind.REPEAT, code, verdict.at)
            is ScanVerdict.Recorded -> {
                val at = System.currentTimeMillis()
                updateSession {
                    it.copy(
                        codes = it.codes + code,
                        scannedCodes = it.scannedCodes + code,
                        scanTimes = it.scanTimes.orEmpty() + (code to at)
                    )
                }
                show(AnswerKind.RECORDED, code)
                write(code, at, recorded = true) { repository.recordScanned(sessionId, code, at) }
            }
            is ScanVerdict.Marked -> {
                // the same time goes to the database and to the screen state, which the
                // backup is taken from
                val at = System.currentTimeMillis()
                updateSession {
                    it.copy(
                        scannedCodes = it.scannedCodes + code,
                        scanTimes = it.scanTimes.orEmpty() + (code to at)
                    )
                }
                show(AnswerKind.MARKED, code)
                write(code, at, recorded = false) { repository.markScanned(sessionId, code, at) }
            }
        }
    }

    /**
     * The answer is shown before the write on purpose: on a stream of boxes it has to be
     * instant. A failed write takes the shown success back, or the user would put away a
     * box whose mark never reached the database.
     */
    private fun write(code: String, at: Long, recorded: Boolean, block: suspend () -> Unit) {
        appScope.launch {
            try {
                block()
            } catch (t: Throwable) {
                Log.e(TAG, "Can't save the mark of $code", t)
                // a no-op once the screen is gone: nobody to tell
                viewModelScope.launch { undoFailedWrite(code, at, recorded) }
            }
        }
    }

    /** Takes back exactly the mark that was added, unless it has changed since. */
    private fun undoFailedWrite(code: String, at: Long, recorded: Boolean) {
        val current = _uiState.value.session ?: return
        if (current.scanTimes?.get(code) != at) return
        updateSession {
            it.copy(
                codes = if (recorded) it.codes - code else it.codes,
                scannedCodes = it.scannedCodes - code,
                scanTimes = it.scanTimes.orEmpty() - code
            )
        }
        show(AnswerKind.SAVE_FAILED, code)
    }

    private fun updateSession(change: (SessionData) -> SessionData) {
        _uiState.update { state -> state.copy(session = state.session?.let(change)) }
        scheduleBackup()
    }

    private fun scheduleBackup() {
        backupJob?.cancel()
        backupJob = viewModelScope.launch {
            delay(BACKUP_DEBOUNCE_MS)
            _uiState.value.session?.let { backup.autoSave(it) }
        }
    }

    private fun show(kind: AnswerKind, code: String, scannedAt: Long? = null) {
        val banner = ScanAnswer(kind, code, scannedAt, id = ++answerCount)
        _uiState.update { it.copy(banner = banner, answered = it.answered + (code to kind)) }
        _answers.trySend(kind)

        // A newer banner replaces this one and its timer. The banner answers about the box
        // in front of the camera, so it stays while that box does.
        bannerJob?.cancel()
        bannerJob = viewModelScope.launch {
            delay(BANNER_MIN_MS)
            while (presence.isPresent(code, System.currentTimeMillis())) delay(BANNER_HOLD_CHECK_MS)
            _uiState.update { if (it.banner?.id == banner.id) it.copy(banner = null) else it }
        }
    }
}

/** How an answer feels: the sound and vibration it comes with. */
val AnswerKind.outcome: Outcome
    get() = when (this) {
        AnswerKind.MARKED, AnswerKind.RECORDED -> Outcome.SUCCESS
        AnswerKind.REPEAT -> Outcome.REPEAT
        AnswerKind.FOREIGN, AnswerKind.SAVE_FAILED -> Outcome.FAILURE
    }
