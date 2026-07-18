package com.tapman104.mpvplayer.player.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tapman104.mpvplayer.player.controls.PlayerBottomControls
import com.tapman104.mpvplayer.player.controls.PlayerTopBar
import com.tapman104.mpvplayer.player.controls.PlayerViewControls
import com.tapman104.mpvplayer.player.model.DecodeMode
import com.tapman104.mpvplayer.player.model.ViewMode

@Composable
fun PlayerOverlay(
    fileName: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    decodeMode: DecodeMode,
    currentViewMode: ViewMode,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onDecodeModeClick: () -> Unit,
    onMoreOptions: () -> Unit,
    onCycleViewMode: () -> Unit,
    onRotate: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        PlayerTopBar(
            fileName = fileName,
            onBack = onBack,
            decodeMode = decodeMode,
            onSelectAudioTrack = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onDecodeModeClick = onDecodeModeClick,
            onMoreOptions = onMoreOptions,
            modifier = Modifier.align(Alignment.TopStart)
        )

        PlayerViewControls(
            currentViewMode = currentViewMode,
            onCycleViewMode = onCycleViewMode,
            onRotate = onRotate,
            onEnterPip = onEnterPip,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 140.dp)
        )

        PlayerBottomControls(
            isPlaying = isPlaying,
            currentPositionMs = positionMs,
            durationMs = durationMs,
            onTogglePlay = onTogglePlay,
            onSeek = onSeek,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        )
    }
}
