package com.potato.player.feature.player.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ScreenLockLandscape
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.player.feature.player.OrientationMode

@Composable
fun PlayerQuickActions(
    currentDecoder: String,
    orientationMode: OrientationMode,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onSelectDecoder: () -> Unit,
    onToggleOrientation: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonModifier = PlayerControlsStyles.iconButtonModifier

    // rememberUpdatedState so lambdas stable-wrapped in remember{} always call latest
    val onAudioRef       = rememberUpdatedState(onSelectAudioTrack)
    val onSubtitleRef    = rememberUpdatedState(onSelectSubtitleTrack)
    val onDecoderRef     = rememberUpdatedState(onSelectDecoder)
    val onOrientationRef = rememberUpdatedState(onToggleOrientation)
    val onMoreRef        = rememberUpdatedState(onMoreOptions)

    val handleAudio       = remember { { onAudioRef.value()       } }
    val handleSubtitle    = remember { { onSubtitleRef.value()    } }
    val handleDecoder     = remember { { onDecoderRef.value()     } }
    val handleOrientation = remember { { onOrientationRef.value() } }
    val handleMore        = remember { { onMoreRef.value()        } }

    val orientationIcon = when (orientationMode) {
        OrientationMode.AUTO -> Icons.Default.ScreenRotation
        OrientationMode.LOCK_LANDSCAPE -> Icons.Default.ScreenLockLandscape
        OrientationMode.LOCK_PORTRAIT -> Icons.Default.ScreenLockPortrait
    }

    Row(
        modifier              = modifier,
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = handleAudio,    modifier = buttonModifier) {
            Icon(Icons.Default.AudioFile, contentDescription = "Audio track", tint = Color.White)
        }
        IconButton(onClick = handleSubtitle, modifier = buttonModifier) {
            Icon(Icons.Default.Subtitles,  contentDescription = "Subtitles",   tint = Color.White)
        }
        IconButton(onClick = handleDecoder,  modifier = buttonModifier) {
            Text(
                text = currentDecoder,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        IconButton(onClick = handleOrientation, modifier = buttonModifier) {
            Icon(
                imageVector = orientationIcon,
                contentDescription = "Orientation: ${orientationMode.label}",
                tint = Color.White
            )
        }
        IconButton(onClick = handleMore, modifier = buttonModifier) {
            Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
        }
    }
}
