package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSeekBar(progress: Float, buffered: Float, onValueChange: (Float) -> Unit, onValueChangeFinished: () -> Unit, modifier: Modifier = Modifier) {
    val baseColors = PlayerControlsStyles.rememberSliderColors()
    val sliderColors = SliderDefaults.colors(
        thumbColor           = baseColors.thumbColor,
        activeTrackColor     = baseColors.activeTrackColor,
        inactiveTrackColor   = Color.Transparent,
        activeTickColor      = Color.Transparent,
        inactiveTickColor    = Color.Transparent
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Background inactive track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.24f))
        )

        // Buffer indicator track
        if (buffered > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(maxWidth * buffered)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.5f))
            )
        }

        // Slider on top (inactive track transparent to let buffer & background show through)
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value                 = progress.coerceIn(0f, 1f),
            onValueChange         = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            colors                = sliderColors,
            interactionSource     = interactionSource,
            thumb                 = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors            = sliderColors,
                    thumbSize         = DpSize(20.dp, 20.dp)
                )
            },
            modifier              = Modifier.fillMaxWidth()
        )
    }
}
