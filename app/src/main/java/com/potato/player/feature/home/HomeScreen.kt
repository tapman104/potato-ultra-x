package com.potato.player.feature.home

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.content.pm.ActivityInfo
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.potato.player.util.MediaMetadataRepository
import com.potato.player.util.findActivity
import com.potato.player.util.lockOrientation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (videoUri: String, title: String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity) {
        val window = activity?.window
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        lockOrientation(activity, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
        controller?.show(WindowInsetsCompat.Type.systemBars())
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(pendingUri) {
        pendingUri?.let { uri ->
            val uriStr = uri.toString()
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

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Potato Player") }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Folder, contentDescription = "Local Files") },
                    label = { Text("Local Files") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.History, contentDescription = "Recent") },
                    label = { Text("Recent") }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            if (selectedTab == 0) {
                Button(
                    onClick = { launcher.launch("video/*") },
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text("Open Video")
                }
            } else {
                Text("Coming soon")
            }
        }
    }
}

