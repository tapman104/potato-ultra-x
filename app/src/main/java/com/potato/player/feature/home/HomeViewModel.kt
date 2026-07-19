package com.potato.player.feature.home

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel : ViewModel() {
    private val _videoUri = MutableStateFlow<String?>(null)
    val videoUri: StateFlow<String?> = _videoUri

    fun onVideoPicked(uri: String) { _videoUri.value = uri }

    fun lockToPortrait(activity: Activity?) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
