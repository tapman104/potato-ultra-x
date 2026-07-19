package com.potato.player.feature.home

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
import androidx.navigation.NavController
import com.potato.player.util.findActivity

@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.lockToPortrait(activity)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.lockToPortrait(activity)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVideoPicked(uri.toString())
            val encoded = Uri.encode(uri.toString())
            navController.navigate("player/$encoded")
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
