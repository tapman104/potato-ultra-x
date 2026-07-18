package com.tapman104.mpvplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.tapman104.mpvplayer.player.PlayerActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Entry point — launch PlayerActivity for now
        startActivity(Intent(this, PlayerActivity::class.java))
        finish()
    }
}
