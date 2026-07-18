package com.tapman104.mpvplayer.player.core

import android.content.Context
import android.view.Surface
import `is`.xyz.mpv.MPVLib
import com.tapman104.mpvplayer.player.engine.MpvCommandExecutor
import com.tapman104.mpvplayer.player.engine.MpvEventDispatcher
import com.tapman104.mpvplayer.player.engine.MpvEvent
import com.tapman104.mpvplayer.player.engine.MpvPropertyObserver
import com.tapman104.mpvplayer.player.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MpvController(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val executor = MpvCommandExecutor()
    val eventDispatcher = MpvEventDispatcher()
    val propertyObserver = MpvPropertyObserver()
    private val stateManager = PlaybackStateManager()

    val state: StateFlow<PlayerState> = stateManager.state
    val positionMs: StateFlow<Long> = propertyObserver.positionMs
    val durationMs: StateFlow<Long> = propertyObserver.durationMs
    val paused: StateFlow<Boolean> = propertyObserver.paused

    init {
        MPVLib.create(appContext)
        MPVLib.init()
        MPVLib.addObserver(eventDispatcher)
        MPVLib.addObserver(propertyObserver)
        propertyObserver.registerObservers()
        observeEvents()
    }

    fun attach(surface: Surface) { MPVLib.attachSurface(surface) }
    fun detach() { MPVLib.detachSurface() }

    fun loadFile(path: String) {
        stateManager.transitionTo(PlayerState.Loading)
        executor.loadFile(path)
    }
    fun togglePlay() {
        if (paused.value) executor.play() else executor.pause()
    }
    fun seekTo(ms: Long) { executor.seekTo(ms) }

    private fun observeEvents() {
        scope.launch {
            eventDispatcher.events.collect { event ->
                when (event) {
                    is MpvEvent.PlaybackRestart -> {
                        val pos = positionMs.value
                        val dur = durationMs.value
                        stateManager.transitionTo(
                            if (paused.value) PlayerState.Paused(pos, dur)
                            else PlayerState.Playing(pos, dur)
                        )
                    }
                    is MpvEvent.EndFile -> stateManager.transitionTo(PlayerState.Idle)
                    else -> Unit
                }
            }
        }
    }

    fun destroy() {
        scope.cancel()
        MPVLib.removeObserver(eventDispatcher)
        MPVLib.removeObserver(propertyObserver)
        MPVLib.destroy()
    }
}
