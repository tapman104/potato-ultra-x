package com.potato.player

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
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

private val AmoledDarkColorScheme = darkColorScheme(
    background        = Color(0xFF000000),
    surface           = Color(0xFF000000),
    surfaceVariant    = Color(0xFF0D0D0D),
    surfaceContainer  = Color(0xFF0A0A0A),
    surfaceContainerHigh = Color(0xFF111111),
    surfaceContainerHighest = Color(0xFF1A1A1A),
    onBackground      = Color(0xFFFFFFFF),
    onSurface         = Color(0xFFFFFFFF),
    primary           = Color(0xFF90CAF9),
    onPrimary         = Color(0xFF000000),
    secondary         = Color(0xFF80CBC4),
    onSecondary       = Color(0xFF000000)
)

class MainActivity : ComponentActivity() {
    private var pendingIntent by mutableStateOf<Intent?>(null)
    private var mpvEngine: MpvEngine? = null
    private var playerRepository: PlayerRepository? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingIntent = intent

        // Set orientation before setContent to prevent portrait flash
        if (intent?.action == Intent.ACTION_VIEW) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        // Immersive full-screen
        WindowCompat.setDecorFitsSystemWindows(window, false)

        addOnPictureInPictureModeChangedListener { info ->
            handlePipModeChange(info.isInPictureInPictureMode)
        }

        setContent {
            MaterialTheme(colorScheme = AmoledDarkColorScheme) {
                val navController = rememberNavController()

                // Engine lives for the entire activity lifetime
                val engine = remember { MpvEngine(applicationContext) }
                val repository = remember { PlayerRepository(engine) }
                mpvEngine = engine
                playerRepository = repository

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

    override fun onPause() {
        super.onPause()
        // Don't pause playback when transitioning into PiP mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) return
        // Fix 5: route through the repository so isPaused StateFlow stays consistent.
        playerRepository?.pause()
        // releaseForBackground() atomically clears isMpvRendering, detaches the surface, and
        // sets vo=null so the next resume always builds a fresh EGL context, even on devices
        // where surfaceDestroyed() never fires and the Surface object survives lock/background.
        mpvEngine?.surface?.releaseForBackground()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        handlePipModeChange(isInPictureInPictureMode)
    }

    private fun handlePipModeChange(isInPip: Boolean) {
        playerRepository?.setPipMode(isInPip)
        mpvEngine?.let { engine ->
            if (isInPip) {
                engine.executor.play()
                engine.surface.reattachSurface()
            } else {
                engine.surface.reattachSurface()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

