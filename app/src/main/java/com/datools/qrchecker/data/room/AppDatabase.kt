package com.datools.qrchecker.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SessionCodeEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun sessionDao(): SessionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "sessions.db")
                // Do not add fallbackToDestructiveMigrationOnDowngrade next to this: both
                // write the same requireMigration flag, and whichever is called second wins.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
