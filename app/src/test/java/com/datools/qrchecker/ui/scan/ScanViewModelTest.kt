package com.datools.qrchecker.ui.scan

import com.datools.qrchecker.ARG_SESSION_ID
import com.datools.qrchecker.data.SessionBackup
import com.datools.qrchecker.data.SessionRepository
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.files.ImageCodeReader
import com.datools.qrchecker.model.SessionData
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScanViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var repository: SessionRepository
    private lateinit var backup: SessionBackup

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        // queries run inline, so a test only has to drive the test dispatcher
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        val appScope = CoroutineScope(dispatcher)
        // no backup folder is chosen, so backups stay off
        backup = SessionBackup(context, db.sessionDao(), appScope)
        repository = SessionRepository(db.sessionDao(), backup)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun TestScope.open(
        codes: List<String>,
        collecting: Boolean = false
    ): Pair<ScanViewModel, List<AnswerKind>> {
        repository.insert(SessionData(id = "s", name = "Batch", codes = codes, scannedCodes = emptyList(), collecting = collecting))
        val viewModel = ScanViewModel(
            SavedStateHandle(mapOf(ARG_SESSION_ID to "s")),
            repository,
            backup,
            ImageCodeReader(ApplicationProvider.getApplicationContext()),
            CoroutineScope(dispatcher)
        )
        val answers = mutableListOf<AnswerKind>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.answers.collect { answers += it }
        }
        runCurrent()
        return viewModel to answers
    }

    @Test
    fun `two codes in view with the pick flipping are answered once each`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A", "B"))
        var t = 10_000L
        repeat(60) { i ->
            viewModel.onFrame(listOf("A", "B"), if (i % 2 == 0) "A" else "B", now = t)
            runCurrent()
            t += 33
        }
        assertEquals(listOf(AnswerKind.MARKED, AnswerKind.MARKED), answers)
        assertEquals(setOf("A", "B"), repository.getById("s")!!.scannedCodes.toSet())
    }

    @Test
    fun `a code not in the session is foreign and nothing is written`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A"))
        viewModel.onFrame(listOf("X"), "X", now = 1_000)
        runCurrent()
        assertEquals(listOf(AnswerKind.FOREIGN), answers)
        assertEquals(listOf("A"), repository.getById("s")!!.codes)
        assertTrue(repository.getById("s")!!.scannedCodes.isEmpty())
    }

    @Test
    fun `a collecting session records a new code`() = runTest(dispatcher) {
        val (viewModel, answers) = open(emptyList(), collecting = true)
        viewModel.onFrame(listOf("N"), "N", now = 1_000)
        runCurrent()
        assertEquals(listOf(AnswerKind.RECORDED), answers)
        assertEquals(listOf("N"), repository.getById("s")!!.scannedCodes)
    }

    @Test
    fun `a mark removed on another screen is seen after reload`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A", "B"))
        viewModel.onFrame(listOf("A"), "A", now = 1_000)
        runCurrent()
        assertEquals(listOf("A"), viewModel.uiState.value.session!!.scannedCodes)

        // the code list unmarks it while the scan screen stays alive underneath
        repository.unmarkScanned("s", "A")
        viewModel.reload()
        runCurrent()
        assertTrue(viewModel.uiState.value.session!!.scannedCodes.isEmpty())

        viewModel.onFrame(listOf("A"), "A", now = 10_000)
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED, AnswerKind.MARKED), answers)
    }

    @Test
    fun `undo takes back a mark and the code still in view is not marked again`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A", "B"))
        viewModel.onFrame(listOf("A"), "A", now = 1_000)
        runCurrent()
        viewModel.undo()
        runCurrent()
        assertTrue(viewModel.uiState.value.session!!.scannedCodes.isEmpty())
        assertTrue(repository.getById("s")!!.scannedCodes.isEmpty())
        assertEquals(null, viewModel.uiState.value.banner)

        viewModel.onFrame(listOf("A"), "A", now = 1_100)
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED), answers)

        // taken away and shown again: a new presentation
        viewModel.onFrame(listOf("A"), "A", now = 10_000)
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED, AnswerKind.MARKED), answers)
    }

    @Test
    fun `undo in a collecting session removes the recorded code`() = runTest(dispatcher) {
        val (viewModel, _) = open(emptyList(), collecting = true)
        viewModel.onFrame(listOf("N"), "N", now = 1_000)
        runCurrent()
        viewModel.undo()
        runCurrent()
        assertTrue(viewModel.uiState.value.session!!.codes.isEmpty())
        val stored = repository.getById("s")!!
        assertTrue(stored.codes.isEmpty())
        assertTrue(repository.binnedCodes("s").isEmpty())
    }

    @Test
    fun `a repeat cannot be undone`() = runTest(dispatcher) {
        val (viewModel, _) = open(listOf("A"))
        viewModel.onFrame(listOf("A"), "A", now = 1_000)
        viewModel.askAgain()
        viewModel.onFrame(listOf("A"), "A", now = 1_100)
        runCurrent()
        assertEquals(AnswerKind.REPEAT, viewModel.uiState.value.banner!!.kind)
        viewModel.undo()
        runCurrent()
        assertEquals(listOf("A"), repository.getById("s")!!.scannedCodes)
    }

    @Test
    fun `one of several codes from an image is answered once picked`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A", "B"))
        viewModel.chooseImageCode("B")
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED), answers)
        assertEquals(listOf("B"), repository.getById("s")!!.scannedCodes)
        assertEquals(null, viewModel.uiState.value.imageChoices)
    }

    @Test
    fun `a tap asks again and the code answers as a repeat`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A"))
        viewModel.onFrame(listOf("A"), "A", now = 1_000)
        viewModel.onFrame(listOf("A"), "A", now = 1_100)
        viewModel.askAgain()
        viewModel.onFrame(listOf("A"), "A", now = 1_200)
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED, AnswerKind.REPEAT), answers)
    }

    @Test
    fun `manual entry is answered even while the code is in view`() = runTest(dispatcher) {
        val (viewModel, answers) = open(listOf("A"))
        viewModel.onFrame(listOf("A"), "A", now = 1_000)
        viewModel.submitManual(" A\n")
        runCurrent()
        assertEquals(listOf(AnswerKind.MARKED, AnswerKind.REPEAT), answers)
    }
}
