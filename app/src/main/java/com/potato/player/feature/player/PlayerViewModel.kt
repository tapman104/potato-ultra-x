package com.potato.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.potato.player.engine.InitResult
import com.potato.player.engine.MpvSurface
import com.potato.player.engine.PlayerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isPlaying:       Boolean = false,
    val positionSec:     Double  = 0.0,
    val durationSec:     Double  = 0.0,
    val cachedSec:       Double  = 0.0,
    val fileLoaded:      Boolean = false,
    val isLoading:       Boolean = false,
    val error:           String? = null,
    val dragPositionSec: Double? = null,  // non-null only while user is scrubbing
    val hwdecCurrent:    String  = "HW+"
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
        viewModelScope.launch { repository.fileLoaded.collect   { v -> _uiState.update { it.copy(fileLoaded = v) } } }
        viewModelScope.launch { repository.isLoading.collect    { v -> _uiState.update { it.copy(isLoading = v) } } }
        viewModelScope.launch { repository.hwdecCurrent.collect { v -> _uiState.update { it.copy(hwdecCurrent = v) } } }
    }

    fun loadFile(uri: String) { repository.loadFile(uri) }
    fun togglePlay()           { repository.togglePlay() }

    fun setDecoder(mode: String) {
        repository.setDecoder(mode)
    }

    fun seekRelative(offsetSec: Double) {
        repository.seekRelative(offsetSec)
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

    override fun onCleared() {
        super.onCleared()
        repository.cleanup()
    }
}
