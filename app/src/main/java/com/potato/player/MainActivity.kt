package com.potato.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.compose.rememberNavController
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.PlayerRepository

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

            AppNavigation(
                navController = navController,
                engine        = engine,
                repository    = repository
            )
        }
    }
}
