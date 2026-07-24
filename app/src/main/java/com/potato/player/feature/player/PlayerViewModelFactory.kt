package com.potato.player.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.potato.player.engine.MpvWrapper

class PlayerViewModelFactory(private val wrapper: MpvWrapper) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return PlayerViewModel(wrapper) as T
    }
}
