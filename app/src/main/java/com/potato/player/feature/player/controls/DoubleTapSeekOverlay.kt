package com.potato.player.feature.player.controls

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class DoubleTapSeekState(
    val isForward: Boolean,
    val totalSeconds: Int,
    val triggerId: Long = System.currentTimeMillis()
)

@Composable
fun DoubleTapSeekOverlay(
    seekState: DoubleTapSeekState?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = seekState != null,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier.fillMaxSize()
    ) {
        seekState?.let { state ->
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Curved ripple zone on left or right half
                val alignment = if (state.isForward) Alignment.CenterEnd else Alignment.CenterStart
                val shape = if (state.isForward) {
                    RoundedCornerShape(topStart = 160.dp, bottomStart = 160.dp)
                } else {
                    RoundedCornerShape(topEnd = 160.dp, bottomEnd = 160.dp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.42f)
                        .align(alignment)
                        .clip(shape)
                        .background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (state.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                            contentDescription = if (state.isForward) "Fast forward" else "Fast rewind",
                            tint = Color.White,
                            modifier = Modifier.size(46.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (state.isForward) "+${state.totalSeconds}s" else "-${state.totalSeconds}s",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
