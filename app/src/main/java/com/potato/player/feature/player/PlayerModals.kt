package com.potato.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.potato.player.engine.TrackType
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerRightSideSheet
import com.potato.player.feature.player.controls.SubtitleTrackDialog

// ponytail: move only, zero new logic
@Composable
fun PlayerModals(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current

    when (uiState.activeSheet) {
        ActiveSheet.DECODER -> {
            PlayerDecoderDialog(
                currentDecoder = uiState.hwdecCurrent,
                onSelectDecoder = { mode -> viewModel.setDecoder(mode) },
                onDismiss = { viewModel.onDismissDecoderDialog() }
            )
        }
        ActiveSheet.AUDIO -> {
            AudioTrackDialog(
                tracks = uiState.tracks.filter { it.type == TrackType.AUDIO },
                currentTrackId = uiState.currentAudioTrackId,
                onSelectTrack = { viewModel.onSelectAudioTrack(it) },
                onDismiss = { viewModel.onDismissAudioDialog() }
            )
        }
        ActiveSheet.SUBTITLE -> {
            SubtitleTrackDialog(
                tracks = uiState.tracks.filter { it.type == TrackType.SUBTITLE },
                currentTrackId = uiState.currentSubtitleTrackId,
                onSelectTrack = { viewModel.onSelectSubtitleTrack(it) },
                onLoadExternal = { uri -> viewModel.onLoadExternalSubtitle(uri, context) },
                onDismiss = { viewModel.onDismissSubtitleDialog() },
                uiState = uiState,
                onSetSubtitleAppearance = { scale, pos -> viewModel.setSubtitleAppearance(scale, pos) },
                onResetSubtitleAppearance = { viewModel.resetSubtitleAppearance() }
            )
        }
        else -> Unit
    }

    // ponytail: gate sheet on fileLoaded so it never appears on an empty player
    if (uiState.fileLoaded) {
        PlayerRightSideSheet(
            visible = uiState.activeSheet == ActiveSheet.MORE_MENU || uiState.activeSheet == ActiveSheet.SPEED,
            currentSpeed = uiState.playbackSpeed,
            onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
            onShowAudioDialog = { viewModel.onShowAudioDialog() },
            onShowSubtitleDialog = { viewModel.onShowSubtitleDialog() },
            onDismiss = {
                if (uiState.activeSheet == ActiveSheet.MORE_MENU) viewModel.onMoreMenuDismiss()
                else if (uiState.activeSheet == ActiveSheet.SPEED) viewModel.onDismissSpeedDialog()
                else viewModel.onMoreMenuDismiss()
            }
        )
    }
}

