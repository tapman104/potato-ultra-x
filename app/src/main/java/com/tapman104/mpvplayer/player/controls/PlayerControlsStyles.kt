package com.tapman104.mpvplayer.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared styling constants for player overlay controls. */
internal object PlayerControlsStyles {
    const val ANIM_DURATION_MS = 200
    val textShadowStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp
    )

    val iconButtonModifier = Modifier
        .size(40.dp)
        .background(Color.Black.copy(alpha = 0.5f), shape = CircleShape)

    @Composable
    fun rememberSliderColors(): SliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
    )
}
