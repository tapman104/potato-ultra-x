package com.tapman104.mpvplayer.player.engine

import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed class MpvEvent {
    object PlaybackRestart : MpvEvent()
    data class EndFile(val reason: Int) : MpvEvent()
    object Seek : MpvEvent()
    object StartFile : MpvEvent()
    data class Unknown(val eventId: Int) : MpvEvent()
}

class MpvEventDispatcher : MPVLib.EventObserver {
    private val _events = MutableSharedFlow<MpvEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

    override fun eventProperty(property: String) {}
    override fun eventProperty(property: String, value: Long) {}
    override fun eventProperty(property: String, value: Boolean) {}
    override fun eventProperty(property: String, value: String) {}
    override fun eventProperty(property: String, value: Double) {}
    override fun eventProperty(property: String, value: MPVNode) {}

    override fun event(eventId: Int, node: MPVNode) {
        val mpvEvent = when (eventId) {
            MPVLib.MpvEvent.MPV_EVENT_START_FILE -> MpvEvent.StartFile
            MPVLib.MpvEvent.MPV_EVENT_END_FILE -> MpvEvent.EndFile(0)
            MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> MpvEvent.PlaybackRestart
            MPVLib.MpvEvent.MPV_EVENT_SEEK -> MpvEvent.Seek
            else -> MpvEvent.Unknown(eventId)
        }
        _events.tryEmit(mpvEvent)
    }
}
