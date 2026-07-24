package com.potato.player.engine

import android.content.Context
import android.view.Surface
import android.view.SurfaceHolder
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVNode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MpvWrapper(val context: Context) : MPVLib.EventObserver {

    private val _events = MutableSharedFlow<MpvEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<MpvEvent> = _events.asSharedFlow()

    private val configurator = MpvOptionsConfigurator()
    @Volatile private var cachedPause: Boolean = false

    init {
        configurator.copyFontAssets(context)
        MPVLib.create(context)
        MPVLib.addObserver(this)
        
        configurator.initOptions(context)
        
        configurator.postInitOptions()
        configurator.registerPropertyObservers()
        
        MPVLib.init()
    }

    var onSurfaceReady: (() -> Unit)? = null
    var onSurfaceReattached: (() -> Unit)? = null

    val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            attachSurface(holder.surface)
            onSurfaceReady?.invoke()
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            if (width > 0 && height > 0) {
                MPVLib.setPropertyString("android-surface-size", "${width}x${height}")
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            detachSurface()
        }
    }

    fun attachSurface(surface: Surface) {
        MPVLib.attachSurface(surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
    }

    fun detachSurface() {
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        MPVLib.detachSurface()
    }

    fun play(uri: String) {
        MPVLib.command("loadfile", uri, "replace")
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun pause() {
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, true)
    }

    fun togglePlay() {
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, !cachedPause)
    }

    fun resume() {
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setPropertyString("vo", "gpu")
        MPVLib.setPropertyBoolean(MpvProp.PAUSE, false)
    }

    fun seekTo(ms: Long) {
        val safeMs = ms.coerceAtLeast(0L)
        MPVLib.command("seek", (safeMs / 1000.0).toString(), "absolute+exact")
    }

    fun seekRelative(sec: Double) {
        MPVLib.command("seek", sec.toString(), "relative+exact")
    }

    fun setAudioTrack(id: Int) {
        MPVLib.setPropertyString(MpvProp.AID, if (id == -1) "no" else id.toString())
    }

    fun setSubTrack(id: Int) {
        val valStr = if (id == -1) "no" else id.toString()
        MPVLib.setPropertyString(MpvProp.SID, valStr)
    }

    fun setSpeed(speed: Double) {
        MPVLib.setPropertyString(MpvProp.SPEED, speed.toString())
    }

    fun setDecoder(hwdec: String) {
        MPVLib.setPropertyString(MpvProp.HWDEC, hwdec)
    }

    fun setSubScale(scale: Double) {
        MPVLib.setPropertyDouble(MpvProp.SUB_SCALE, scale)
    }

    fun setSubPos(pos: Int) {
        MPVLib.setPropertyInt(MpvProp.SUB_POS, pos)
    }

    fun addExternalSubtitle(path: String) {
        MPVLib.command("sub-add", path, "select")
    }

    fun getPropertyInt(name: String): Int? = MPVLib.getPropertyInt(name)
    fun getPropertyString(name: String): String? = MPVLib.getPropertyString(name)

    private var destroyed = false

    fun destroy() {
        if (destroyed) return
        destroyed = true
        detachSurface()
        MPVLib.removeObserver(this)
        MPVLib.destroy()
    }

    override fun eventProperty(name: String) {}
    override fun eventProperty(name: String, value: Long) { _events.tryEmit(MpvEvent.PropertyLong(name, value)) }
    override fun eventProperty(name: String, value: Boolean) {
        if (name == MpvProp.PAUSE) cachedPause = value
        _events.tryEmit(MpvEvent.PropertyBool(name, value))
    }
    override fun eventProperty(name: String, value: String) { _events.tryEmit(MpvEvent.PropertyString(name, value)) }
    override fun eventProperty(name: String, value: Double) { _events.tryEmit(MpvEvent.PropertyDouble(name, value)) }
    override fun eventProperty(name: String, value: MPVNode) {}

    override fun event(eventId: Int, eventNode: MPVNode) {
        _events.tryEmit(MpvEvent.Id(eventId))
    }
}
