package com.ambientnotes.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SongLogDao {
    @Insert
    suspend fun insert(entry: SongLogEntry): Long

    @Update
    suspend fun update(entry: SongLogEntry)

    @Query("SELECT * FROM song_log ORDER BY recognizedAtEpochMs DESC")
    fun observeAll(): Flow<List<SongLogEntry>>

    @Query("SELECT * FROM song_log ORDER BY recognizedAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int = 50): List<SongLogEntry>

    @Query(
        "SELECT * FROM song_log WHERE title = :title AND artist = :artist " +
            "ORDER BY recognizedAtEpochMs DESC LIMIT 1"
    )
    suspend fun mostRecentMatching(title: String?, artist: String?): SongLogEntry?

    @Query("DELETE FROM song_log")
    suspend fun clearAll()
}
