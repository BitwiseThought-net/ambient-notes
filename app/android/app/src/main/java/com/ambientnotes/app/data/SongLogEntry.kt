package com.ambientnotes.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single successfully-identified song, persisted for history display and
 * to know what's already been posted to which targets. */
@Entity(tableName = "song_log")
data class SongLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String?,
    val artist: String?,
    val album: String?,
    val releaseDate: String?,
    val confidence: Float,
    val providerName: String?,
    val externalIdsJson: String, // serialized Map<String,String>, see Converters
    val recognizedAtEpochMs: Long,
    val postedTargetIdsJson: String = "[]", // targets this entry was successfully posted to
)
