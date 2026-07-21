package com.potato.player.feature.player

import android.view.SurfaceView
import android.os.Build
import android.app.PictureInPictureParams
import androidx.compose.animation.*
import com.potato.player.util.MediaMetadataRepository
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
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

    var controlsVisible by remember { mutableStateOf(true) }
    var doubleTapSeekState by remember { mutableStateOf<DoubleTapSeekState?>(null) }
    var isLongPressActive by remember { mutableStateOf(false) }

    // Auto-hide controls after 4 seconds of inactivity when playing & not dragging seek bar
    LaunchedEffect(controlsVisible, uiState.isPlaying, uiState.dragPositionSec) {
        if (controlsVisible && uiState.isPlaying && uiState.dragPositionSec == null) {
            delay(4000L)
            controlsVisible = false
        }
    }

    LaunchedEffect(uiState.isInPipMode) {
        if (uiState.isInPipMode) {
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

    // Clean up both surface callbacks when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            viewModel.surface.setSurfaceReadyCallback(null)
            viewModel.surface.setSurfaceReattachedCallback(null)
        }
    }

    // Load the video once the surface is ready; also handles config-change re-attach.
    // Bug fix: use hasAttachedSurface() (not hasSurface()) so we only fire the
    // immediate path when the surface is truly attached — prevents double-loadFile
    // when pendingAttachSurface is set but attachedSurface is still null.
    LaunchedEffect(videoUri) {
        viewModel.surface.setSurfaceReadyCallback {
            viewModel.loadFile(videoUri, title)
        }
        // Resume path: re-attach surface on return from Recents without restarting the file.
        viewModel.surface.setSurfaceReattachedCallback {
            viewModel.resumeAfterSurfaceReattach()
        }
        if (viewModel.surface.hasAttachedSurface()) {
            viewModel.loadFile(videoUri, title)
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
                .pointerInput(uiState.isInPipMode) {
                    if (uiState.isInPipMode) return@pointerInput
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
                    .padding(top = if (controlsVisible) 72.dp else 36.dp)
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
                visible = controlsVisible,
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

            // ── Bottom controls ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .navigationBarsPadding()
            ) {
                PlayerBottomControls(
                    isPlaying        = uiState.isPlaying,
                    // ViewModel stores seconds; controls work in milliseconds
                    currentPositionMs = (uiState.positionSec * 1000.0).toLong(),
                    durationMs        = (uiState.durationSec * 1000.0).toLong(),
                    cachedPositionMs  = (uiState.cachedSec * 1000.0).toLong(),
                    bufferDurationMs  = (uiState.cacheDurationSec * 1000.0).toLong(),
                    isAutoRotation    = uiState.isAutoRotation,
                    onTogglePlay      = viewModel::togglePlay,
                    onSeekGesture     = { ms -> viewModel.onSliderDragChange(ms / 1000.0) },
                    onSeekCommit      = { ms -> viewModel.onSliderDragEnd(ms / 1000.0) },
                    onDragStart       = { viewModel.onSliderDragStart(uiState.positionSec) },
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
                    viewModel.resumeAfterSurfaceReattach()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        applyInsets()
        updateOrientation()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    LaunchedEffect(uiState.fileLoaded, uiState.videoWidth, uiState.videoHeight, uiState.orientationMode, uiState.isAutoRotation) {
        updateOrientation()
    }
}

