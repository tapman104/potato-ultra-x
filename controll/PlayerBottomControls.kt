package com.tapman104.mpvplayer.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapman104.mpvplayer.util.TimeFormatter

@Composable
fun PlayerBottomControls(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferPositionMs: Long = 0L,
    gestureSeekPreviewMs: Long = -1L,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekGesture: (Long) -> Unit = {},
    onSeekPreviewMs: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val displayPositionMs = if (gestureSeekPreviewMs >= 0L) gestureSeekPreviewMs else currentPositionMs
    val sliderValue = if (durationMs > 0L) displayPositionMs.toFloat() / durationMs else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else sliderValue

    val durationString = remember(durationMs) { TimeFormatter.formatMs(durationMs) }
    val sliderColors = PlayerControlsStyles.rememberSliderColors()

    val onSeekRef = rememberUpdatedState(onSeek)
    val onSeekGestureRef = rememberUpdatedState(onSeekGesture)
    val onSeekPreviewMsRef = rememberUpdatedState(onSeekPreviewMs)
    val durationMsRef = rememberUpdatedState(durationMs)

    val onValueChange = remember {
        { fraction: Float ->
            dragFraction = fraction
            val targetMs = (fraction * durationMsRef.value).toLong()
            onSeekGestureRef.value(targetMs)
            onSeekPreviewMsRef.value(targetMs)
        }
    }
    val onValueChangeFinished = remember {
        {
            val currentFraction = if (dragFraction >= 0f) dragFraction else sliderValue
            val targetMs = (currentFraction.coerceIn(0f, 1f) * durationMsRef.value).toLong()
            onSeekRef.value(targetMs)
            onSeekPreviewMsRef.value(-1L)
            dragFraction = -1f
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = TimeFormatter.formatMs(displayPositionMs),
                color = Color.White,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = durationString,
                color = Color.White,
                fontSize = 13.sp
            )
        }

        Slider(
            value = displayFraction.coerceIn(0f, 1f),
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth()
        )

        PlayPauseCircleButton(
            isPlaying = isPlaying,
            onTogglePlay = onTogglePlay
        )
    }
}

@Composable
private fun PlayPauseCircleButton(
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier
                .size(64.dp)
                .background(Color.White, shape = CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}