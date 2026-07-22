package com.potato.player.feature.player

import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
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
    BackHandler { onBack() }
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
        isInPipMode = uiState.isInPipMode
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
        viewModel.onSurfaceReady(videoUri, title)
        viewModel.onSurfaceReattached()
        onDispose {
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
            factory = { ctx -> viewModel.createSurfaceView(ctx) },
            modifier = Modifier.fillMaxSize()
        )

        // ── Gesture & Tap Overlay ────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uiState.isInPipMode) {
                    if (uiState.isInPipMode) return@pointerInput
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
        if (!uiState.isInPipMode) {
            DoubleTapSeekOverlay(seekState = doubleTapSeekState)
        }

        // ── Top Hold for 2x Fast-Forward Banner ──────────────────────────────
        if (!uiState.isInPipMode) {
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

        if (uiState.fileLoaded && !uiState.isInPipMode) {

            // ── Top bar ──────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsState.isVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier
                    .align(Alignment.TopCenter)
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
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .navigationBarsPadding()
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
        if (!uiState.isInPipMode) {
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
        if (!uiState.fileLoaded) return
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

    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        fun applyInsets() {
            controller?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyInsets()
                updateOrientation()
                // Always call resumeAfterSurfaceReattach() on resume when a file is loaded.
                // reattachSurface() is idempotent (guarded by isMpvRendering + attachedSurface
                // identity check), so this is safe to call even when MPV is already rendering.
                // Removing the old hasAttachedSurface() guard fixes the black screen on devices
                // where the Surface Java object survives lock/unlock and surfaceCreated() never
                // fires — in that case attachedSurface was non-null, the guard was false, and
                // resumeAfterSurfaceReattach() was never called even though the EGL context
                // had been invalidated by the display driver.
                if (uiState.fileLoaded) {
                    viewModel.onSurfaceReattached()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        applyInsets()
        updateOrientation()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (activity?.isInPictureInPictureMode == false) {
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(uiState.fileLoaded, uiState.videoWidth, uiState.videoHeight, uiState.orientationMode, uiState.isAutoRotation) {
        updateOrientation()
    }
}

