package com.potato.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.potato.player.engine.PlayerRepository

class PlayerViewModelFactory(private val repository: PlayerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(repository) as T
    }
}
