package com.datools.qrchecker

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.datools.qrchecker.data.room.AppDatabase
import com.datools.qrchecker.data.room.MIGRATION_1_2
import com.datools.qrchecker.data.room.MIGRATION_2_3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "migration-test"

/**
 * The 1 to 2 migration moves two JSON blobs into one row per code, so it is the one place
 * where a mistake silently loses a user's scanning progress.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate1To2_keepsCodesOrderAndScannedState() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO sessions (id, name, codes, scannedCodes) VALUES " +
                        "('s1', 'Session', '[\"A1\",\"A2\",\"A3\"]', '[\"A2\"]')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT name FROM sessions WHERE id = 's1'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("Session", c.getString(0))
        }

        db.query(
            "SELECT code, scanned, position FROM session_codes " +
                    "WHERE sessionId = 's1' ORDER BY position"
        ).use { c ->
            assertEquals(3, c.count)

            c.moveToNext()
            assertEquals("A1", c.getString(0)); assertEquals(0, c.getInt(1)); assertEquals(0, c.getInt(2))
            c.moveToNext()
            assertEquals("A2", c.getString(0)); assertEquals(1, c.getInt(1)); assertEquals(1, c.getInt(2))
            c.moveToNext()
            assertEquals("A3", c.getString(0)); assertEquals(0, c.getInt(1)); assertEquals(2, c.getInt(2))
        }
    }

    /** A session whose blob cannot be parsed must survive with no codes, not fail the migration. */
    @Test
    fun migrate1To2_survivesAnUnparseableCodeList() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO sessions (id, name, codes, scannedCodes) VALUES " +
                        "('broken', 'Broken', 'not json', '[]')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)

        db.query("SELECT 1 FROM sessions WHERE id = 'broken'").use { assertEquals(1, it.count) }
        db.query("SELECT 1 FROM session_codes WHERE sessionId = 'broken'").use {
            assertEquals(0, it.count)
        }
    }

    @Test
    fun migrate2To3_addsScanTimeAndLeavesOldMarksWithoutOne() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL("INSERT INTO sessions (id, name) VALUES ('s1', 'Session')")
            db.execSQL(
                "INSERT INTO session_codes (sessionId, code, scanned, position) VALUES " +
                        "('s1', 'A1', 1, 0), ('s1', 'A2', 0, 1)"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)

        db.query("SELECT code, scanned, scannedAt FROM session_codes ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("A1", c.getString(0))
            assertEquals(1, c.getInt(1))
            // время отметки, поставленной до этого обновления, не выдумывается
            assertTrue(c.isNull(2))
            c.moveToNext()
            assertEquals("A2", c.getString(0))
            assertEquals(0, c.getInt(1))
            assertTrue(c.isNull(2))
        }
    }

    @Test
    fun migrate1To3_runsTheWholeChain() {
        helper.createDatabase(TEST_DB, 1).use { db ->
            db.execSQL(
                "INSERT INTO sessions (id, name, codes, scannedCodes) VALUES " +
                        "('s1', 'Session', '[\"A1\",\"A2\"]', '[\"A1\"]')"
            )
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT code, scanned, scannedAt FROM session_codes ORDER BY position").use { c ->
            assertEquals(2, c.count)
            c.moveToFirst()
            assertEquals("A1", c.getString(0))
            assertEquals(1, c.getInt(1))
            assertTrue(c.isNull(2))
        }
    }
}
