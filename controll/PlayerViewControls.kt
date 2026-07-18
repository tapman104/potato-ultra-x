package com.tapman104.mpvplayer.player.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tapman104.mpvplayer.player.model.ViewMode

@Composable
fun PlayerViewControls(
    currentViewMode: ViewMode,
    onCycleViewMode: () -> Unit,
    onRotate: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val onCycleRef = rememberUpdatedState(onCycleViewMode)
    val onRotateRef = rememberUpdatedState(onRotate)
    val onPipRef = rememberUpdatedState(onEnterPip)

    val handleCycle = remember { { onCycleRef.value() } }
    val handleRotate = remember { { onRotateRef.value() } }
    val handlePip = remember { { onPipRef.value() } }

    val aspectRatioDescription = remember(currentViewMode) {
        "Cycle view mode: ${currentViewMode.name}"
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(
            onClick = handleCycle,
            modifier = PlayerControlsStyles.iconButtonModifier
        ) {
            Icon(
                imageVector = Icons.Default.AspectRatio,
                contentDescription = aspectRatioDescription,
                tint = Color.White
            )
        }
        IconButton(
            onClick = handleRotate,
            modifier = PlayerControlsStyles.iconButtonModifier
        ) {
            Icon(
                imageVector = Icons.Default.ScreenRotation,
                contentDescription = "Rotate video",
                tint = Color.White
            )
        }
        IconButton(
            onClick = handlePip,
            modifier = PlayerControlsStyles.iconButtonModifier
        ) {
            Icon(
                imageVector = Icons.Default.PictureInPicture,
                contentDescription = "Picture-in-picture",
                tint = Color.White
            )
        }
    }
}
