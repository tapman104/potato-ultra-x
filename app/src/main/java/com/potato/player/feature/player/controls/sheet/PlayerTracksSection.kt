package com.potato.player.feature.player.controls.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ponytail: split only, zero new logic
@Composable
fun PlayerTracksSection(
    accentColor: Color,
    onShowAudioDialog: () -> Unit,
    onShowSubtitleDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "TRACKS",
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp
    )
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onShowAudioDialog,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Audiotrack,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Audio", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onShowSubtitleDialog,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ClosedCaption,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = accentColor
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Subtitles", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
