package com.tapman104.mpvplayer.player.engine

import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class MpvCommandExecutor {
    private val mpvDispatcher: CoroutineDispatcher =
        Executors.newSingleThreadExecutor { r -> Thread(r, "mpv-thread") }
            .asCoroutineDispatcher()
    private val scope = CoroutineScope(mpvDispatcher)

    fun loadFile(path: String) = dispatch { MPVLib.command("loadfile", path) }
    fun play() = dispatch { MPVLib.setPropertyBoolean("pause", false) }
    fun pause() = dispatch { MPVLib.setPropertyBoolean("pause", true) }
    fun seekTo(positionMs: Long) = dispatch {
        MPVLib.command("seek", (positionMs / 1000.0).toString(), "absolute")
    }
    fun setProperty(name: String, value: String) = dispatch { MPVLib.setPropertyString(name, value) }
    fun command(vararg args: String) = dispatch { MPVLib.command(*args) }

    private fun dispatch(block: () -> Unit) { scope.launch { block() } }
}
