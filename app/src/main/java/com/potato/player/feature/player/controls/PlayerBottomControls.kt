package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.util.TimeFormatter

@Composable
fun PlayerBottomControls(
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    onTogglePlay: () -> Unit,
    onSeekGesture: (Long) -> Unit,    // called continuously during drag
    onSeekCommit: (Long) -> Unit,     // called once on finger lift
    onDragStart: () -> Unit,          // tells repository to suppress echo-backs
    onDragEnd: () -> Unit,            // tells repository to re-enable echo-backs
    modifier: Modifier = Modifier,
) {
    // dragFraction: -1f means "not dragging"; any >= 0f means actively scrubbing
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    val sliderValue = if (durationMs > 0L) currentPositionMs.toFloat() / durationMs else 0f
    val displayFraction = if (dragFraction >= 0f) dragFraction else sliderValue

    val durationString = remember(durationMs) { TimeFormatter.formatMs(durationMs) }
    val sliderColors   = PlayerControlsStyles.rememberSliderColors()

    // rememberUpdatedState: lambdas captured in remember{} always see the latest values
    val onSeekGestureRef  = rememberUpdatedState(onSeekGesture)
    val onSeekCommitRef   = rememberUpdatedState(onSeekCommit)
    val onDragStartRef    = rememberUpdatedState(onDragStart)
    val onDragEndRef      = rememberUpdatedState(onDragEnd)
    val durationMsRef     = rememberUpdatedState(durationMs)

    val onValueChange = remember {
        { fraction: Float ->
            if (dragFraction < 0f) {
                // First movement of this gesture — notify repository to suppress MPV echo-backs
                onDragStartRef.value()
            }
            dragFraction = fraction
            val targetMs = (fraction * durationMsRef.value).toLong()
            onSeekGestureRef.value(targetMs)
        }
    }

    val onValueChangeFinished = remember {
        {
            val finalFraction = if (dragFraction >= 0f) dragFraction else sliderValue
            val targetMs = (finalFraction.coerceIn(0f, 1f) * durationMsRef.value).toLong()
            onSeekCommitRef.value(targetMs)
            onDragEndRef.value()
            dragFraction = -1f
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        // Time row: current position left, total duration right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text  = TimeFormatter.formatMs(
                    if (dragFraction >= 0f) (dragFraction * durationMs).toLong()
                    else currentPositionMs
                ),
                color    = Color.White,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text     = durationString,
                color    = Color.White,
                fontSize = 13.sp
            )
        }

        // Seek bar
        Slider(
            value               = displayFraction.coerceIn(0f, 1f),
            onValueChange       = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            colors              = sliderColors,
            modifier            = Modifier.fillMaxWidth()
        )

        // Play / Pause — white circle, black icon
        Box(
            contentAlignment = Alignment.Center,
            modifier         = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick  = onTogglePlay,
                modifier = Modifier
                    .size(64.dp)
                    .background(Color.White, shape = CircleShape)
            ) {
                Icon(
                    imageVector     = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint            = Color.Black,
                    modifier        = Modifier.size(36.dp)
                )
            }
        }
    }
}
