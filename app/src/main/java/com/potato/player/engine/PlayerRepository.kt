package com.potato.player.engine

import `is`.xyz.mpv.MPVLib  // only for InitResult type re-export
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class PlayerRepository(val engine: MpvEngine) : MpvEventListener {

    val initResult: SharedFlow<InitResult> = engine.initResult

    private val _isPaused    = MutableStateFlow(true)
    private val _positionSec = MutableStateFlow(0.0)
    private val _durationSec = MutableStateFlow(0.0)
    private val _cachedSec   = MutableStateFlow(0.0)
    private val _fileLoaded  = MutableStateFlow(false)
    private val _isLoading   = MutableStateFlow(false)

    val isPaused:    StateFlow<Boolean> = _isPaused
    val positionSec: StateFlow<Double>  = _positionSec
    val durationSec: StateFlow<Double>  = _durationSec
    val cachedSec:   StateFlow<Double>  = _cachedSec
    val fileLoaded:  StateFlow<Boolean> = _fileLoaded
    val isLoading:   StateFlow<Boolean> = _isLoading

    // Suppress MPV time-pos echo-backs while the slider is being dragged
    @Volatile private var isSliderSeeking  = false
    // Throttle time-pos updates to ~5 Hz; reset to 0 after seekCommit to snap immediately
    @Volatile private var lastTimePosUpdate = 0L

    init { engine.dispatcher.addListener(this) }

    // ── Actions ──────────────────────────────────────────────────────────────

    fun loadFile(path: String) {
        _isLoading.value = true
        engine.executor.loadFile(path)
    }

    fun togglePlay() { engine.executor.togglePlay() }
    fun play()       { engine.executor.play() }
    fun pause()      { engine.executor.pause() }

    fun seekGesture(sec: Double) { engine.executor.seekGesture(sec) }

    fun seekCommit(sec: Double) {
        lastTimePosUpdate = 0L          // always accept the next time-pos after commit
        engine.executor.seekCommit(sec)
    }

    fun onSliderDragStart() { isSliderSeeking = true  }
    fun onSliderDragEnd()   { isSliderSeeking = false }

    fun stop()    { engine.executor.stop() }
    fun cleanup() { engine.dispatcher.removeListener(this) }

    // ── MpvEventListener ─────────────────────────────────────────────────────

    override fun onFileLoaded() {
        _fileLoaded.value = true
        _isLoading.value  = false
    }

    override fun onPlaybackStarted() {
        _isPaused.value  = false
        _isLoading.value = false
    }

    override fun onPlaybackStopped(endReason: Int) {
        _isPaused.value = true
    }

    override fun onPropertyChange(name: String, value: Any?) {
        when (name) {
            MpvProp.PAUSE -> {
                val paused = value as? Boolean ?: return
                _isPaused.value = paused
            }
            MpvProp.TIME_POS -> {
                val sec = value as? Double ?: return
                if (isSliderSeeking) return                     // suppress during drag
                val now = System.currentTimeMillis()
                if (now - lastTimePosUpdate >= 200) {           // ~5 Hz throttle
                    _positionSec.value = sec
                    lastTimePosUpdate = now
                }
            }
            MpvProp.DURATION -> {
                val sec = value as? Double ?: return
                _durationSec.value = sec
            }
            MpvProp.DEMUXER_CACHE_TIME -> {
                val sec = value as? Double ?: return
                _cachedSec.value = sec
            }
        }
    }

    override fun onError(message: String) { _isLoading.value = false }
}
