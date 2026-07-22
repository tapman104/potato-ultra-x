package com.potato.player.engine

import com.potato.player.data.AppDatabase
import com.potato.player.data.VideoHistory
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerRepository(val engine: MpvEngine) : MpvEventListener {

    val initResult: SharedFlow<Result<Unit>> = engine.initResult

    private val _isPaused = MutableStateFlow(true); val isPaused: StateFlow<Boolean> = _isPaused
    private val _positionSec = MutableStateFlow(0.0); val positionSec: StateFlow<Double> = _positionSec
    private val _durationSec = MutableStateFlow(0.0); val durationSec: StateFlow<Double> = _durationSec
    private val _cachedSec = MutableStateFlow(0.0); val cachedSec: StateFlow<Double> = _cachedSec
    private val _cacheDurationSec = MutableStateFlow(0.0); val cacheDurationSec: StateFlow<Double> = _cacheDurationSec
    private val _playbackSpeed = MutableStateFlow(1.0); val playbackSpeed: StateFlow<Double> = _playbackSpeed
    private val _fileLoaded = MutableStateFlow(false); val fileLoaded: StateFlow<Boolean> = _fileLoaded
    private val _isLoading = MutableStateFlow(false); val isLoading: StateFlow<Boolean> = _isLoading
    private val _hwdecCurrent = MutableStateFlow("HW+"); val hwdecCurrent: StateFlow<String> = _hwdecCurrent
    private val _tracks = MutableStateFlow<List<TrackInfo>>(emptyList()); val tracks: StateFlow<List<TrackInfo>> = _tracks.asStateFlow()
    private val _currentAudioTrackId = MutableStateFlow(-1); val currentAudioTrackId: StateFlow<Int> = _currentAudioTrackId.asStateFlow()
    private val _currentSubtitleTrackId = MutableStateFlow(-1); val currentSubtitleTrackId: StateFlow<Int> = _currentSubtitleTrackId.asStateFlow()
    private val _subScale = MutableStateFlow(1.0); val subScale: StateFlow<Double> = _subScale.asStateFlow()
    private val _subPos = MutableStateFlow(100); val subPos: StateFlow<Int> = _subPos.asStateFlow()
    private val _videoWidth = MutableStateFlow(0); val videoWidth: StateFlow<Int> = _videoWidth.asStateFlow()
    private val _videoHeight = MutableStateFlow(0); val videoHeight: StateFlow<Int> = _videoHeight.asStateFlow()
    private val _isInPipMode = MutableStateFlow(false); val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    private val _isFastForwarding = MutableStateFlow(false)
    val isFastForwarding: StateFlow<Boolean> = _isFastForwarding.asStateFlow()
    @Volatile private var normalPlaybackSpeed = 1.0

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

    private val database by lazy { AppDatabase.getInstance(engine.context) }
    private var currentUri = ""
    private var currentTitle = ""
    private var resumePositionSec = 0.0
    private var hasSoughtOnStart = false

    init { engine.dispatcher.addListener(this) }

    // ponytail: direct state check preserves instant fast-path on initial seek and integer offset accumulation without complex Flow boilerplate.
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

    fun loadFile(uri: String, title: String = "") {
        currentUri = uri
        currentTitle = title
        resumePositionSec = 0.0
        hasSoughtOnStart = false
        _isLoading.value = true
        _isPaused.value = false   // optimistically show Pause button immediately
        // Bug fix: run DB lookup on IO *before* calling loadFile so resumePositionSec
        // is guaranteed set before onPlaybackStarted fires (eliminates the race).
        repoScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val history = database.videoHistoryDao().getByUri(uri)
            if (history != null && history.lastPlayedPositionSec > 0) {
                resumePositionSec = history.lastPlayedPositionSec
            }
            engine.executor.loadFile(uri)
        }
    }
    fun togglePlay() {
        _isPaused.value = !_isPaused.value
        engine.executor.togglePlay()
    }
    fun play()       { engine.executor.play() }
    fun pause()      { engine.executor.pause() }
    fun setPipMode(isInPip: Boolean) { _isInPipMode.value = isInPip }
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

    fun setDecoder(hwdec: String) {
        engine.prepareDecoderSwitch {
            engine.executor.setDecoder(hwdec)
            _hwdecCurrent.value = when (hwdec) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
        }
    }

    fun setPlaybackSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_isFastForwarding.value) { _playbackSpeed.value = clamped; engine.executor.setPlaybackSpeed(clamped) }
    }

    fun loadTracks() {
        engine.executor.execute {
            _videoWidth.value = engine.executor.getPropertyInt(MpvProp.VIDEO_PARAMS_W) ?: 0
            _videoHeight.value = engine.executor.getPropertyInt(MpvProp.VIDEO_PARAMS_H) ?: 0
            val count = engine.executor.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
            val list = mutableListOf<TrackInfo>()
            for (i in 0 until count) {
                val trackType = when (engine.executor.getPropertyString("track-list/$i/type")) {
                    "audio" -> TrackType.AUDIO
                    "sub"   -> TrackType.SUBTITLE
                    else    -> continue
                }
                val id = engine.executor.getPropertyInt("track-list/$i/id") ?: continue
                val title = engine.executor.getPropertyString("track-list/$i/title")
                val lang = engine.executor.getPropertyString("track-list/$i/lang")
                val extStr = engine.executor.getPropertyString("track-list/$i/external")
                list.add(TrackInfo(id = id, type = trackType, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
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

    private fun saveHistoryIfNeeded() {
        if (currentUri.isEmpty() || _durationSec.value <= 0.0) return
        val entry = VideoHistory(
            uri = currentUri,
            title = currentTitle.ifEmpty { currentUri.substringAfterLast('/') },
            lastPlayedPositionSec = _positionSec.value,
            durationSec = _durationSec.value,
            lastAudioTrackId = _currentAudioTrackId.value,
            lastSubtitleTrackId = _currentSubtitleTrackId.value,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        repoScope.launch(kotlinx.coroutines.Dispatchers.IO) { database.videoHistoryDao().upsert(entry) }
    }

    fun enterStandby() {
        saveHistoryIfNeeded()
        engine.enterStandby()
        _fileLoaded.value = false; _isLoading.value = false; _isPaused.value = true
        _positionSec.value = 0.0; _durationSec.value = 0.0; _cachedSec.value = 0.0; _cacheDurationSec.value = 0.0
        _playbackSpeed.value = 1.0; normalPlaybackSpeed = 1.0
        // Bug fix: do NOT reset _hwdecCurrent here — it will update naturally from the
        // MPV property event on next load. Resetting caused a stale "HW+" badge flash.
        _tracks.value = emptyList(); _currentAudioTrackId.value = -1; _currentSubtitleTrackId.value = -1
        _subScale.value = 1.0; _subPos.value = 100; _isFastForwarding.value = false
        _videoWidth.value = 0; _videoHeight.value = 0; _isInPipMode.value = false
        isSliderSeeking = false; lastTimePosUpdate = 0L
        currentUri = ""
        currentTitle = ""
        resumePositionSec = 0.0
        hasSoughtOnStart = false
    }

    fun cleanup() { repoScope.cancel(); engine.dispatcher.removeListener(this) }

    // ── MpvEventListener ─────────────────────────────────────────────────────

    override fun onFileLoaded() { _fileLoaded.value = true; _isLoading.value = false; loadTracks() }
    override fun onPlaybackStarted() {
        _isLoading.value = false
        _isPaused.value = false
        engine.surface.markRendering()
        engine.executor.play()
        // Both ops run sequentially on Main: flush first so no pending seek can overwrite
        // the resume position that fires immediately after.
        repoScope.launch {
            flushPendingSeeks()
            if (resumePositionSec > 0.0 && !hasSoughtOnStart) {
                hasSoughtOnStart = true
                engine.executor.seekCommit(resumePositionSec)
            }
        }
    }
    override fun onSeek() { repoScope.launch { flushPendingSeeks() } }
    override fun onPlaybackStopped(endReason: Int) {
        _isPaused.value = true
        if (_isFastForwarding.value) {
            engine.executor.setPlaybackSpeed(normalPlaybackSpeed)
        }
        _isFastForwarding.value = false

        saveHistoryIfNeeded()
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
            MpvProp.VIDEO_PARAMS_W -> ((value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull())?.let { _videoWidth.value = it }
            MpvProp.VIDEO_PARAMS_H -> ((value as? Number)?.toInt() ?: (value as? String)?.toIntOrNull())?.let { _videoHeight.value = it }
        }
    }

    override fun onError(message: String) { _isLoading.value = false }
}

