package com.potato.player.feature.player.controls

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.util.TimeFormatter

@Composable
fun SeekPreviewBubble(durationMs: Long, displayFraction: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val bubbleWidthDp = 64.dp
        val maxOffsetX = if (maxWidth > bubbleWidthDp) maxWidth - bubbleWidthDp else 0.dp
        val targetOffsetX = (maxWidth * displayFraction.coerceIn(0f, 1f)) - (bubbleWidthDp / 2)
        val clampedOffsetX = targetOffsetX.coerceIn(0.dp, maxOffsetX)

        Box(
            modifier = Modifier
                .offset(x = clampedOffsetX)
                .width(bubbleWidthDp)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF1E1E1E)
                ),
                border = BorderStroke(1.dp, Color(0xFF90CAF9))
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = TimeFormatter.formatMs((displayFraction.coerceIn(0f, 1f) * durationMs).toLong()),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
