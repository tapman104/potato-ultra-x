package com.tapman104.mpvplayer.player.controls

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapman104.mpvplayer.player.model.DecodeMode

@Composable
fun PlayerTopBar(
    fileName: String,
    onBack: () -> Unit,
    decodeMode: DecodeMode,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onDecodeModeClick: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBackRef = rememberUpdatedState(onBack)
    val onSelectAudioTrackRef = rememberUpdatedState(onSelectAudioTrack)
    val onSelectSubtitleTrackRef = rememberUpdatedState(onSelectSubtitleTrack)
    val onDecodeModeClickRef = rememberUpdatedState(onDecodeModeClick)
    val onMoreOptionsRef = rememberUpdatedState(onMoreOptions)

    val handleBack = remember { { onBackRef.value() } }
    val handleAudio = remember { { onSelectAudioTrackRef.value() } }
    val handleSubtitle = remember { { onSelectSubtitleTrackRef.value() } }
    val handleDecode = remember { { onDecodeModeClickRef.value() } }
    val handleMore = remember { { onMoreOptionsRef.value() } }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = handleBack,
            modifier = PlayerControlsStyles.iconButtonModifier
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
        Text(
            text = fileName,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f)
        )
        PlayerQuickActions(
            decodeMode = decodeMode,
            onSelectAudioTrack = handleAudio,
            onSelectSubtitleTrack = handleSubtitle,
            onDecodeModeClick = handleDecode,
            onMoreOptions = handleMore
        )
    }
}
