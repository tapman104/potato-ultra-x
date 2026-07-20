package com.potato.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.potato.player.engine.MpvEngine
import com.potato.player.engine.PlayerRepository
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    private var pendingIntent by mutableStateOf<Intent?>(null)
    private var mpvEngine: MpvEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntent = intent

        // Set orientation before setContent to prevent portrait flash
        if (intent?.action == Intent.ACTION_VIEW) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val navController = rememberNavController()

            // Engine lives for the entire activity lifetime
            val engine = remember { MpvEngine(applicationContext) }
            val repository = remember { PlayerRepository(engine) }
            mpvEngine = engine

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
                if (pendingIntent == null) return@LaunchedEffect
                navController.currentBackStackEntryFlow.first()
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
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            val flags = intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (flags != 0) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // transient grant only — still have access for this session
                }
            }
            try {
                contentResolver.openFileDescriptor(uri, "r")?.close()
            } catch (e: Exception) {
                return // URI unreadable, bail
            }
        }

        val title = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        navController.navigate(PlayerRoute(videoUri = uri.toString(), title = title))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        mpvEngine?.surface?.isRotating?.set(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

