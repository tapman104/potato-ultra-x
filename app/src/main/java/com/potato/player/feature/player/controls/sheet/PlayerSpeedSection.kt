package com.potato.player.feature.player.controls.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.feature.player.controls.PlayerControlsStyles
import kotlin.math.abs
import kotlin.math.round

private val speedPresets = listOf(
    listOf(0.25, 0.5, 0.75, 1.0),
    listOf(1.25, 1.5, 2.0, 3.0)
)

// ponytail: split only, zero new logic
@Composable
fun PlayerSpeedSection(
    currentSpeed: Double,
    onSelectSpeed: (Double) -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val formattedCurrent = "${round(currentSpeed * 100) / 100.0}x"

    // Playback Speed Section
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Speed,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "PLAYBACK SPEED",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(accentColor.copy(alpha = 0.2f))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = formattedCurrent,
                color = accentColor,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Presets",
        color = Color.White.copy(alpha = 0.8f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))

    // Presets grid (2 rows x 4 columns)
    speedPresets.forEach { rowPresets ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rowPresets.forEach { preset ->
                val isSelected = abs(currentSpeed - preset) < 0.02
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                        .clickable { onSelectSpeed(preset) }
                        .padding(vertical = 10.dp)
                ) {
                    Text(
                        text = "${preset}x",
                        color = if (isSelected) accentColor else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Fine-Tuning Slider
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Fine-Tuning",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "0.25x – 4.0x",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp
        )
    }
    Spacer(modifier = Modifier.height(4.dp))

    Slider(
        value = currentSpeed.toFloat().coerceIn(0.25f, 4.0f),
        onValueChange = { valFloat ->
            val rounded = round(valFloat * 100) / 100.0
            onSelectSpeed(rounded)
        },
        valueRange = 0.25f..4.0f,
        colors = PlayerControlsStyles.rememberSliderColors(),
        modifier = Modifier.fillMaxWidth()
    )

    if (abs(currentSpeed - 1.0) >= 0.02) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { onSelectSpeed(1.0) }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "RESET TO 1.0X",
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
