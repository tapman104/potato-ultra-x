package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun SubtitleAppearanceDialog(
    initialSize: Float,
    initialPosition: Float,
    onApply: (size: Float, position: Float) -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit
) {
    var size by remember(initialSize) { mutableFloatStateOf(initialSize) }
    var position by remember(initialPosition) { mutableFloatStateOf(initialPosition) }

    val accentColor = Color(0xFF90CAF9)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title
            Text(
                text = "Subtitle Appearance",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Subtitle Size
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Size", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    Text(
                        "${"%.1f".format(size)}×",
                        color = accentColor,
                        fontSize = 13.sp
                    )
                }
                Slider(
                    value = size,
                    onValueChange = { size = it },
                    valueRange = 0.5f..3.0f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Subtitle Position
            Column(modifier = Modifier.fillMaxWidth()) {
                val positionLabel = when {
                    position >= 0.66f -> "Top"
                    position >= 0.33f -> "Middle"
                    else -> "Bottom"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Position", color = Color(0xFFAAAAAA), fontSize = 14.sp)
                    Text(positionLabel, color = accentColor, fontSize = 13.sp)
                }
                Slider(
                    value = position,
                    onValueChange = { position = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset", color = Color(0xFFAAAAAA))
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", color = Color(0xFFAAAAAA))
                }
                Button(
                    onClick = { onApply(size, position) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply", color = Color(0xFF1E1E1E), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
