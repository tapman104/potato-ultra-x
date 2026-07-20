package com.potato.player

import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.PlayerRepository
import com.potato.player.feature.home.HomeScreen
import com.potato.player.feature.player.PlayerScreen
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.PlayerViewModelFactory
import com.potato.player.util.findActivity
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data class PlayerRoute(
    val videoUri: String,
    val title: String = ""
)

@Composable
fun AppNavigation(
    navController: NavHostController,
    engine: MpvEngine,
    repository: PlayerRepository
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // App-level lifecycle monitoring: pause MPV when app goes to background (onPause)
    // and resume playback when returning (onResume) — guards against warm-resume black screen
    // where the SurfaceView is preserved and surfaceChanged never fires to re-trigger loadFile.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (!repository.isPaused.value) {
                        repository.pause()
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Only unpause if a file is already loaded. If the surface was destroyed
                    // and recreated (GPU context loss path), loadFile() will be called by
                    // attachSurfaceInternal → surfaceReadyCallback, which unpauses itself.
                    // This branch handles the common case where the surface is preserved.
                    if (repository.fileLoaded.value) {
                        repository.play()
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onNavigateToPlayer = { uri, title ->
                    navController.navigate(PlayerRoute(videoUri = uri, title = title))
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val activity = LocalContext.current.findActivity()

            DisposableEffect(route.videoUri) {
                onDispose {
                    repository.enterStandby()
                }
            }

            val playerViewModel: PlayerViewModel = viewModel(
                factory = PlayerViewModelFactory(repository)
            )

            PlayerScreen(
                videoUri  = route.videoUri,
                title     = route.title,
                viewModel = playerViewModel,
                onBack    = {
                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    navController.popBackStack()
                }
            )
        }
    }
}
