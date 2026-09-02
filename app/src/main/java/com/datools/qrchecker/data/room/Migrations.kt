package com.datools.qrchecker.data.room

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val TAG = "QRChecker"

/**
 * Moves the two JSON blobs on the session row into one row per code.
 *
 * The old table is renamed rather than dropped, so the blobs stay readable while the new
 * rows are written, and a code that fails to parse costs that session's list instead of
 * failing the whole migration.
 */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `sessions` RENAME TO `sessions_old`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `sessions` " +
                    "(`id` TEXT NOT NULL, `name` TEXT NOT NULL, PRIMARY KEY(`id`))"
        )
        db.execSQL("INSERT INTO `sessions` (`id`, `name`) SELECT `id`, `name` FROM `sessions_old`")

        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `session_codes` " +
                    "(`sessionId` TEXT NOT NULL, `code` TEXT NOT NULL, `scanned` INTEGER NOT NULL, " +
                    "`position` INTEGER NOT NULL, PRIMARY KEY(`sessionId`, `code`), " +
                    "FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`) " +
                    "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_session_codes_sessionId` " +
                    "ON `session_codes` (`sessionId`)"
        )

        val gson = Gson()
        val listType = object : TypeToken<List<String>>() {}.type

        fun parse(json: String?): List<String> = try {
            if (json.isNullOrBlank()) emptyList() else gson.fromJson(json, listType) ?: emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not parse a legacy code list", t)
            emptyList()
        }

        db.query("SELECT `id`, `codes`, `scannedCodes` FROM `sessions_old`").use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0)
                val codes = parse(cursor.getString(1))
                val scanned = parse(cursor.getString(2)).toHashSet()

                codes.forEachIndexed { position, code ->
                    db.execSQL(
                        "INSERT OR REPLACE INTO `session_codes` " +
                                "(`sessionId`, `code`, `scanned`, `position`) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(id, code, if (code in scanned) 1 else 0, position)
                    )
                }
            }
        }

        db.execSQL("DROP TABLE `sessions_old`")
    }
}

/**
 * Добавляет время отметки.
 *
 * У кодов, отмеченных до этого обновления, оно остаётся пустым: настоящего времени для
 * них не существует, и проставлять туда «сейчас» значило бы придумать данные, на которые
 * потом будут смотреть как на факт.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `session_codes` ADD COLUMN `scannedAt` INTEGER DEFAULT NULL")
    }
}
