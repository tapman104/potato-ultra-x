package com.potato.player.feature.player

import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import android.view.SurfaceView
import com.potato.player.util.MediaMetadataRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.potato.player.feature.player.controls.DoubleTapSeekOverlay
import com.potato.player.feature.player.controls.DoubleTapSeekState
import com.potato.player.feature.player.controls.HoldToFastForward
import com.potato.player.feature.player.controls.PlayerBottomControls
import com.potato.player.feature.player.controls.PlayerTopBar
import androidx.activity.compose.BackHandler
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
    BackHandler {
        viewModel.pause()
        onBack()
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val progressState by viewModel.progressState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // ponytail: orientation + insets boilerplate extracted for readability
    PlayerLifecycleEffect(activity = activity, uiState = uiState, viewModel = viewModel)

    // Derive accurate display name or use provided title
    var fileName by remember(videoUri, title) { mutableStateOf(if (title.isNotBlank()) title else "Video") }
    LaunchedEffect(videoUri, title, context) {
        if (title.isBlank()) {
            fileName = MediaMetadataRepository.resolveFileName(context, videoUri)
        }
    }

    val controlsState = rememberControlsVisibilityState(
        isPlaying = uiState.isPlaying,
        dragPositionSec = progressState.dragPositionSec,
        isInPipMode = activity?.isInPictureInPictureMode == true
    )
    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    var isLongPressActive by remember { mutableStateOf(false) }

    // Clear double-tap seek overlay after animation
    LaunchedEffect(doubleTapSeekState?.triggerId) {
        if (doubleTapSeekState != null) {
            delay(PlayerUiConstants.DOUBLE_TAP_OVERLAY_CLEAR_MS)
            doubleTapSeekState = null
        }
    }

    // Load the video once the surface is ready; also handles config-change re-attach.
    DisposableEffect(viewModel, videoUri) {
        viewModel.setSurfaceReadyCallback { viewModel.onSurfaceReady(videoUri, title) }
        onDispose {
            viewModel.setSurfaceReadyCallback(null)
            viewModel.onSurfaceDestroyed()
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
                    sv.holder.addCallback(viewModel.surfaceCallback)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture & Tap Overlay ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activity?.isInPictureInPictureMode == true) {
                    if (activity?.isInPictureInPictureMode == true) return@pointerInput
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
                                viewModel.seekExactRelative(-PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS)
                                val accum = if (current != null && !current.isForward) {
                                    current.totalSeconds + PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                } else PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                doubleTapSeekState = DoubleTapSeekState(isForward = false, totalSeconds = accum)
                            } else {
                                viewModel.seekExactRelative(PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS)
                                val accum = if (current != null && current.isForward) {
                                    current.totalSeconds + PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                } else PlayerUiConstants.DOUBLE_TAP_SEEK_SECONDS
                                doubleTapSeekState = DoubleTapSeekState(isForward = true, totalSeconds = accum)
                            }
                        },
                        onTap = {
                            controlsState.toggle()
                        }
                    )
                }
        )

        // ── Double-Tap Seek Overlay ──────────────────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        if (!(activity?.isInPictureInPictureMode == true)) {
            HoldToFastForward(
                visible = uiState.isFastForwarding || isLongPressActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (controlsState.isVisible) 72.dp else 36.dp)
            )
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

        if (uiState.fileLoaded && !(activity?.isInPictureInPictureMode == true)) {

            // ── Top bar ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerTopBar(
                    fileName              = fileName,
                    currentDecoder        = uiState.hwdecCurrent,
                    onBack                = onBack,
                    onSelectAudioTrack    = { viewModel.onShowAudioDialog() },
                    onSelectSubtitleTrack = { viewModel.onShowSubtitleDialog() },
                    onSelectDecoder       = { viewModel.onShowDecoderDialog() },
                    onMoreOptions         = { viewModel.onMoreMenuToggle() }
                )
            }

            // ── Center play/pause ────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                IconButton(
                    onClick  = viewModel::togglePlay,
                    modifier = Modifier
                        .size(64.dp)
                        .background(Color.White, shape = CircleShape)
                ) {
                    Icon(
                        imageVector     = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        tint            = Color.Black,
                        modifier        = Modifier.size(36.dp)
                    )
                }
            }

            // ── Bottom controls ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
            ) {
                PlayerBottomControls(
                    progressState     = progressState,
                    isAutoRotation    = uiState.isAutoRotation,
                    onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                    onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                    onDragStart       = { viewModel.onSliderDragStart(progressState.positionSec) },
                    onDragEnd         = { /* already handled inside onSeekCommit path */ },
                    onToggleAutoRotation = { viewModel.toggleAutoRotation() },
                    onEnterPip        = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            activity?.enterPictureInPictureMode(
                                PictureInPictureParams.Builder().build()
                            )
                        }
                    }
                )
            }
        }

        // ponytail: move only, zero new logic
        if (!(activity?.isInPictureInPictureMode == true)) {
            PlayerModals(
                uiState = uiState,
                viewModel = viewModel
            )
        }
    }
}

// ponytail: extracted from PlayerScreen — zero new logic
@Composable
private fun PlayerLifecycleEffect(
    activity: android.app.Activity?,
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasSetAspectOrientation by remember { mutableStateOf(false) }

    fun updateOrientation() {
        if (uiState.isAutoRotation) {
            hasSetAspectOrientation = true
            lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
            return
        }
        when (uiState.orientationMode) {
            OrientationMode.LOCK_LANDSCAPE -> {
                hasSetAspectOrientation = true
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            }
            OrientationMode.LOCK_PORTRAIT -> {
                hasSetAspectOrientation = true
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT)
            }
            OrientationMode.AUTO -> {
                if (uiState.videoWidth > 0 && uiState.videoHeight > 0) {
                    val target = if (uiState.videoHeight > uiState.videoWidth) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                    lockOrientation(activity, target)
                    hasSetAspectOrientation = true
                } else if (!hasSetAspectOrientation) {
                    lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                }
            }
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(lifecycleOwner, activity, view) {
        val window = activity?.window
        if (window != null) {
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                updateOrientation()
                // We notify the engine to reattach the surface on resume.
                // The new simple attach sequence in MpvSurface will safely tear down the old 
                // context and rebuild it, ensuring the GPU context is fully restored even on 
                // OEM devices where the Surface object survives lock/background.
                if (uiState.fileLoaded) {
                    viewModel.onSurfaceReattached()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        updateOrientation()
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isInPictureInPictureMode == false && window != null) {
                val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(uiState.fileLoaded, uiState.videoWidth, uiState.videoHeight, uiState.orientationMode, uiState.isAutoRotation) {
        updateOrientation()
    }
}

