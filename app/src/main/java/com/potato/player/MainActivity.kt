package com.potato.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.PlayerRepository
import com.potato.player.feature.home.HomeScreen
import com.potato.player.feature.home.HomeViewModel
import com.potato.player.feature.player.PlayerScreen
import com.potato.player.feature.player.PlayerViewModel
import com.potato.player.feature.player.PlayerViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val navController = rememberNavController()

            // Engine lives for the entire activity lifetime
            val engine = remember { MpvEngine(applicationContext) }
            val repository = remember { PlayerRepository(engine) }

            DisposableEffect(Unit) {
                engine.init()
                onDispose { engine.destroy() }
            }

            NavHost(navController = navController, startDestination = "home") {

                composable("home") {
                    HomeScreen(navController = navController)
                }

                composable("player/{encodedUri}") { backStackEntry ->
                    val encodedUri = backStackEntry.arguments?.getString("encodedUri") ?: return@composable
                    val playerViewModel: PlayerViewModel = viewModel(
                        factory = PlayerViewModelFactory(repository)
                    )
                    PlayerScreen(
                        encodedUri    = encodedUri,
                        viewModel     = playerViewModel,
                        navController = navController
                    )
                }
            }
        }
    }
}
