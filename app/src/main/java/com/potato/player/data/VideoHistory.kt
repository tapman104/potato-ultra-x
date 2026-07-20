package com.potato.player.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_history")
data class VideoHistory(
    @PrimaryKey val uri: String,
    val title: String,
    val lastPlayedPositionSec: Double,
    val durationSec: Double,
    val lastAudioTrackId: Int,
    val lastSubtitleTrackId: Int,
    val lastPlayedTimestamp: Long
)
