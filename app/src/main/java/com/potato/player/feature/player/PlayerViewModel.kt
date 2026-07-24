package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.AppDatabase
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.data.VideoHistory
import com.potato.player.engine.MpvWrapper
import com.potato.player.engine.MpvEvent
import com.potato.player.engine.MpvEventId
import com.potato.player.engine.MpvProp
import com.potato.player.engine.TrackInfo
import com.potato.player.engine.TrackType
import com.potato.player.feature.player.PlayerUiConstants
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.SurfaceHolder

class PlayerViewModel(private val wrapper: MpvWrapper) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(wrapper.context) }
    private val database by lazy { AppDatabase.getInstance(wrapper.context) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var currentUri = ""
    private var currentTitle = ""
    
    private var normalPlaybackSpeed = 1.0
    private var isSliderSeeking = false
    @Volatile private var pendingResumePosition: Long = 0L

    init {
        viewModelScope.launch {
            wrapper.events.collect { event ->
                when (event) {
                    is MpvEvent.Id -> handleMpvEventId(event.id)
                    is MpvEvent.PropertyBool -> handlePropertyBool(event.name, event.value)
                    is MpvEvent.PropertyDouble -> handlePropertyDouble(event.name, event.value)
                    is MpvEvent.PropertyLong -> handlePropertyLong(event.name, event.value)
                    is MpvEvent.PropertyString -> handlePropertyString(event.name, event.value)
                }
            }
        }

        viewModelScope.launch {
            combine(
                prefsRepository.subScaleFlow,
                prefsRepository.subPosFlow,
                prefsRepository.autoRotationFlow
            ) { scale, pos, autoRot ->
                Triple(scale, pos, autoRot)
            }.collect { (scale, pos, autoRot) ->
                wrapper.setSubScale(scale)
                wrapper.setSubPos(pos)
                _uiState.update { it.copy(subScale = scale, subPos = pos, isAutoRotation = autoRot) }
            }
        }
    }

    private fun handleMpvEventId(id: Int) {
        when (id) {
            MpvEventId.FILE_LOADED -> {
                _uiState.update { it.copy(fileLoaded = true, isLoading = false) }
                loadTracks()
                if (pendingResumePosition > 0L) {
                    wrapper.seekTo(pendingResumePosition)
                    pendingResumePosition = 0L
                }
            }
            MpvEventId.PLAYBACK_RESTART -> {
                _uiState.update { it.copy(isLoading = false, isPlaying = true) }
            }
            MpvEventId.END_FILE -> {
                _uiState.update { it.copy(isPlaying = false) }
                saveHistoryIfNeeded()
                if (_uiState.value.isFastForwarding) {
                    _uiState.update { it.copy(isFastForwarding = false) }
                    wrapper.setSpeed(normalPlaybackSpeed)
                }
            }
        }
    }

    private fun handlePropertyBool(name: String, value: Boolean) {
        if (name == MpvProp.PAUSE) {
            _uiState.update { it.copy(isPlaying = !value) }
        }
    }

    private fun handlePropertyDouble(name: String, value: Double) {
        when (name) {
            MpvProp.TIME_POS -> {
                if (!isSliderSeeking) _progressState.update { it.copy(positionSec = value) }
            }
            MpvProp.DURATION -> _progressState.update { it.copy(durationSec = value) }
            MpvProp.DEMUXER_CACHE_TIME -> _progressState.update { it.copy(cachedSec = value) }
            MpvProp.DEMUXER_CACHE_DURATION -> _progressState.update { it.copy(cacheDurationSec = value) }
            MpvProp.SPEED -> {
                if (!_uiState.value.isFastForwarding) {
                    _uiState.update { it.copy(playbackSpeed = value) }
                    normalPlaybackSpeed = value
                }
            }
            MpvProp.SUB_SCALE -> _uiState.update { it.copy(subScale = value) }
        }
    }

    private fun handlePropertyLong(name: String, value: Long) {
        when (name) {
            MpvProp.SUB_POS -> _uiState.update { it.copy(subPos = value.toInt()) }
            MpvProp.VIDEO_PARAMS_W -> _uiState.update { it.copy(videoWidth = value.toInt()) }
            MpvProp.VIDEO_PARAMS_H -> _uiState.update { it.copy(videoHeight = value.toInt()) }
        }
    }

    private fun handlePropertyString(name: String, value: String) {
        when (name) {
            MpvProp.HWDEC_CURRENT -> {
                val hwdec = when {
                    value == "no" || value.isEmpty() -> "SW"
                    value.contains("copy") -> "HW+"
                    else -> "HW"
                }
                _uiState.update { it.copy(hwdecCurrent = hwdec) }
            }
            "track-list" -> {
                val tracks = parseTrackList(value)
                if (tracks.isNotEmpty()) {
                    _uiState.update { it.copy(tracks = tracks) }
                } else {
                    loadTracks()
                }
            }
        }
    }

    private fun parseTrackList(raw: String): List<TrackInfo> {
        return try {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val obj = arr.getJSONObject(i)
                val typeStr = obj.optString("type", "")
                val type = when (typeStr) {
                    "audio" -> TrackType.AUDIO
                    "sub" -> TrackType.SUBTITLE
                    else -> return@mapNotNull null
                }
                TrackInfo(
                    id = obj.getInt("id"),
                    type = type,
                    title = obj.optString("title", "").takeIf { it.isNotBlank() },
                    lang = obj.optString("lang", "").takeIf { it.isNotBlank() },
                    isExternal = obj.optBoolean("external", false)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun loadTracks() {
        val count = wrapper.getPropertyInt(MpvProp.TRACK_LIST_COUNT) ?: 0
        val list = mutableListOf<TrackInfo>()
        for (i in 0 until count) {
            val trackType = when (wrapper.getPropertyString("track-list/$i/type")) {
                "audio" -> TrackType.AUDIO
                "sub"   -> TrackType.SUBTITLE
                else    -> continue
            }
            val id = wrapper.getPropertyInt("track-list/$i/id") ?: continue
            val title = wrapper.getPropertyString("track-list/$i/title")
            val lang = wrapper.getPropertyString("track-list/$i/lang")
            val extStr = wrapper.getPropertyString("track-list/$i/external")
            list.add(TrackInfo(id = id, type = trackType, title = title, lang = lang, isExternal = extStr == "yes" || extStr == "true"))
        }
        val aid = wrapper.getPropertyString(MpvProp.AID)?.toIntOrNull() ?: -1
        val sid = wrapper.getPropertyString(MpvProp.SID)?.toIntOrNull() ?: -1
        _uiState.update { it.copy(tracks = list, currentAudioTrackId = aid, currentSubtitleTrackId = sid) }
    }

    val surfaceCallback: SurfaceHolder.Callback get() = wrapper.surfaceCallback

    fun onSurfaceReady(uri: String, title: String = "") {
        if (currentUri != uri) {
            loadFile(uri, title)
        }
    }

    fun onSurfaceReattached() {
        if (!_uiState.value.fileLoaded) return
        if (_uiState.value.isPlaying) {
            wrapper.resume()
        }
    }

    fun setSurfaceReadyCallback(cb: (() -> Unit)?) {
        wrapper.onSurfaceReady = cb
    }

    fun onSurfaceDestroyed() {
        wrapper.onSurfaceReady = null
    }

    fun loadFile(uri: String, title: String = "") {
        currentUri = uri
        currentTitle = title
        _uiState.update { it.copy(isLoading = true, isPlaying = false, fileLoaded = false, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val history = database.videoHistoryDao().getByUri(uri)
            pendingResumePosition = if (history != null && history.lastPlayedPositionSec > 0)
                (history.lastPlayedPositionSec * 1000).toLong() else 0L
            
            withContext(Dispatchers.Main) {
                wrapper.play(uri)
            }
        }
    }

    fun togglePlay() {
        wrapper.togglePlay()
    }
    
    fun pause() {
        wrapper.pause()
    }

    fun cycleOrientationMode() {
        val next = when (_uiState.value.orientationMode) {
            OrientationMode.AUTO -> OrientationMode.LOCK_LANDSCAPE
            OrientationMode.LOCK_LANDSCAPE -> OrientationMode.LOCK_PORTRAIT
            OrientationMode.LOCK_PORTRAIT -> OrientationMode.AUTO
        }
        _uiState.update { it.copy(orientationMode = next) }
    }

    fun toggleAutoRotation() {
        val next = !_uiState.value.isAutoRotation
        _uiState.update { it.copy(isAutoRotation = next) }
        viewModelScope.launch { prefsRepository.setAutoRotation(next) }
    }

    fun setDecoder(mode: String) {
        val hwdec = when (mode) { "no" -> "SW"; "mediacodec" -> "HW"; else -> "HW+" }
        _uiState.update { it.copy(hwdecCurrent = hwdec) }
        wrapper.setDecoder(mode)
    }

    fun seekRelative(offsetSec: Double) {
        val target = (_progressState.value.positionSec + offsetSec).coerceIn(0.0, _progressState.value.durationSec.takeIf { it > 0.0 } ?: Double.MAX_VALUE)
        wrapper.seekTo((target * 1000).toLong())
    }

    fun seekExactRelative(offsetSec: Int) {
        wrapper.seekRelative(offsetSec.toDouble())
    }

    fun startFastForward() {
        if (!_uiState.value.isFastForwarding) {
            normalPlaybackSpeed = _uiState.value.playbackSpeed
            _uiState.update { it.copy(isFastForwarding = true) }
            wrapper.setSpeed(2.0)
        }
    }

    fun stopFastForward() {
        if (_uiState.value.isFastForwarding) {
            _uiState.update { it.copy(isFastForwarding = false) }
            wrapper.setSpeed(normalPlaybackSpeed)
            _uiState.update { it.copy(playbackSpeed = normalPlaybackSpeed) }
        }
    }

    fun onSliderDragStart(posSec: Double) {
        isSliderSeeking = true
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        _progressState.update { it.copy(dragPositionSec = posSec) }
        // The old code used seekGesture which just stored a value, but since MPV has its own thread and queue
        // we could just use absolute+exact if we want to seek during drag. Since we deleted the debouncer,
        // it's better not to spam it, but actually `seekTo` should be fine. However, old `seekGesture`
        // was non-blocking. MPV natively drops rapidly queued seeks anyway. Let's do `seekTo`.
        wrapper.seekTo((posSec * 1000).toLong())
    }

    fun onSliderDragEnd(posSec: Double) {
        isSliderSeeking = false
        wrapper.seekTo((posSec * 1000).toLong())
        _progressState.update { it.copy(dragPositionSec = null) }
    }

    fun onMoreMenuToggle() {
        _uiState.update {
            it.copy(activeSheet = if (it.activeSheet == ActiveSheet.MORE_MENU) ActiveSheet.NONE else ActiveSheet.MORE_MENU)
        }
    }

    fun onMoreMenuDismiss() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun onShowAudioDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.AUDIO) }
    }

    fun onDismissAudioDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun onShowSubtitleDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.SUBTITLE) }
    }

    fun onDismissSubtitleDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun onShowSpeedDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.SPEED) }
    }

    fun onDismissSpeedDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun onShowDecoderDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.DECODER) }
    }

    fun onDismissDecoderDialog() {
        _uiState.update { it.copy(activeSheet = ActiveSheet.NONE) }
    }

    fun setPlaybackSpeed(speed: Double) {
        val clamped = speed.coerceIn(0.25, 4.0)
        normalPlaybackSpeed = clamped
        if (!_uiState.value.isFastForwarding) {
            _uiState.update { it.copy(playbackSpeed = clamped) }
            wrapper.setSpeed(clamped)
        }
    }

    fun onSelectAudioTrack(id: Int) {
        wrapper.setAudioTrack(id)
        _uiState.update { it.copy(currentAudioTrackId = id) }
        onDismissAudioDialog()
    }

    fun onSelectSubtitleTrack(id: Int) {
        wrapper.setSubTrack(id)
        _uiState.update { it.copy(currentSubtitleTrackId = id) }
        onDismissSubtitleDialog()
    }

    fun onLoadExternalSubtitle(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            wrapper.addExternalSubtitle(path)
            // Reload tracks to reflect new subtitle
            loadTracks()
        }
        onDismissSubtitleDialog()
    }

    fun setSubScale(scale: Double) { wrapper.setSubScale(scale) }
    fun setSubPos(pos: Int) { wrapper.setSubPos(pos) }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        wrapper.setSubScale(scale)
        wrapper.setSubPos(pos)
        viewModelScope.launch {
            prefsRepository.setSubScale(scale)
            prefsRepository.setSubPos(pos)
        }
    }

    fun resetSubtitleAppearance() {
        wrapper.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
        wrapper.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        viewModelScope.launch {
            prefsRepository.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
            prefsRepository.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        }
    }

    private fun saveHistoryIfNeeded() {
        if (currentUri.isEmpty() || _progressState.value.durationSec <= 0.0) return
        val entry = VideoHistory(
            uri = currentUri,
            title = currentTitle.ifEmpty { currentUri.substringAfterLast('/') },
            lastPlayedPositionSec = _progressState.value.positionSec,
            durationSec = _progressState.value.durationSec,
            lastAudioTrackId = _uiState.value.currentAudioTrackId,
            lastSubtitleTrackId = _uiState.value.currentSubtitleTrackId,
            lastPlayedTimestamp = System.currentTimeMillis()
        )
        viewModelScope.launch(Dispatchers.IO) { database.videoHistoryDao().upsert(entry) }
    }

    override fun onCleared() {
        super.onCleared()
        saveHistoryIfNeeded()
        wrapper.destroy()
    }
}
