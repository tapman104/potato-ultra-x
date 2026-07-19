package com.potato.player.engine

import android.util.Log
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import java.util.concurrent.CopyOnWriteArrayList

interface MpvEventListener {
    fun onFileLoaded()
    fun onPlaybackStarted()
    fun onPlaybackStopped(endReason: Int)
    fun onPropertyChange(name: String, value: Any?)
    fun onError(message: String)
}

class MpvEventDispatcher : MPVLib.EventObserver {

    private val listeners = CopyOnWriteArrayList<MpvEventListener>()

    fun addListener(l: MpvEventListener)    { listeners.addIfAbsent(l) }
    fun removeListener(l: MpvEventListener) { listeners.remove(l) }

    private inline fun notify(block: (MpvEventListener) -> Unit) {
        listeners.forEach {
            try { block(it) } catch (e: Exception) {
                Log.e(TAG, "Error dispatching MPV event", e)
            }
        }
    }

    // Property callbacks — one override per type because MPVLib uses Java overloads
    override fun eventProperty(name: String)                  { notify { it.onPropertyChange(name, null)  } }
    override fun eventProperty(name: String, value: Long)     { notify { it.onPropertyChange(name, value) } }
    override fun eventProperty(name: String, value: Boolean)  { notify { it.onPropertyChange(name, value) } }
    override fun eventProperty(name: String, value: String)   { notify { it.onPropertyChange(name, value) } }
    override fun eventProperty(name: String, value: Double)   { notify { it.onPropertyChange(name, value) } }
    override fun eventProperty(name: String, value: MPVNode)  { notify { it.onPropertyChange(name, value) } }

    override fun event(eventId: Int, eventNode: MPVNode) {
        Log.d(TAG, "MPV event: $eventId")
        when (eventId) {
            MpvEvent.FILE_LOADED      -> notify { it.onFileLoaded() }
            MpvEvent.PLAYBACK_RESTART -> notify { it.onPlaybackStarted() }
            MpvEvent.END_FILE -> {
                val reason = runCatching {
                    eventNode.get("reason")?.asInt()?.toInt() ?: 0
                }.getOrDefault(0)
                notify { it.onPlaybackStopped(reason) }
            }
        }
    }

    companion object { private const val TAG = "MpvEventDispatcher" }
}
