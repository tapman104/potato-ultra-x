package com.potato.player.engine

import `is`.xyz.mpv.MPVLib  // only for InitResult type re-export
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    private val _subScale               = MutableStateFlow(1.0)
    private val _subPos                 = MutableStateFlow(100)

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
    val subScale: StateFlow<Double>                    = _subScale.asStateFlow()
    val subPos: StateFlow<Int>                         = _subPos.asStateFlow()

    // Suppress MPV time-pos echo-backs while the slider is being dragged
    @Volatile private var isSliderSeeking  = false
    // Throttle time-pos updates to ~5 Hz; reset to 0 after seekCommit to snap immediately
    @Volatile private var lastTimePosUpdate = 0L

    private val repoScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main.immediate + kotlinx.coroutines.SupervisorJob())
    private var seekDebounceJob: kotlinx.coroutines.Job? = null
    @Volatile private var isSeekingGate = false
    @Volatile private var lastSeekTime = 0L
    @Volatile private var pendingSeekCommitSec: Double? = null
    @Volatile private var pendingSeekRelativeSec: Int = 0

    init { engine.dispatcher.addListener(this) }

    private fun flushPendingSeeks() {
        val now = System.currentTimeMillis()
        if (now - lastSeekTime < 80) {
            if (seekDebounceJob?.isActive != true) {
                seekDebounceJob = repoScope.launch {
                    delay(80 - (now - lastSeekTime) + 5)
                    flushPendingSeeks()
                }
            }
            return
        }
        val commitTarget = pendingSeekCommitSec
        val relOffset = pendingSeekRelativeSec
        pendingSeekCommitSec = null
        pendingSeekRelativeSec = 0
        if (commitTarget != null) {
            lastTimePosUpdate = 0L
            lastSeekTime = System.currentTimeMillis()
            isSeekingGate = true
            engine.executor.seekCommit(commitTarget)
        } else if (relOffset != 0) {
            lastSeekTime = System.currentTimeMillis()
            isSeekingGate = true
            engine.executor.seekExactRelative(relOffset)
        } else {
            isSeekingGate = false
        }
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    fun loadFile(path: String) { _isLoading.value = true; engine.executor.loadFile(path) }
    fun togglePlay() { engine.executor.togglePlay() }
    fun play()       { engine.executor.play() }
    fun pause()      { engine.executor.pause() }
    fun seekGesture(sec: Double) { engine.executor.seekGesture(sec) }

    fun seekCommit(sec: Double) {
        val now = System.currentTimeMillis()
        if (!isSeekingGate && now - lastSeekTime >= 80) {
            lastTimePosUpdate = 0L; lastSeekTime = now; isSeekingGate = true
            engine.executor.seekCommit(sec)
        } else {
            pendingSeekCommitSec = sec; pendingSeekRelativeSec = 0
            if (seekDebounceJob?.isActive != true) {
                seekDebounceJob = repoScope.launch { delay(80); flushPendingSeeks() }
            }
        }
    }

    fun seekRelative(offsetSec: Double) {
        val target = (_positionSec.value + offsetSec).coerceIn(0.0, _durationSec.value.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        seekCommit(target)
    }

    fun seekExactRelative(offsetSec: Int) {
        val now = System.currentTimeMillis()
        if (!isSeekingGate && now - lastSeekTime >= 80) {
            lastSeekTime = now; isSeekingGate = true
            engine.executor.seekExactRelative(offsetSec)
        } else {
            pendingSeekRelativeSec += offsetSec
            if (seekDebounceJob?.isActive != true) {
                seekDebounceJob = repoScope.launch { delay(80); flushPendingSeeks() }
            }
        }
    }

    fun startFastForward() {
        if (!_isFastForwarding.value) {
            normalPlaybackSpeed = _playbackSpeed.value; _isFastForwarding.value = true
            engine.executor.setPlaybackSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (_isFastForwarding.value) {
            _isFastForwarding.value = false; engine.executor.setPlaybackSpeed(normalPlaybackSpeed)
            _playbackSpeed.value = normalPlaybackSpeed
        }
    }

    fun setDecoder(hwdec: String) { engine.prepareDecoderSwitch { engine.executor.setDecoder(hwdec) } }

    fun setPlaybackSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_isFastForwarding.value) { _playbackSpeed.value = clamped; engine.executor.setPlaybackSpeed(clamped) }
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
                list.add(TrackInfo(id = id, type = type, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
            }
            _tracks.value = list
            _currentAudioTrackId.value = engine.executor.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
            _currentSubtitleTrackId.value = engine.executor.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
        }
    }

    fun setAudioTrack(id: Int) { engine.executor.setAudioTrack(id); _currentAudioTrackId.value = id }
    fun setSubtitleTrack(id: Int) { engine.executor.setSubtitleTrack(id); _currentSubtitleTrackId.value = id }
    fun addExternalSubtitle(path: String) { engine.executor.addExternalSubtitle(path) { loadTracks() } }
    fun setSubScale(scale: Double) { _subScale.value = scale; engine.executor.setSubScale(scale) }
    fun setSubPos(pos: Int) { _subPos.value = pos; engine.executor.setSubPos(pos) }
    fun onSliderDragStart() { isSliderSeeking = true }
    fun onSliderDragEnd()   { isSliderSeeking = false }
    fun stop()              { engine.executor.stop() }

    fun enterStandby() {
        engine.enterStandby()
        _fileLoaded.value = false; _isLoading.value = false; _isPaused.value = true
        _positionSec.value = 0.0; _durationSec.value = 0.0; _cachedSec.value = 0.0; _cacheDurationSec.value = 0.0
        _playbackSpeed.value = 1.0; normalPlaybackSpeed = 1.0; _hwdecCurrent.value = "HW+"
        _tracks.value = emptyList(); _currentAudioTrackId.value = -1; _currentSubtitleTrackId.value = -1
        _subScale.value = 1.0; _subPos.value = 100; _isFastForwarding.value = false
        isSliderSeeking = false; lastTimePosUpdate = 0L
    }

    fun cleanup() { repoScope.cancel(); engine.dispatcher.removeListener(this) }

    // ── MpvEventListener ─────────────────────────────────────────────────────

    override fun onFileLoaded() { _fileLoaded.value = true; _isLoading.value = false; loadTracks() }
    override fun onPlaybackStarted() { _isPaused.value = false; _isLoading.value = false; flushPendingSeeks() }
    override fun onSeek() { flushPendingSeeks() }
    override fun onPlaybackStopped(endReason: Int) {
        _isPaused.value = true
        if (_isFastForwarding.value) {
            engine.executor.setPlaybackSpeed(normalPlaybackSpeed)
        }
        _isFastForwarding.value = false
    }

    override fun onPropertyChange(name: String, value: Any?) {
        when (name) {
            MpvProp.PAUSE -> (value as? Boolean)?.let { _isPaused.value = it }
            MpvProp.TIME_POS -> {
                val sec = value as? Double ?: return
                if (isSliderSeeking) return
                val now = System.currentTimeMillis()
                if (now - lastTimePosUpdate >= 200) { _positionSec.value = sec; lastTimePosUpdate = now }
            }
            MpvProp.DURATION -> (value as? Double)?.let { _durationSec.value = it }
            MpvProp.DEMUXER_CACHE_TIME -> (value as? Double)?.let { _cachedSec.value = it }
            MpvProp.DEMUXER_CACHE_DURATION -> (value as? Double)?.let { _cacheDurationSec.value = it }
            MpvProp.SPEED -> (value as? Double)?.let { if (!_isFastForwarding.value) { _playbackSpeed.value = it } }
            MpvProp.HWDEC_CURRENT -> (value as? String)?.let {
                _hwdecCurrent.value = when {
                    it == "no" || it.isEmpty() -> "SW"
                    it.contains("copy") -> "HW+"
                    else -> "HW"
                }
            }
            MpvProp.SUB_SCALE -> ((value as? Number)?.toDouble() ?: (value as? String)?.toDoubleOrNull())?.let { _subScale.value = it }
            MpvProp.SUB_POS -> ((value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull())?.let { _subPos.value = it }
        }
    }

    override fun onError(message: String) { _isLoading.value = false }
}

