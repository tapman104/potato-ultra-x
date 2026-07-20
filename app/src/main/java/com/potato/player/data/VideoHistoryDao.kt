package com.potato.player.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: VideoHistory)

    @Query("SELECT * FROM video_history WHERE uri = :uri LIMIT 1")
    suspend fun getByUri(uri: String): VideoHistory?

    @Query("SELECT * FROM video_history ORDER BY lastPlayedTimestamp DESC")
    fun getAllOrderedByTimestamp(): Flow<List<VideoHistory>>
}
