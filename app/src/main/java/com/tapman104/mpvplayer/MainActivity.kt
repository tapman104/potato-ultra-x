package com.tapman104.mpvplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import com.tapman104.mpvplayer.home.ui.HomeScreen
import com.tapman104.mpvplayer.player.PlayerActivity

class MainActivity : ComponentActivity() {

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            HomeScreen(
                onOpenFile = { pickVideo.launch("video/*") },
                onSettingsClick = { /* wire up later */ }
            )
        }
    }
}
