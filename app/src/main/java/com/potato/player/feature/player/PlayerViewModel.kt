package com.potato.player.feature.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.engine.InitResult
import com.potato.player.engine.MpvSurface
import com.potato.player.engine.PlayerRepository
import com.potato.player.engine.TrackInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val showAudioDialog: Boolean = false,
    val showSubtitleDialog: Boolean = false,
    val showSpeedDialog: Boolean = false,
    val showMoreMenu:    Boolean = false
)

class PlayerViewModel(private val repository: PlayerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState

    // Expose surface directly so PlayerScreen can register SurfaceHolder.Callback
    val surface: MpvSurface get() = repository.engine.surface

    init {
        viewModelScope.launch {
            repository.initResult.collect { result ->
                when (result) {
                    is InitResult.Success -> _uiState.update { it.copy(error = null) }
                    is InitResult.Failure -> _uiState.update { it.copy(error = result.message, isLoading = false) }
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
    }

    fun lockToLandscape(activity: Activity?) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun loadFile(uri: String) { repository.loadFile(uri) }
    fun togglePlay()           { repository.togglePlay() }

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
        _uiState.update { it.copy(showMoreMenu = !it.showMoreMenu) }
    }

    fun onMoreMenuDismiss() {
        _uiState.update { it.copy(showMoreMenu = false) }
    }

    fun onShowAudioDialog() {
        _uiState.update { it.copy(showAudioDialog = true, showMoreMenu = false) }
    }

    fun onDismissAudioDialog() {
        _uiState.update { it.copy(showAudioDialog = false) }
    }

    fun onShowSubtitleDialog() {
        _uiState.update { it.copy(showSubtitleDialog = true, showMoreMenu = false) }
    }

    fun onDismissSubtitleDialog() {
        _uiState.update { it.copy(showSubtitleDialog = false) }
    }

    fun onShowSpeedDialog() {
        _uiState.update { it.copy(showSpeedDialog = true, showMoreMenu = false) }
    }

    fun onDismissSpeedDialog() {
        _uiState.update { it.copy(showSpeedDialog = false) }
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
        val path = resolveSubtitlePath(uri, context) ?: uri.toString()
        repository.addExternalSubtitle(path)
        onDismissSubtitleDialog()
    }

    private fun resolveSubtitlePath(uri: Uri, context: Context): String? {
        if (uri.scheme == "file") {
            return uri.path
        }
        if (uri.scheme == "content") {
            try {
                var fileName = "external_sub"
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) {
                            cursor.getString(nameIdx)?.let { fileName = it }
                        }
                    }
                }
                val cacheFile = java.io.File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    cacheFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return cacheFile.absolutePath
            } catch (e: Exception) {
                android.util.Log.w("PlayerViewModel", "Failed to resolve subtitle content uri to file", e)
            }
        }
        return uri.toString()
    }

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}
