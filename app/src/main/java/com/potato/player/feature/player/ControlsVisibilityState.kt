package com.potato.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

class ControlsVisibilityState(
    initialVisible: Boolean = true
) {
    var isVisible by mutableStateOf(initialVisible)
        private set

    fun show() {
        isVisible = true
    }

    fun hide() {
        isVisible = false
    }

    fun toggle() {
        isVisible = !isVisible
    }
}

@Composable
fun rememberControlsVisibilityState(
    isPlaying: Boolean,
    dragPositionSec: Double?,
    isInPipMode: Boolean,
    hideDelayMs: Long = PlayerUiConstants.CONTROLS_HIDE_DELAY_MS
): ControlsVisibilityState {
    val state = remember { ControlsVisibilityState() }

    LaunchedEffect(state.isVisible, isPlaying, dragPositionSec) {
        if (state.isVisible && isPlaying && dragPositionSec == null) {
            delay(hideDelayMs)
            state.hide()
        }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            state.hide()
        }
    }

    return state
}
