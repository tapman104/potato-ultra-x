package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.PlaybackSpeedDialog
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerTopBar
import com.potato.player.feature.player.controls.SubtitleTrackDialog
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    encodedUri: String,
    viewModel: PlayerViewModel,
    navController: NavController,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Derive accurate display name (checking OpenableColumns for content URIs or clean path segment)
    val fileName = remember(encodedUri, context) {
        resolveFileName(context, encodedUri)
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var seekIndicatorText by remember { mutableStateOf<String?>(null) }

    // Auto-hide controls after 4 seconds of inactivity when playing & not dragging seek bar
    LaunchedEffect(controlsVisible, uiState.isPlaying, uiState.dragPositionSec) {
        if (controlsVisible && uiState.isPlaying && uiState.dragPositionSec == null) {
            delay(4000L)
            controlsVisible = false
        }
    }

    // Clear double-tap seek indicator after animation
    LaunchedEffect(seekIndicatorText) {
        if (seekIndicatorText != null) {
            delay(800L)
            seekIndicatorText = null
        }
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
                    sv.keepScreenOn = true
                    sv.holder.addCallback(viewModel.surface)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture & Tap Overlay ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            controlsVisible = !controlsVisible
                        },
                        onDoubleTap = { offset ->
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2f) {
                                viewModel.seekRelative(-10.0)
                                seekIndicatorText = "-10s"
                            } else {
                                viewModel.seekRelative(10.0)
                                seekIndicatorText = "+10s"
                            }
                        }
                    )
                }
        )

        // ── Double-tap Seek Indicator ────────────────────────────────────────
        AnimatedVisibility(
            visible = seekIndicatorText != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            seekIndicatorText?.let { text ->
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(24.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

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
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PlayerTopBar(
                    fileName              = fileName,
                    currentDecoder        = uiState.hwdecCurrent,
                    onBack                = { navController.popBackStack() },
                    onSelectAudioTrack    = { viewModel.onShowAudioDialog() },
                    onSelectSubtitleTrack = { viewModel.onShowSubtitleDialog() },
                    onSelectDecoder       = { showDecoderDialog = true },
                    onMoreOptions         = { viewModel.onMoreMenuToggle() },
                    showMoreMenu          = uiState.showMoreMenu,
                    onMoreMenuToggle      = { viewModel.onMoreMenuToggle() },
                    onMoreMenuDismiss     = { viewModel.onMoreMenuDismiss() },
                    onShowAudioDialog     = { viewModel.onShowAudioDialog() },
                    onShowSubtitleDialog  = { viewModel.onShowSubtitleDialog() },
                    onShowSpeedDialog     = { viewModel.onShowSpeedDialog() }
                )
            }

            // ── Bottom controls ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                PlayerBottomControls(
                    isPlaying        = uiState.isPlaying,
                    // ViewModel stores seconds; controls work in milliseconds
                    currentPositionMs = (uiState.positionSec * 1000.0).toLong(),
                    durationMs        = (uiState.durationSec * 1000.0).toLong(),
                    cachedPositionMs  = (uiState.cachedSec * 1000.0).toLong(),
                    bufferDurationMs  = (uiState.cacheDurationSec * 1000.0).toLong(),
                    onTogglePlay      = viewModel::togglePlay,
                    onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                    onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                    onDragStart       = { viewModel.onSliderDragStart(uiState.positionSec) },
                    onDragEnd         = { /* already handled inside onSeekCommit path */ }
                )
            }
        }

        // ── Decoder Selection Dialog ─────────────────────────────────────────
        if (showDecoderDialog) {
            PlayerDecoderDialog(
                currentDecoder = uiState.hwdecCurrent,
                onSelectDecoder = { mode -> viewModel.setDecoder(mode) },
                onDismiss = { showDecoderDialog = false }
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

        if (uiState.showSpeedDialog) {
            PlaybackSpeedDialog(
                currentSpeed = uiState.playbackSpeed,
                onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                onDismiss = { viewModel.onDismissSpeedDialog() }
            )
        }
    }
}

private fun resolveFileName(context: Context, encodedUri: String): String {
    val decoded = Uri.decode(encodedUri)
    val parsedUri = Uri.parse(decoded)

    if (parsedUri.scheme == "content") {
        try {
            context.contentResolver.query(parsedUri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (e: Exception) {
            // Fallback to path segment below if query fails
        }
    }

    return parsedUri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: decoded.substringAfterLast('/').takeIf { it.isNotBlank() }
        ?: "Video"
}
