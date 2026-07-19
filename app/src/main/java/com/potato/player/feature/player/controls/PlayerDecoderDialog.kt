package com.potato.player.feature.player.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

data class DecoderOption(
    val title: String,
    val badge: String,
    val description: String,
    val mpvValue: String
)

private val decoderOptions = listOf(
    DecoderOption(
        title = "Hardware+ (HW+)",
        badge = "HW+",
        description = "Recommended auto-copy mode. Supports video filters and shaders while keeping high hardware efficiency.",
        mpvValue = "mediacodec,mediacodec-copy,no"
    ),
    DecoderOption(
        title = "Hardware Direct (HW)",
        badge = "HW",
        description = "Direct hardware decoding. Maximum playback speed and lowest battery consumption.",
        mpvValue = "mediacodec"
    ),
    DecoderOption(
        title = "Software (SW)",
        badge = "SW",
        description = "CPU-based software decoding. Highest compatibility for rare or complex codecs.",
        mpvValue = "no"
    )
)

@Composable
fun PlayerDecoderDialog(
    currentDecoder: String,
    onSelectDecoder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Video Decoder",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Choose the hardware or software decoding engine used by MPV.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                decoderOptions.forEach { option ->
                    val isSelected = currentDecoder == option.badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.15f)
                                else Color.Transparent
                            )
                            .clickable {
                                onSelectDecoder(option.mpvValue)
                                onDismiss()
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSelectDecoder(option.mpvValue)
                                onDismiss()
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color.White,
                                unselectedColor = Color.White.copy(alpha = 0.5f)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = option.title,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = option.description,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text = "CLOSE",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
