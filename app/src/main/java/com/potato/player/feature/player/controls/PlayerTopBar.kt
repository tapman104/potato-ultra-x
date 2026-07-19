package com.potato.player.feature.player.controls

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlayerTopBar(
    fileName: String,
    onBack: () -> Unit,
    onSelectAudioTrack: () -> Unit,
    onSelectSubtitleTrack: () -> Unit,
    onMoreOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onBackRef     = rememberUpdatedState(onBack)
    val handleBack    = remember { { onBackRef.value() } }

    Row(
        modifier          = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick  = handleBack,
            modifier = PlayerControlsStyles.iconButtonModifier
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint               = Color.White
            )
        }

        // File name — truncates with ellipsis if too long
        Text(
            text      = fileName,
            color     = Color.White,
            fontSize  = 14.sp,
            maxLines  = 1,
            overflow  = TextOverflow.Ellipsis,
            modifier  = Modifier
                .padding(start = 4.dp)
                .weight(1f)
        )

        // Audio / Subtitle / More icons
        PlayerQuickActions(
            onSelectAudioTrack    = onSelectAudioTrack,
            onSelectSubtitleTrack = onSelectSubtitleTrack,
            onMoreOptions         = onMoreOptions
        )
    }
}
