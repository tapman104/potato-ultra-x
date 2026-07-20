package com.potato.player.feature.player

import android.content.Context
import android.net.Uri
import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.data.UserPreferencesRepository
import com.potato.player.engine.MpvSurface
import com.potato.player.engine.PlayerRepository
import com.potato.player.engine.TrackInfo
import com.potato.player.util.MediaMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActiveSheet { NONE, MORE_MENU, SPEED, AUDIO, SUBTITLE, DECODER }

enum class OrientationMode(val orientation: Int, val label: String) {
    AUTO(ActivityInfo.SCREEN_ORIENTATION_SENSOR, "AUTO"),
    LOCK_LANDSCAPE(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, "LAND"),
    LOCK_PORTRAIT(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT, "PORT")
}

data class PlayerUiState(
    val isPlaying:       Boolean = false,
    val positionSec:     Double  = 0.0,
    val durationSec:     Double  = 0.0,
    val cachedSec:       Double  = 0.0,
    val cacheDurationSec: Double = 0.0,
    val playbackSpeed:   Double  = 1.0,
    val isFastForwarding: Boolean = false,
    val fileLoaded:      Boolean = false,
    val isLoading:       Boolean = false,
    val error:           String? = null,
    val dragPositionSec: Double? = null,  // non-null only while user is scrubbing
    val hwdecCurrent:    String  = "HW+",
    val tracks:          List<TrackInfo> = emptyList(),
    val currentAudioTrackId: Int = -1,
    val currentSubtitleTrackId: Int = -1,
    val subScale: Double = 1.0,
    val subPos: Int = 100,
    val activeSheet: ActiveSheet = ActiveSheet.NONE,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val orientationMode: OrientationMode = OrientationMode.AUTO,
    val isAutoRotation: Boolean = false,
    val isInPipMode: Boolean = false
)

class PlayerViewModel(private val repository: PlayerRepository) : ViewModel() {

    private val prefsRepository by lazy { UserPreferencesRepository(repository.engine.context) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    // Expose surface directly so PlayerScreen can register SurfaceHolder.Callback
    val surface: MpvSurface get() = repository.engine.surface

    init {
        viewModelScope.launch { prefsRepository.subScaleFlow.collect { repository.setSubScale(it) } }
        viewModelScope.launch { prefsRepository.subPosFlow.collect { repository.setSubPos(it) } }
        viewModelScope.launch { prefsRepository.autoRotationFlow.collect { v -> _uiState.update { it.copy(isAutoRotation = v) } } }
        viewModelScope.launch { repository.isInPipMode.collect { v -> _uiState.update { it.copy(isInPipMode = v) } } }
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
        viewModelScope.launch { repository.isPaused.collect     { v -> _uiState.update { it.copy(isPlaying = !v) } } }
        viewModelScope.launch { repository.positionSec.collect  { v -> _uiState.update { it.copy(positionSec = v) } } }
        viewModelScope.launch { repository.durationSec.collect  { v -> _uiState.update { it.copy(durationSec = v) } } }
        viewModelScope.launch { repository.cachedSec.collect    { v -> _uiState.update { it.copy(cachedSec = v) } } }
        viewModelScope.launch { repository.cacheDurationSec.collect { v -> _uiState.update { it.copy(cacheDurationSec = v) } } }
        viewModelScope.launch { repository.playbackSpeed.collect { v -> _uiState.update { it.copy(playbackSpeed = v) } } }
        viewModelScope.launch { repository.isFastForwarding.collect { v -> _uiState.update { it.copy(isFastForwarding = v) } } }
        viewModelScope.launch { repository.fileLoaded.collect   { v -> _uiState.update { it.copy(fileLoaded = v) } } }
        viewModelScope.launch { repository.isLoading.collect    { v -> _uiState.update { it.copy(isLoading = v) } } }
        viewModelScope.launch { repository.hwdecCurrent.collect { v -> _uiState.update { it.copy(hwdecCurrent = v) } } }
        viewModelScope.launch { repository.tracks.collect       { v -> _uiState.update { it.copy(tracks = v) } } }
        viewModelScope.launch { repository.currentAudioTrackId.collect { v -> _uiState.update { it.copy(currentAudioTrackId = v) } } }
        viewModelScope.launch { repository.currentSubtitleTrackId.collect { v -> _uiState.update { it.copy(currentSubtitleTrackId = v) } } }
        viewModelScope.launch { repository.subScale.collect { v -> _uiState.update { it.copy(subScale = v) } } }
        viewModelScope.launch { repository.subPos.collect   { v -> _uiState.update { it.copy(subPos = v) } } }
        viewModelScope.launch { repository.videoWidth.collect  { v -> _uiState.update { it.copy(videoWidth = v) } } }
        viewModelScope.launch { repository.videoHeight.collect { v -> _uiState.update { it.copy(videoHeight = v) } } }
    }

    fun loadFile(uri: String, title: String = "") { repository.loadFile(uri, title) }

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

    fun togglePlay()           { repository.togglePlay() }

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
        _uiState.update { it.copy(dragPositionSec = posSec) }
    }

    fun onSliderDragChange(posSec: Double) {
        _uiState.update { it.copy(dragPositionSec = posSec) }
        repository.seekGesture(posSec)
    }

    fun onSliderDragEnd(posSec: Double) {
        repository.seekCommit(posSec)
        repository.onSliderDragEnd()
        _uiState.update { it.copy(dragPositionSec = null) }
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
        repository.setSubScale(1.0)
        repository.setSubPos(100)
        viewModelScope.launch {
            prefsRepository.setSubScale(1.0)
            prefsRepository.setSubPos(100)
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.enterStandby()
        repository.cleanup()
    }
}
