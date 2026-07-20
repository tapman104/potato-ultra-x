package com.potato.player.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.pm.ActivityInfo
import com.potato.player.util.MediaMetadataRepository
import com.potato.player.util.findActivity
import com.potato.player.util.lockOrientation

@Composable
fun HomeScreen(
    onNavigateToPlayer: (videoUri: String, title: String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(pendingUri) {
        pendingUri?.let { uri ->
            val uriStr = uri.toString()
            viewModel.onVideoPicked(uriStr)
            val title = MediaMetadataRepository.resolveTitle(context, uri)
            onNavigateToPlayer(uriStr, title)
            pendingUri = null
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pendingUri = uri
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = { launcher.launch("video/*") },
            modifier = Modifier.padding(16.dp)
        ) {
            Text("Open Video")
        }
    }
}

