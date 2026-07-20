package com.potato.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerRightSideSheet
import com.potato.player.feature.player.controls.SubtitleTrackDialog

// ponytail: move only, zero new logic
@Composable
fun PlayerModals(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel,
    showDecoderDialog: Boolean,
    onDismissDecoderDialog: () -> Unit
) {
    val context = LocalContext.current

    if (showDecoderDialog) {
        PlayerDecoderDialog(
            currentDecoder = uiState.hwdecCurrent,
            onSelectDecoder = { mode -> viewModel.setDecoder(mode) },
            onDismiss = onDismissDecoderDialog
        )
    }

    if (uiState.showAudioDialog) {
        AudioTrackDialog(
            tracks = uiState.tracks.filter { it.type == "audio" },
            currentTrackId = uiState.currentAudioTrackId,
            onSelectTrack = { viewModel.onSelectAudioTrack(it) },
            onDismiss = { viewModel.onDismissAudioDialog() }
        )
    }

    if (uiState.showSubtitleDialog) {
        SubtitleTrackDialog(
            tracks = uiState.tracks.filter { it.type == "sub" },
            currentTrackId = uiState.currentSubtitleTrackId,
            onSelectTrack = { viewModel.onSelectSubtitleTrack(it) },
            onLoadExternal = { uri -> viewModel.onLoadExternalSubtitle(uri, context) },
            onDismiss = { viewModel.onDismissSubtitleDialog() }
        )
    }

    PlayerRightSideSheet(
        visible = uiState.showMoreMenu || uiState.showSpeedDialog,
        currentSpeed = uiState.playbackSpeed,
        onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
        onShowAudioDialog = { viewModel.onShowAudioDialog() },
        onShowSubtitleDialog = { viewModel.onShowSubtitleDialog() },
        onDismiss = {
            viewModel.onMoreMenuDismiss()
            viewModel.onDismissSpeedDialog()
        }
    )
}
