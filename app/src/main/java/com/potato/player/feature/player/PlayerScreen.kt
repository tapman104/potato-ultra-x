package com.potato.player.feature.player

import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerTopBar

@Composable
fun PlayerScreen(
    encodedUri: String,
    viewModel: PlayerViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Derive a display name from the URI (last path segment, no extension)
    val fileName = remember(encodedUri) {
        val decoded = Uri.decode(encodedUri)
        Uri.parse(decoded).lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?: "Video"
    }

    // Clean up surface callback when composable leaves composition
    DisposableEffect(Unit) {
        onDispose { viewModel.surface.setSurfaceReadyCallback(null) }
    }

    // Load the video once the surface is ready; also handles config-change re-attach
    LaunchedEffect(encodedUri) {
        val uri = Uri.decode(encodedUri)
        viewModel.surface.setSurfaceReadyCallback {
            viewModel.loadFile(uri)
        }
        if (viewModel.surface.hasSurface()) {
            viewModel.loadFile(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        // ── Video surface ────────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    sv.holder.addCallback(viewModel.surface)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Loading indicator ────────────────────────────────────────────────
        if (uiState.isLoading) {
            CircularProgressIndicator(
                color    = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // ── Error message ────────────────────────────────────────────────────
        uiState.error?.let { msg ->
            Text(
                text     = "Error: $msg",
                color    = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (uiState.fileLoaded) {

            // ── Top bar ──────────────────────────────────────────────────────
            PlayerTopBar(
                fileName              = fileName,
                onBack                = { navController.popBackStack() },
                onSelectAudioTrack    = { /* Phase 4 */ },
                onSelectSubtitleTrack = { /* Phase 4 */ },
                onMoreOptions         = { /* Phase 8 */ },
                modifier              = Modifier.align(Alignment.TopCenter)
            )

            // ── Bottom controls ──────────────────────────────────────────────
            PlayerBottomControls(
                isPlaying        = uiState.isPlaying,
                // ViewModel stores seconds; controls work in milliseconds
                currentPositionMs = (uiState.positionSec * 1000.0).toLong(),
                durationMs        = (uiState.durationSec * 1000.0).toLong(),
                onTogglePlay      = viewModel::togglePlay,
                onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                onDragStart       = { viewModel.onSliderDragStart(uiState.positionSec) },
                onDragEnd         = { /* already handled inside onSeekCommit path */ },
                modifier          = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
