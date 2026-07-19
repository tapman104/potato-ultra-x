package com.potato.player.feature.player

import android.net.Uri
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Extension: Int seconds → "MM:SS"
private fun Int.toTimeString() = "%02d:%02d".format(this / 60, this % 60)

@Composable
fun PlayerScreen(encodedUri: String, viewModel: PlayerViewModel) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Track whether the slider drag has started in this gesture
    var isDragging by remember { mutableStateOf(false) }

    // Register the surface callback and clean it up when the composable leaves
    DisposableEffect(Unit) {
        onDispose { viewModel.surface.setSurfaceReadyCallback(null) }
    }

    // Load the video once the surface is ready
    LaunchedEffect(encodedUri) {
        val uri = Uri.decode(encodedUri)
        viewModel.surface.setSurfaceReadyCallback {
            viewModel.loadFile(uri)
        }
        // If a surface is already attached (e.g. config change), load immediately
        if (viewModel.surface.hasSurface()) {
            viewModel.loadFile(uri)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // Video surface — fills the entire screen
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    sv.holder.addCallback(viewModel.surface)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }

        // Error message
        uiState.error?.let { msg ->
            Text(
                text = "Error: $msg",
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Playback controls — bottom overlay
        if (uiState.fileLoaded) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Time display
                val displayPos = (uiState.dragPositionSec ?: uiState.positionSec).toInt()
                val duration   = uiState.durationSec.toInt()
                Text(
                    text  = "${displayPos.toTimeString()} / ${duration.toTimeString()}",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.End)
                )

                // Seek bar
                Slider(
                    value      = (uiState.dragPositionSec ?: uiState.positionSec).toFloat(),
                    onValueChange = { pos ->
                        if (!isDragging) {
                            isDragging = true
                            viewModel.onSliderDragStart(pos.toDouble())
                        } else {
                            viewModel.onSliderDragChange(pos.toDouble())
                        }
                    },
                    onValueChangeFinished = {
                        val finalPos = uiState.dragPositionSec ?: uiState.positionSec
                        viewModel.onSliderDragEnd(finalPos)
                        isDragging = false
                    },
                    valueRange = 0f..uiState.durationSec.toFloat().coerceAtLeast(1f),
                    modifier   = Modifier.fillMaxWidth()
                )

                // Play / Pause button
                IconButton(
                    onClick  = { viewModel.togglePlay() },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text(
                        text  = if (uiState.isPlaying) "⏸" else "▶",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    }
}
