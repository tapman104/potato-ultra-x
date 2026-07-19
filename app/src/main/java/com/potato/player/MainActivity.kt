package com.potato.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import android.content.ContentResolver
import android.content.Intent
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.PlayerRepository

class MainActivity : ComponentActivity() {
    private var pendingIntent by mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntent = intent

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

            val fileLoaded by repository.fileLoaded.collectAsState()
            val isPaused by repository.isPaused.collectAsState()

            DisposableEffect(fileLoaded, isPaused) {
                if (fileLoaded && !isPaused) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            AppNavigation(
                navController = navController,
                engine        = engine,
                repository    = repository
            )

            LaunchedEffect(navController, pendingIntent) {
                pendingIntent?.let { intent ->
                    handleViewIntent(intent, navController)
                    pendingIntent = null
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent = intent
    }

    private fun handleViewIntent(intent: Intent?, navController: NavController) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri = intent.data ?: return
            if (uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == "content") {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if permission cannot be persisted
                }
            }
            val title = uri.lastPathSegment ?: ""
            navController.navigate(PlayerRoute(videoUri = uri.toString(), title = title))
        }
    }
}
