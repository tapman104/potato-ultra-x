package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import androidx.compose.animation.*
import com.potato.player.util.MediaMetadataRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.potato.player.feature.player.controls.AudioTrackDialog
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import com.potato.player.feature.player.controls.DoubleTapSeekState
import com.potato.player.feature.player.controls.HoldToFastForward
import com.potato.player.feature.player.controls.PlayerRightSideSheet
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerDecoderDialog
import com.potato.player.feature.player.controls.PlayerTopBar
import com.potato.player.feature.player.controls.SubtitleTrackDialog
import android.content.pm.ActivityInfo
import com.potato.player.util.findActivity
import com.potato.player.util.lockOrientation
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUri: String,
    title: String = "",
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Derive accurate display name or use provided title
    var fileName by remember(videoUri, title) { mutableStateOf(if (title.isNotBlank()) title else "Video") }
    LaunchedEffect(videoUri, title, context) {
        if (title.isBlank()) {
            fileName = MediaMetadataRepository.resolveFileName(context, videoUri)
        }
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var showDecoderDialog by remember { mutableStateOf(false) }
    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    var isLongPressActive by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds of inactivity when playing & not dragging seek bar
    LaunchedEffect(controlsVisible, uiState.isPlaying, uiState.dragPositionSec) {
        if (controlsVisible && uiState.isPlaying && uiState.dragPositionSec == null) {
            delay(4000L)
            controlsVisible = false
        }
    }

    // Clear double-tap seek overlay after animation
    LaunchedEffect(doubleTapSeekState?.triggerId) {
        if (doubleTapSeekState != null) {
            delay(800L)
            doubleTapSeekState = null
        }
    }

    // Clean up surface callback when composable leaves composition
    DisposableEffect(Unit) {
        onDispose { viewModel.surface.setSurfaceReadyCallback(null) }
    }

    // Load the video once the surface is ready; also handles config-change re-attach
    LaunchedEffect(videoUri) {
        viewModel.surface.setSurfaceReadyCallback {
            viewModel.loadFile(videoUri)
        }
        if (viewModel.surface.hasSurface()) {
            viewModel.loadFile(videoUri)
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
                        onPress = { offset ->
                            tryAwaitRelease()
                            if (isLongPressActive) {
                                isLongPressActive = false
                                viewModel.stopFastForward()
                            }
                        },
                        onLongPress = { offset ->
                            isLongPressActive = true
                            viewModel.startFastForward()
                        },
                        onDoubleTap = { offset ->
                            val current = doubleTapSeekState // ponytail: one snapshot variable eliminates the crash
                            val screenWidth = size.width
                            if (offset.x < screenWidth / 2f) {
                                viewModel.seekExactRelative(-10)
                                val accum = if (current != null && !current.isForward) {
                                    current.totalSeconds + 10
                                } else 10
                                doubleTapSeekState = DoubleTapSeekState(isForward = false, totalSeconds = accum)
                            } else {
                                viewModel.seekExactRelative(10)
                                val accum = if (current != null && current.isForward) {
                                    current.totalSeconds + 10
                                } else 10
                                doubleTapSeekState = DoubleTapSeekState(isForward = true, totalSeconds = accum)
                            }
                        },
                        onTap = {
                            controlsVisible = !controlsVisible
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                        } while (event.changes.any { it.pressed })
                        if (isLongPressActive) {
                            isLongPressActive = false
                            viewModel.stopFastForward()
                        }
                    }
                }
        )

        // ── Double-Tap Seek Overlay Ripple ───────────────────────────────────
        DoubleTapSeekOverlay(seekState = doubleTapSeekState)

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        HoldToFastForward(
            visible = uiState.isFastForwarding || isLongPressActive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (controlsVisible) 72.dp else 36.dp)
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
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                PlayerTopBar(
                    fileName              = fileName,
                    currentDecoder        = uiState.hwdecCurrent,
                    onBack                = onBack,
                    onSelectAudioTrack    = { viewModel.onShowAudioDialog() },
                    onSelectSubtitleTrack = { viewModel.onShowSubtitleDialog() },
                    onSelectDecoder       = { showDecoderDialog = true },
                    onMoreOptions         = { viewModel.onMoreMenuToggle() }
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
}
