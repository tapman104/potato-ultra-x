package com.tapman104.mpvplayer.player.core

import com.tapman104.mpvplayer.player.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaybackStateManager {
    private val _state = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun transitionTo(newState: PlayerState) { _state.value = newState }
}
