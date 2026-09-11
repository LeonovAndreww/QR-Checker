package com.datools.qrchecker.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Миграций нет, и это не упущение.
 *
 * Номерами 1, 3 и 4 в истории проекта успели побывать по две разные схемы: сначала коды
 * лежали JSON-блобом в строке сессии, потом отдельной таблицей, потом обрастали
 * столбцами, а в какой-то момент нумерация была сброшена на единицу. Room различает базы
 * только по номеру, поэтому миграция от любого из этих номеров для части устройств
 * выполнялась бы не над той схемой - и роняла приложение при открытии, что уже
 * случалось.
 *
 * Отсюда номер 10: им не была ни одна схема. Любая база, оставшаяся от прежних сборок,
 * не находит пути к нему и пересоздаётся - без падения и без угадывания, что там внутри.
 * Стоит это ничего: 2.0.0 подписана другим ключом, и поверх прежней установки она всё
 * равно не встанет.
 *
 * Дальше - только настоящие миграции: 10 и всё, что после, будет означать ровно одну
 * схему.
 */
@Database(
    entities = [SessionEntity::class, SessionCodeEntity::class],
    version = 10,
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
