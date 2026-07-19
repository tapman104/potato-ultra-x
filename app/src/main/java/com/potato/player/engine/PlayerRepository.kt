package com.potato.player.engine

import `is`.xyz.mpv.MPVLib  // only for InitResult type re-export
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlayerRepository(val engine: MpvEngine) : MpvEventListener {

    val initResult: SharedFlow<InitResult> = engine.initResult

    private val _isPaused    = MutableStateFlow(true)
    private val _positionSec = MutableStateFlow(0.0)
    private val _durationSec = MutableStateFlow(0.0)
    private val _cachedSec   = MutableStateFlow(0.0)
    private val _cacheDurationSec = MutableStateFlow(0.0)
    private val _playbackSpeed    = MutableStateFlow(1.0)
    private val _fileLoaded  = MutableStateFlow(false)
    private val _isLoading   = MutableStateFlow(false)
    private val _hwdecCurrent = MutableStateFlow("HW+")
    private val _tracks              = MutableStateFlow<List<TrackInfo>>(emptyList())
    private val _currentAudioTrackId    = MutableStateFlow(-1)
    private val _currentSubtitleTrackId = MutableStateFlow(-1)

    private val _isFastForwarding = MutableStateFlow(false)
    val isFastForwarding: StateFlow<Boolean> = _isFastForwarding.asStateFlow()
    private var normalPlaybackSpeed = 1.0

    val isPaused:    StateFlow<Boolean> = _isPaused
    val positionSec: StateFlow<Double>  = _positionSec
    val durationSec: StateFlow<Double>  = _durationSec
    val cachedSec:   StateFlow<Double>  = _cachedSec
    val cacheDurationSec: StateFlow<Double> = _cacheDurationSec
    val playbackSpeed: StateFlow<Double>    = _playbackSpeed
    val fileLoaded:  StateFlow<Boolean> = _fileLoaded
    val isLoading:   StateFlow<Boolean> = _isLoading
    val hwdecCurrent: StateFlow<String> = _hwdecCurrent
    val tracks: StateFlow<List<TrackInfo>>             = _tracks.asStateFlow()
    val currentAudioTrackId: StateFlow<Int>            = _currentAudioTrackId.asStateFlow()
    val currentSubtitleTrackId: StateFlow<Int>         = _currentSubtitleTrackId.asStateFlow()

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

    fun seekRelative(offsetSec: Double) {
        val target = (_positionSec.value + offsetSec).coerceIn(
            0.0,
            _durationSec.value.takeIf { it > 0.0 } ?: Double.MAX_VALUE
        )
        seekCommit(target)
    }

    fun seekExactRelative(offsetSec: Int) {
        engine.executor.seekExactRelative(offsetSec)
    }

    fun startFastForward() {
        if (!_isFastForwarding.value) {
            normalPlaybackSpeed = _playbackSpeed.value
            _isFastForwarding.value = true
            engine.executor.setPlaybackSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (_isFastForwarding.value) {
            _isFastForwarding.value = false
            engine.executor.setPlaybackSpeed(normalPlaybackSpeed)
            _playbackSpeed.value = normalPlaybackSpeed
        }
    }

    fun setDecoder(hwdec: String) {
        engine.executor.setDecoder(hwdec)
    }

    fun setPlaybackSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_isFastForwarding.value) {
            _playbackSpeed.value = clamped
            engine.executor.setPlaybackSpeed(clamped)
        }
    }

    fun loadTracks() {
        engine.executor.execute {
            val count = engine.executor.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
            val list = mutableListOf<TrackInfo>()
            for (i in 0 until count) {
                val type = engine.executor.getPropertyString("track-list/$i/type") ?: continue
                if (type != "audio" && type != "sub") continue
                val id = engine.executor.getPropertyInt("track-list/$i/id") ?: continue
                val title = engine.executor.getPropertyString("track-list/$i/title")
                val lang = engine.executor.getPropertyString("track-list/$i/lang")
                val extStr = engine.executor.getPropertyString("track-list/$i/external")
                val isExternal = extStr == "yes" || extStr == "true"
                list.add(TrackInfo(id = id, type = type, title = title, lang = lang, isExternal = isExternal))
            }
            _tracks.value = list

            val aidStr = engine.executor.getPropertyString(MpvProp.AID)
            _currentAudioTrackId.value = aidStr?.toIntOrNull() ?: -1

            val sidStr = engine.executor.getPropertyString(MpvProp.SID)
            _currentSubtitleTrackId.value = sidStr?.toIntOrNull() ?: -1
        }
    }

    fun setAudioTrack(id: Int) {
        engine.executor.setAudioTrack(id)
        _currentAudioTrackId.value = id
    }

    fun setSubtitleTrack(id: Int) {
        engine.executor.setSubtitleTrack(id)
        _currentSubtitleTrackId.value = id
    }

    fun addExternalSubtitle(path: String) {
        engine.executor.addExternalSubtitle(path) {
            loadTracks()
        }
    }

    fun onSliderDragStart() { isSliderSeeking = true  }
    fun onSliderDragEnd()   { isSliderSeeking = false }

    fun stop()    { engine.executor.stop() }

    fun enterStandby() {
        engine.enterStandby()
        _fileLoaded.value = false
        _isLoading.value  = false
        _isPaused.value   = true
        _positionSec.value = 0.0
        _durationSec.value = 0.0
        _cachedSec.value   = 0.0
        _cacheDurationSec.value = 0.0
        _tracks.value      = emptyList()
        _currentAudioTrackId.value = -1
        _currentSubtitleTrackId.value = -1
        _isFastForwarding.value = false
        isSliderSeeking = false
    }

    fun cleanup() { engine.dispatcher.removeListener(this) }

    // ── MpvEventListener ─────────────────────────────────────────────────────

    override fun onFileLoaded() {
        _fileLoaded.value = true
        _isLoading.value  = false
        loadTracks()
    }

    override fun onPlaybackStarted() {
        _isPaused.value  = false
        _isLoading.value = false
    }

    override fun onPlaybackStopped(endReason: Int) {
        _isPaused.value = true
        _isFastForwarding.value = false
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
            MpvProp.DEMUXER_CACHE_DURATION -> {
                val sec = value as? Double ?: return
                _cacheDurationSec.value = sec
            }
            MpvProp.SPEED -> {
                val sec = value as? Double ?: return
                if (!_isFastForwarding.value && sec != 2.0) {
                    _playbackSpeed.value = sec
                }
            }
            MpvProp.HWDEC_CURRENT -> {
                val current = value as? String ?: return
                _hwdecCurrent.value = when {
                    current == "no" || current.isEmpty() -> "SW"
                    current.contains("copy") -> "HW+"
                    else -> "HW"
                }
            }
        }
    }

    override fun onError(message: String) { _isLoading.value = false }
}
