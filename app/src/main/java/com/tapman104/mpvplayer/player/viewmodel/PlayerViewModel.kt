package com.tapman104.mpvplayer.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.tapman104.mpvplayer.player.core.MpvController
import com.tapman104.mpvplayer.player.model.DecodeMode
import com.tapman104.mpvplayer.player.model.PlayerState
import com.tapman104.mpvplayer.player.model.ViewMode
import kotlinx.coroutines.flow.StateFlow

class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val controller = MpvController(app)

    val playerState: StateFlow<PlayerState> = controller.state
    val positionMs: StateFlow<Long> = controller.positionMs
    val durationMs: StateFlow<Long> = controller.durationMs
    val paused: StateFlow<Boolean> = controller.paused

    fun loadFile(path: String) = controller.loadFile(path)
    fun togglePlay() = controller.togglePlay()
    fun seekTo(ms: Long) = controller.seekTo(ms)

    // Stubs — wire up later
    fun selectAudioTrack() {}
    fun selectSubtitleTrack() {}
    fun cycleDecodeMode() {}
    fun showMoreOptions() {}
    fun cycleViewMode() {}
    fun rotateVideo() {}
    fun enterPip() {}

    override fun onCleared() {
        super.onCleared()
        controller.destroy()
    }
}
