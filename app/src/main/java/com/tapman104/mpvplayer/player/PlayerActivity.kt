package com.tapman104.mpvplayer.player

import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tapman104.mpvplayer.player.ui.PlayerScreen
import com.tapman104.mpvplayer.player.viewmodel.PlayerViewModel

class PlayerActivity : ComponentActivity() {
    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val uri: Uri? = intent.data
        val fileName = uri?.lastPathSegment ?: "Unknown"
        val path = uri?.toString() ?: ""

        if (savedInstanceState == null && path.isNotEmpty()) {
            viewModel.loadFile(path)
        }

        setContent {
            PlayerScreen(
                viewModel = viewModel,
                fileName = fileName,
                onBack = { finish() }
            )
        }
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
