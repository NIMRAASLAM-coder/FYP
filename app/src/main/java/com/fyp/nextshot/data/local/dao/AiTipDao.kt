package com.fyp.nextshot.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fyp.nextshot.data.local.models.AiTipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiTipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tips: List<AiTipEntity>)

    /**
     * Returns the most recent AI tips for a user, ordered newest first.
     * Limited to 15 tips max to keep the UI snappy.
     */
    @Query("SELECT * FROM ai_tips_table WHERE userId = :userId ORDER BY generatedAtMillis DESC LIMIT 15")
    fun getLatestTipsForUser(userId: String): Flow<List<AiTipEntity>>

    /**
     * Returns the most recent tip's timestamp for cache-checking.
     * If null, no tips exist yet.
     */
    @Query("SELECT MAX(generatedAtMillis) FROM ai_tips_table WHERE userId = :userId")
    suspend fun getLatestTipTimestamp(userId: String): Long?

    /**
     * Delete all tips for a user (used before refreshing with new ones).
     */
    @Query("DELETE FROM ai_tips_table WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
