package com.datools.qrchecker.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Появился столбец «когда убрали в корзину».
 *
 * Одна строка вместо пересоздания базы: терять партию из-за обновления приложения
 * человеку незачем, а размен «немного кода против стёртой работы» тут очевидный.
 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE session_codes ADD COLUMN deletedAt INTEGER")
    }
}

@Database(
    entities = [SessionEntity::class, SessionCodeEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // re-check inside the lock: another thread may have built the instance
                // while this one was waiting for the monitor
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sessions.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // Запасной путь на случай схемы, к которой перехода нет: база
                    // пересоздаётся, а не роняет приложение при открытии.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
