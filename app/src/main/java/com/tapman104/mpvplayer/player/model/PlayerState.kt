package com.tapman104.mpvplayer.player.model

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(val positionMs: Long, val durationMs: Long) : PlayerState()
    data class Paused(val positionMs: Long, val durationMs: Long) : PlayerState()
    data class Error(val message: String) : PlayerState()
}
