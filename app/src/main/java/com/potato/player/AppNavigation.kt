package com.potato.player

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
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
                    val encodedUri = Uri.encode(uri)
                    val encodedTitle = Uri.encode(title)
                    navController.navigate(PlayerRoute(videoUri = encodedUri, title = encodedTitle))
                }
            )
        }

        composable<PlayerRoute> { backStackEntry ->
            val route: PlayerRoute = backStackEntry.toRoute()
            val decodedUri = remember(route.videoUri) { Uri.decode(route.videoUri) }
            val decodedTitle = remember(route.title) { Uri.decode(route.title) }

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
                encodedUri = decodedUri,
                title      = decodedTitle,
                viewModel  = playerViewModel,
                onBack     = { navController.popBackStack() }
            )
        }
    }
}
