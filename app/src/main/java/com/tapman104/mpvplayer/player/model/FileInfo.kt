package com.tapman104.mpvplayer.player.model

data class FileInfo(
    val fileName: String,
    val filePath: String?,
    val durationMs: Long,
    val videoTracks: Int,
    val audioTracks: Int,
    val subtitleTracks: Int
)
