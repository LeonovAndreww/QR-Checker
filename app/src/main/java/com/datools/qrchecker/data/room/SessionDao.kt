package com.datools.qrchecker.data.room

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: String): SessionEntity?

    @Query("SELECT * FROM sessions")
    fun getAllFlow(): Flow<List<SessionEntity>>
}