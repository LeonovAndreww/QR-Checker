package com.datools.qrchecker.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, SessionCodeEntity::class],
    version = 4,
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
                    // Миграций нет намеренно. Версией 1 успели побывать две разные
                    // схемы: опубликованная в 1.2.1, где коды лежали JSON-блобом в
                    // строке сессии, и здешняя, где под них отдельная таблица. Room
                    // различает базы только по номеру, так что любая миграция от
                    // единицы для половины устройств выполнялась бы не над той схемой
                    // и роняла приложение при открытии.
                    //
                    // Пересоздание ничего не стоит: 2.0.0 подписана другим ключом, и
                    // поверх прежней установки она всё равно не встанет.
                    //
                    // Только эта строка, и никакого fallbackToDestructiveMigrationOnDowngrade
                    // рядом: оба метода пишут в одно поле requireMigration, и вызванный
                    // вторым OnDowngrade ставит его обратно в true, то есть снова требует
                    // миграцию и роняет приложение при обновлении. Понижение версии здесь
                    // и так разрешено - fallbackToDestructiveMigration ставит
                    // allowDestructiveMigrationOnDowngrade сам.
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
