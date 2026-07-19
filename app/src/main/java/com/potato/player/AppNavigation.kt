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
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (!repository.isPaused.value) {
                    repository.pause()
                }
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
            DisposableEffect(Unit) {
                repository.enterStandby()
                onDispose { }
            }
            HomeScreen(
                onNavigateToPlayer = { uri, title ->
                    navController.navigate(PlayerRoute(videoUri = uri, title = title))
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val activity = LocalContext.current.findActivity()

            DisposableEffect(Unit) {
                engine.init()
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
