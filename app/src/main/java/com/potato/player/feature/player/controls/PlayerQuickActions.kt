package com.potato.player.feature.player.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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

@Composable
fun PlayerQuickActions(
    currentDecoder: String,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onSelectDecoder: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
    showMoreMenu: Boolean = false,
    onMoreMenuDismiss: () -> Unit = {},
    onShowAudioDialog: () -> Unit = {},
    onShowSubtitleDialog: () -> Unit = {},
    onShowSpeedDialog: () -> Unit = {},
) {
    val buttonModifier = PlayerControlsStyles.iconButtonModifier

    // rememberUpdatedState so lambdas stable-wrapped in remember{} always call latest
    val onAudioRef    = rememberUpdatedState(onSelectAudioTrack)
    val onSubtitleRef = rememberUpdatedState(onSelectSubtitleTrack)
    val onDecoderRef  = rememberUpdatedState(onSelectDecoder)
    val onMoreRef     = rememberUpdatedState(onMoreOptions)
    val onShowAudioDialogRef = rememberUpdatedState(onShowAudioDialog)
    val onShowSubtitleDialogRef = rememberUpdatedState(onShowSubtitleDialog)
    val onShowSpeedDialogRef = rememberUpdatedState(onShowSpeedDialog)
    val onMoreMenuDismissRef = rememberUpdatedState(onMoreMenuDismiss)

    val handleAudio    = remember { { onAudioRef.value()    } }
    val handleSubtitle = remember { { onSubtitleRef.value() } }
    val handleDecoder  = remember { { onDecoderRef.value()  } }
    val handleMore     = remember { { onMoreRef.value()     } }
    val handleShowAudioDialog = remember { { onShowAudioDialogRef.value() } }
    val handleShowSubtitleDialog = remember { { onShowSubtitleDialogRef.value() } }
    val handleShowSpeedDialog = remember { { onShowSpeedDialogRef.value() } }
    val handleMoreMenuDismiss = remember { { onMoreMenuDismissRef.value() } }

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
        IconButton(onClick = handleShowSpeedDialog, modifier = buttonModifier) {
            Icon(Icons.Default.Speed, contentDescription = "Playback speed", tint = Color.White)
        }
        Box {
            IconButton(onClick = handleMore, modifier = buttonModifier) {
                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = Color.White)
            }
            DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = handleMoreMenuDismiss
            ) {
                DropdownMenuItem(
                    text = { Text("Audio Track") },
                    leadingIcon = { Icon(Icons.Default.Audiotrack, null) },
                    onClick = handleShowAudioDialog
                )
                DropdownMenuItem(
                    text = { Text("Subtitle Track") },
                    leadingIcon = { Icon(Icons.Default.ClosedCaption, null) },
                    onClick = handleShowSubtitleDialog
                )
                DropdownMenuItem(
                    text = { Text("Playback Speed") },
                    leadingIcon = { Icon(Icons.Default.Speed, null) },
                    onClick = handleShowSpeedDialog
                )
            }
        }
    }
}
