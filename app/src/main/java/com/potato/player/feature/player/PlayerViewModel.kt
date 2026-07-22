package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.PlayerRepository
import com.potato.player.engine.TrackInfo
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PlayerViewModel(private val repository: PlayerRepository) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(repository.engine.context) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progressState = MutableStateFlow(PlaybackProgressState())
    val progressState: StateFlow<PlaybackProgressState> = _progressState.asStateFlow()

    private var currentUri = ""
    private var currentTitle = ""

    init {
        // [A1] combine high-frequency progress flows into progressState
        viewModelScope.launch {
            combine(
                repository.positionSec,
                repository.durationSec,
                repository.cachedSec,
                repository.cacheDurationSec
            ) { pos, dur, cached, cacheDur ->
                _progressState.update {
                    it.copy(
                        positionSec = pos,
                        durationSec = dur,
                        cachedSec = cached,
                        cacheDurationSec = cacheDur
                    )
                }
            }.collect {}
        }

        // [A2] group tracks and track selections
        viewModelScope.launch {
            combine(repository.tracks, repository.currentAudioTrackId, repository.currentSubtitleTrackId) { tracks, audioId, subId ->
                Triple(tracks, audioId, subId)
            }.collect { (tracks, audioId, subId) ->
                _uiState.update { it.copy(tracks = tracks, currentAudioTrackId = audioId, currentSubtitleTrackId = subId) }
            }
        }

        // [A2] group playback control and speed states
        viewModelScope.launch {
            combine(repository.isPaused, repository.playbackSpeed, repository.isFastForwarding) { paused, speed, ff ->
                Triple(paused, speed, ff)
            }.collect { (paused, speed, ff) ->
                _uiState.update { it.copy(isPlaying = !paused, playbackSpeed = speed, isFastForwarding = ff) }
            }
        }

        // [A2] group file loading states
        viewModelScope.launch {
            combine(repository.fileLoaded, repository.isLoading) { loaded, loading ->
                Pair(loaded, loading)
            }.collect { (loaded, loading) ->
                _uiState.update { it.copy(fileLoaded = loaded, isLoading = loading) }
            }
        }

        // [A2] group subtitle appearance states
        viewModelScope.launch {
            combine(repository.subScale, repository.subPos) { scale, pos ->
                Pair(scale, pos)
            }.collect { (scale, pos) ->
                _uiState.update { it.copy(subScale = scale, subPos = pos) }
            }
        }

        // [A2] group video parameters and display properties
        viewModelScope.launch {
            combine(
                repository.videoWidth,
                repository.videoHeight,
                repository.isInPipMode,
                repository.hwdecCurrent
            ) { w, h, pip, hwdec ->
                _uiState.update { it.copy(videoWidth = w, videoHeight = h, isInPipMode = pip, hwdecCurrent = hwdec) }
            }.collect {}
        }

        // [A2] combine preference syncs
        viewModelScope.launch {
            combine(prefsRepository.subScaleFlow, prefsRepository.subPosFlow) { scale, pos ->
                repository.setSubScale(scale)
                repository.setSubPos(pos)
            }.collect {}
        }

        viewModelScope.launch {
            prefsRepository.autoRotationFlow.collect { v -> _uiState.update { it.copy(isAutoRotation = v) } }
        }

        // ponytail: initResult has unique error/lifecycle side effects so kept separate
        viewModelScope.launch {
            repository.initResult.collect { result ->
                if (result.isSuccess) {
                    _uiState.update { it.copy(error = null) }
                } else {
                    val msg = result.exceptionOrNull()?.message ?: "Unknown error"
                    _uiState.update { it.copy(error = msg, isLoading = false) }
                }
            }
        }
    }

    fun createSurfaceView(context: Context): View = SurfaceView(context).also { sv ->
        sv.keepScreenOn = true
        sv.holder.addCallback(repository.engine.surface)
    }

    fun onSurfaceReady(uri: String = currentUri, title: String = currentTitle) {
        if (uri.isNotEmpty()) {
            currentUri = uri
            currentTitle = title
        }
        repository.engine.surface.setSurfaceReadyCallback {
            viewModelScope.launch { repository.loadFile(currentUri, currentTitle) }
        }
        repository.engine.surface.setSurfaceReattachedCallback {
            resumeAfterSurfaceReattach()
        }
        if (repository.engine.surface.hasAttachedSurface() && currentUri.isNotEmpty()) {
            viewModelScope.launch { repository.loadFile(currentUri, currentTitle) }
        }
    }

    fun onSurfaceReattached() {
        resumeAfterSurfaceReattach()
    }

    fun onSurfaceDestroyed() {
        repository.engine.surface.setSurfaceReadyCallback(null)
        repository.engine.surface.setSurfaceReattachedCallback(null)
    }

    fun loadFile(uri: String, title: String = "") {
        currentUri = uri
        currentTitle = title
        repository.loadFile(uri, title)
    }

    /**
     * Called when the SurfaceView is re-created after the app returns from Recents.
     * Reattaches the MPV surface without restarting the file, then resumes playback
     * only if the player was already playing before backgrounding.
     */
    fun resumeAfterSurfaceReattach() {
        if (!_uiState.value.fileLoaded) return
        repository.engine.surface.reattachSurface()
        if (_uiState.value.isPlaying) {
            repository.play()
        }
    }

    fun togglePlay() {
        _uiState.update { it.copy(isPlaying = !it.isPlaying) }
        repository.togglePlay()
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
        repository.setDecoder(mode)
    }

    fun seekRelative(offsetSec: Double) {
        repository.seekRelative(offsetSec)
    }

    fun seekExactRelative(offsetSec: Int) {
        repository.seekExactRelative(offsetSec)
    }

    fun startFastForward() {
        repository.startFastForward()
    }

    fun stopFastForward() {
        repository.stopFastForward()
    }

    fun onSliderDragStart(posSec: Double) {
        repository.onSliderDragStart()
        _progressState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        _progressState.update { it.copy(dragPositionSec = posSec) }
        repository.seekGesture(posSec)
    }

    fun onSliderDragEnd(posSec: Double) {
        repository.seekCommit(posSec)
        repository.onSliderDragEnd()
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
        repository.setPlaybackSpeed(speed)
    }

    fun onSelectAudioTrack(id: Int) {
        repository.setAudioTrack(id)
        _uiState.update { it.copy(currentAudioTrackId = id) }
        onDismissAudioDialog()
    }

    fun onSelectSubtitleTrack(id: Int) {
        repository.setSubtitleTrack(id)
        _uiState.update { it.copy(currentSubtitleTrackId = id) }
        onDismissSubtitleDialog()
    }

    fun onLoadExternalSubtitle(uri: Uri, context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val path = MediaMetadataRepository.resolveSubtitlePath(context, uri) ?: uri.toString()
            repository.addExternalSubtitle(path)
        }
        onDismissSubtitleDialog()
    }

    fun setSubScale(scale: Double) { repository.setSubScale(scale) }
    fun setSubPos(pos: Int) { repository.setSubPos(pos) }

    fun setSubtitleAppearance(scale: Double, pos: Int) {
        repository.setSubScale(scale)
        repository.setSubPos(pos)
        viewModelScope.launch {
            prefsRepository.setSubScale(scale)
            prefsRepository.setSubPos(pos)
        }
    }

    fun resetSubtitleAppearance() {
        repository.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
        repository.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        viewModelScope.launch {
            prefsRepository.setSubScale(PlayerUiConstants.DEFAULT_SUBTITLE_SCALE)
            prefsRepository.setSubPos(PlayerUiConstants.DEFAULT_SUBTITLE_POSITION)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.enterStandby()
        repository.cleanup()
    }
}
