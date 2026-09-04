package com.datools.qrchecker.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
                    // Миграций нет и не было кому их проходить: приложение не
                    // публиковалось, а сессия здесь живёт часы. База со старой схемой
                    // пересоздаётся вместо того, чтобы тянуть за собой код переноса,
                    // который никто никогда не выполнит.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
