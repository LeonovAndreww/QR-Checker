package com.datools.qrchecker.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.data.room.SessionCodeEntity
import com.datools.qrchecker.data.room.SessionDao
import com.datools.qrchecker.data.room.SessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.sessionDao()
    }

    @After
    fun tearDown() = db.close()

    private fun session(id: String = "s", collecting: Boolean = false) =
        SessionEntity(id = id, name = "Batch", createdAt = 1, openedAt = 1, collecting = collecting)

    private fun code(value: String, position: Int, scanned: Boolean = false, at: Long? = null) =
        SessionCodeEntity(sessionId = "s", code = value, scanned = scanned, position = position, scannedAt = at)

    @Test
    fun `marking a code is idempotent and keeps the first time`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0)))
        assertEquals(1, dao.markScanned("s", "A", 100))
        assertEquals(0, dao.markScanned("s", "A", 200))
        assertEquals(100L, dao.getCodes("s").single().scannedAt)
    }

    @Test
    fun `binned code keeps its mark and comes back with it`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0, scanned = true, at = 5)))
        dao.moveCodeToBin("s", "A", 10)
        assertTrue(dao.getCodes("s").isEmpty())
        assertEquals(10L, dao.getDeletedCodes("s").single().deletedAt)

        dao.restoreCode("s", "A")
        val back = dao.getCodes("s").single()
        assertTrue(back.scanned)
        assertEquals(5L, back.scannedAt)
    }

    @Test
    fun `replacing the codes leaves the bin alone`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0), code("B", 1)))
        dao.moveCodeToBin("s", "B", 10)
        dao.renameAndReplaceCodes("s", "Renamed", listOf(code("C", 0)))

        assertEquals(listOf("C"), dao.getCodes("s").map { it.code })
        assertEquals(listOf("B"), dao.getDeletedCodes("s").map { it.code })
        assertEquals("Renamed", dao.getSession("s")?.name)
    }

    @Test
    fun `a document turns a collecting session into a checking one`() = runTest {
        dao.insertSessionWithCodes(session(collecting = true), emptyList())
        dao.renameAndReplaceCodes("s", "Batch", listOf(code("A", 0)))
        assertFalse(dao.getSession("s")!!.collecting)
    }

    @Test
    fun `recorded codes go to the end in arrival order`() = runTest {
        dao.insertSessionWithCodes(session(collecting = true), emptyList())
        dao.addScannedCode("s", "X", 1)
        dao.addScannedCode("s", "Y", 2)
        val codes = dao.getCodes("s")
        assertEquals(listOf("X", "Y"), codes.map { it.code })
        assertTrue(codes.all { it.scanned })
    }

    @Test
    fun `merge counts only marks that were actually added`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0, scanned = true, at = 1), code("B", 1), code("C", 2)))
        assertEquals(1, dao.markScannedIn("s", listOf("A", "B", "Z"), 50))
        assertEquals(1, dao.markScannedEach("s", mapOf("A" to 7L, "C" to 9L)))
        assertEquals(9L, dao.getCodes("s").first { it.code == "C" }.scannedAt)
    }

    @Test
    fun `the summary counts neither binned nor foreign codes`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0, scanned = true), code("B", 1)))
        dao.moveCodeToBin("s", "A", 10)
        val summary = dao.getSummariesFlow().first().single()
        assertEquals(1, summary.total)
        assertEquals(0, summary.scanned)
    }

    @Test
    fun `old bin entries are purged, fresh ones stay`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0), code("B", 1)))
        dao.moveCodeToBin("s", "A", 10)
        dao.moveCodeToBin("s", "B", 1_000)
        assertEquals(1, dao.purgeBinnedBefore(500))
        assertEquals(listOf("B"), dao.getDeletedCodes("s").map { it.code })
    }

    @Test
    fun `deleting a session removes its codes`() = runTest {
        dao.insertSessionWithCodes(session(), listOf(code("A", 0)))
        dao.deleteSession("s")
        assertNull(dao.getSession("s"))
        assertTrue(dao.getCodes("s").isEmpty())
    }
}
