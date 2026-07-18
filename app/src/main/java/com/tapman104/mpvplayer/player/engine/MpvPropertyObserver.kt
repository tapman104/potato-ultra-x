package com.tapman104.mpvplayer.player.engine

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MpvPropertyObserver : MPVLib.EventObserver {
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _paused = MutableStateFlow(true)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    override fun eventProperty(property: String, value: Long) {
        when (property) {
            "time-pos" -> _positionMs.value = value * 1000L
            "duration" -> _durationMs.value = value * 1000L
        }
    }
    override fun eventProperty(property: String, value: Boolean) {
        if (property == "pause") _paused.value = value
    }
    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String, value: Double) {}
    override fun eventProperty(property: String, value: MPVNode) {}
    override fun event(eventId: Int, node: MPVNode) {}

    fun registerObservers() {
        MPVLib.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_INT64)
        MPVLib.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
    }
}
