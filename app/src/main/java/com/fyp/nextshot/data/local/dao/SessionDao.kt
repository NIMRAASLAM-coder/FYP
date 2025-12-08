package com.fyp.nextshot.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.fyp.nextshot.data.local.models.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Delete
    suspend fun deleteSession(session: SessionEntity)

    // Query to retrieve all sessions for the current user
    @Query("SELECT * FROM session_table WHERE userId = :userId ORDER BY dateMillis DESC")
    fun getAllSessionsForUser(userId: String): Flow<List<SessionEntity>>


    // In data/local/dao/SessionDao.kt (Add this query)
    @Query("SELECT * FROM session_table WHERE cloudDocumentId = :cloudId")
    suspend fun getSessionByCloudId(cloudId: String): SessionEntity?



}