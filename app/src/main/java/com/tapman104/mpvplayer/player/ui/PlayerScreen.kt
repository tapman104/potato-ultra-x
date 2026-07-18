package com.tapman104.mpvplayer.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.tapman104.mpvplayer.player.model.DecodeMode
import com.tapman104.mpvplayer.player.model.ViewMode
import com.tapman104.mpvplayer.player.viewmodel.PlayerViewModel

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    fileName: String,
    onBack: () -> Unit
) {
    val paused by viewModel.paused.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VideoSurface(
            controller = viewModel.controller,
            modifier = Modifier.fillMaxSize()
        )
        PlayerOverlay(
            fileName = fileName,
            isPlaying = !paused,
            positionMs = positionMs,
            durationMs = durationMs,
            decodeMode = DecodeMode.HW,       // stub — wire to state later
            currentViewMode = ViewMode.FIT,    // stub — wire to state later
            onBack = onBack,
            onTogglePlay = viewModel::togglePlay,
            onSeek = viewModel::seekTo,
            onSelectAudioTrack = viewModel::selectAudioTrack,
            onSelectSubtitleTrack = viewModel::selectSubtitleTrack,
            onDecodeModeClick = viewModel::cycleDecodeMode,
            onMoreOptions = viewModel::showMoreOptions,
            onCycleViewMode = viewModel::cycleViewMode,
            onRotate = viewModel::rotateVideo,
            onEnterPip = viewModel::enterPip,
            modifier = Modifier.fillMaxSize()
        )
    }
}
