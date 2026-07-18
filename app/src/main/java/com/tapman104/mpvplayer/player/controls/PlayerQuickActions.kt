package com.tapman104.mpvplayer.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapman104.mpvplayer.player.model.DecodeMode

@Composable
fun PlayerQuickActions(
    decodeMode: DecodeMode,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onDecodeModeClick: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val buttonModifier = PlayerControlsStyles.iconButtonModifier

    val onAudioRef = rememberUpdatedState(onSelectAudioTrack)
    val onSubtitleRef = rememberUpdatedState(onSelectSubtitleTrack)
    val onDecodeRef = rememberUpdatedState(onDecodeModeClick)
    val onMoreRef = rememberUpdatedState(onMoreOptions)

    val handleAudio = remember { { onAudioRef.value() } }
    val handleSubtitle = remember { { onSubtitleRef.value() } }
    val handleDecode = remember { { onDecodeRef.value() } }
    val handleMore = remember { { onMoreRef.value() } }

    val decodeLabel = remember(decodeMode) {
        when (decodeMode) {
            DecodeMode.HW -> "HW"
            DecodeMode.HWPlus -> "HW+"
            DecodeMode.SW -> "SW"
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = handleAudio,
            modifier = buttonModifier
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = "Audio track", tint = Color.White)
        }
        IconButton(
            onClick = handleSubtitle,
            modifier = buttonModifier
        ) {
            Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
        }
        IconButton(
            onClick = handleDecode,
            modifier = buttonModifier
        ) {
            Text(
                text = decodeLabel,
                color = Color.White,
                fontSize = 12.sp
            )
        }
        IconButton(
            onClick = handleMore,
            modifier = buttonModifier
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
        }
    }
}
